package com.esg.investor;

import com.esg.analytics.AnalyticsDtos.Kpi;
import com.esg.analytics.KpiService;
import com.esg.analytics.MaturityService;
import com.esg.compliance.ComplianceDtos.FrameworkStatus;
import com.esg.compliance.ComplianceService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.investor.InvestorDtos.ActionRequest;
import com.esg.investor.InvestorDtos.GenerateRequest;
import com.esg.investor.InvestorDtos.Pillar;
import com.esg.investor.InvestorDtos.Preview;
import com.esg.investor.InvestorDtos.Scorecard;
import com.esg.investor.InvestorDtos.Settings;
import com.esg.investor.InvestorDtos.SettingsRequest;
import com.esg.investor.ScorecardMath.Component;
import com.esg.security.CurrentUser;
import com.esg.social.SocialMetricsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ESG scorecards for investors (WP-40, D35 (2); TRD §8.10 ScorecardGenerator). Generated as a frozen draft from the
 * platform's records, published by someone other than whoever generated it, withdrawable with a reason. Investors
 * (investor:read without investor:share) see published scorecards only.
 */
@Service
public class ScorecardService {

    // no-mock-gate: config — scorecard workflow (V032 scorecards status check)
    static final Map<String, Set<String>> ACTIONS = Map.of("draft", Set.of("publish", "discard"), "published", Set.of("withdraw"), "withdrawn", Set.of());
    private static final Map<String, String> CARBON = Map.of("scope_1", "scope1_tco2e", "scope_2", "scope2_location_tco2e", "scope_3", "scope3_tco2e");
    private static final String SELECT = "select s.*, g.first_name || ' ' || g.last_name as generated_by_name, p.first_name || ' ' || p.last_name "
            + "as published_by_name from investor.scorecards s left join auth.users g on g.id = s.generated_by left join auth.users p on p.id = s.published_by ";

    private final JdbcTemplate jdbc;
    private final InvestorSupport support;
    private final MaturityService maturity;
    private final ComplianceService compliance;
    private final SocialMetricsService social;
    private final KpiService kpis;
    private final ObjectMapper json;

    public ScorecardService(JdbcTemplate jdbc, InvestorSupport support, MaturityService maturity, ComplianceService compliance,
                            SocialMetricsService social, KpiService kpis, ObjectMapper json) {
        this.jdbc = jdbc;
        this.support = support;
        this.maturity = maturity;
        this.compliance = compliance;
        this.social = social;
        this.kpis = kpis;
        this.json = json;
    }

    // ---------------------------------------------------------------- weights

    public Settings settings() {
        return jdbc.query("select weight_e, weight_s, weight_g, version from investor.settings", (rs, i) -> new Settings(rs.getInt(1), rs.getInt(2),
                rs.getInt(3), rs.getLong(4))).stream().findFirst().orElse(new Settings(40, 30, 30, 0));
    }

    @Transactional
    public Settings updateSettings(SettingsRequest r) {
        if (r.weightE() + r.weightS() + r.weightG() != 100) {
            throw FieldValidationException.of("weightE", "the E, S and G weights must add up to 100");
        }
        long version = r.version() == null ? 0 : r.version();
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from investor.settings)", Boolean.class));
        int n = !exists && version == 0
                ? jdbc.update("insert into investor.settings (weight_e, weight_s, weight_g) values (?, ?, ?)", r.weightE(), r.weightS(), r.weightG())
                : jdbc.update("update investor.settings set weight_e = ?, weight_s = ?, weight_g = ?, version = version + 1 where version = ?", r.weightE(),
                        r.weightS(), r.weightG(), version);
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "The weights were changed by someone else. Reload and try again.");
        }
        return settings();
    }

    // ---------------------------------------------------------------- scoring

    /** What a scorecard for the year would show now (not saved). */
    public Preview preview(int year) {
        Settings w = settings();
        List<Pillar> pillars = pillars(year, w);
        BigDecimal total = ScorecardMath.total(pillars.get(0).score(), pillars.get(1).score(), pillars.get(2).score(), w.weightE(), w.weightS(), w.weightG());
        return new Preview(year, total, ScorecardMath.grade(total), pillars);
    }

    List<Pillar> pillars(int year, Settings w) {
        Date start = Date.valueOf(LocalDate.of(year, 1, 1));
        Date end = Date.valueOf(LocalDate.of(year, 12, 31));
        Map<String, List<String>> byPillar = new HashMap<>();
        jdbc.query("select metric_key, pillar from reference.metrics where is_active and data_type <> 'text'", rs -> {
            byPillar.computeIfAbsent(rs.getString(2), k -> new ArrayList<>()).add(rs.getString(1));
        });
        Set<String> evidenced = new HashSet<>(jdbc.queryForList("select distinct metric_key from data_hub.data_points where deleted_at is null "
                + "and validation_status = 'approved' and reporting_period_end between ? and ?", String.class, start, end));
        jdbc.queryForList("select distinct scope from carbon.emission_records where deleted_at is null and reporting_period_end between ? and ?",
                String.class, start, end).forEach(s -> {
                    if (CARBON.containsKey(s)) {
                        evidenced.add(CARBON.get(s));
                    }
                });
        evidenced.addAll(social.available(year));
        Map<String, Integer> dims = new HashMap<>();
        maturity.compute(year).dimensions().forEach(d -> dims.put(d.key(), d.score()));
        List<FrameworkStatus> active = compliance.status(year).stream().filter(f -> f.active() && f.totalRequirements() > 0).toList();
        BigDecimal completeness = active.isEmpty() ? null : active.stream().map(FrameworkStatus::completeness).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(active.size()), 1, java.math.RoundingMode.HALF_UP);
        List<Kpi> targeted = kpis.list("active").stream().filter(k -> k.targetValue() != null && k.onTrack() != null).toList();
        Map<String, long[]> flags = new HashMap<>();
        jdbc.query("select m.pillar, count(*) filter (where a.status in ('accepted', 'dismissed')), count(*) from analytics.anomalies a "
                + "join reference.metrics m on m.metric_key = a.metric_key where a.status <> 'cleared' and a.reporting_period_end between ? and ? "
                + "group by m.pillar", rs -> {
                    flags.put(rs.getString(1), new long[] {rs.getLong(2), rs.getLong(3)});
                }, start, end);

        List<Pillar> out = new ArrayList<>();
        String[][] spec = {{"E", "carbon", "Carbon accounting maturity"}, {"S", "value_chain", "Value chain & social maturity"},
            {"G", "governance", "Governance maturity"}};
        int[] weights = {w.weightE(), w.weightS(), w.weightG()};
        for (int i = 0; i < 3; i++) {
            String p = spec[i][0];
            List<String> metrics = byPillar.getOrDefault(p, List.of());
            long have = metrics.stream().filter(evidenced::contains).count();
            List<Component> c = new ArrayList<>();
            c.add(new Component("coverage", "Data coverage", ScorecardMath.pct(have, metrics.size()),
                    have + " of " + metrics.size() + " " + p + " catalogue metrics have approved data for " + year));
            int dim = dims.getOrDefault(spec[i][1], 1);
            c.add(new Component("maturity", spec[i][2], ScorecardMath.fromMaturity(dim), "maturity dimension " + dim + " of 5"));
            if ("G".equals(p)) {
                c.add(new Component("compliance", "Framework completeness", completeness, completeness == null ? "no active framework with content"
                        : "average gap-analysis completeness of " + active.size() + " active framework" + (active.size() == 1 ? "" : "s")));
            }
            List<Kpi> pk = targeted.stream().filter(k -> p.equals(k.pillar())).toList();
            long onTrack = pk.stream().filter(k -> Boolean.TRUE.equals(k.onTrack())).count();
            c.add(new Component("kpis", "KPIs on track", ScorecardMath.pct(onTrack, pk.size()),
                    pk.isEmpty() ? "no " + p + " KPI with a target and a known trend" : onTrack + " of " + pk.size() + " KPIs with a target on track"));
            long[] f = flags.getOrDefault(p, new long[] {0, 0});
            c.add(new Component("flags", "Anomaly flags resolved", ScorecardMath.pct(f[0], f[1]),
                    f[1] == 0 ? "no anomaly flags for " + year : f[0] + " of " + f[1] + " flags explained and accepted, or dismissed"));
            out.add(new Pillar(p, weights[i], ScorecardMath.pillar(c), c));
        }
        return out;
    }

    // ---------------------------------------------------------------- records

    public List<Scorecard> list() {
        String where = support.manager() ? "where s.deleted_at is null" : "where s.deleted_at is null and s.status = 'published'";
        return jdbc.query(SELECT + where + " order by s.reporting_year desc, s.created_at desc", (rs, i) -> scorecard(rs));
    }

    public Scorecard get(UUID id) {
        Scorecard s = jdbc.query(SELECT + "where s.id = ? and s.deleted_at is null", (rs, i) -> scorecard(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Scorecard"));
        if (!support.manager() && !"published".equals(s.status())) {
            throw ApiException.notFound("Scorecard");
        }
        return s;
    }

    /** The newest published scorecard for the year (report variables). */
    public Optional<Scorecard> latestPublished(int year) {
        return jdbc.query(SELECT + "where s.deleted_at is null and s.status = 'published' and s.reporting_year = ? order by s.published_at desc limit 1",
                (rs, i) -> scorecard(rs), year).stream().findFirst();
    }

    @Transactional
    public Scorecard generate(GenerateRequest r) {
        int year = support.year(r.year());
        Preview p = preview(year);
        Settings w = settings();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into investor.scorecards (id, reporting_year, title, commentary, weight_e, weight_s, weight_g, score_e, score_s, score_g, total, "
                    + "grade, components, generated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)", id, year, r.title().trim(),
                    blank(r.commentary()), w.weightE(), w.weightS(), w.weightG(), p.pillars().get(0).score(), p.pillars().get(1).score(),
                    p.pillars().get(2).score(), p.total(), p.grade(), json.writeValueAsString(p.pillars()), CurrentUser.access().userId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return get(id);
    }

    @Transactional
    public Scorecard act(UUID id, ActionRequest r) {
        Scorecard s = get(id);
        if (!s.actions().contains(r.action())) {
            String blocked = s.blockedActions().get(r.action());
            if (blocked != null) {
                throw ApiException.conflict("ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + r.action() + " a scorecard that is " + s.status() + ".");
        }
        UUID me = CurrentUser.access().userId();
        String note = blank(r.note());
        int n = switch (r.action()) {
            case "publish" -> jdbc.update("update investor.scorecards set status = 'published', published_by = ?, published_at = now(), version = version + 1 "
                    + "where id = ? and version = ?", me, id, r.version());
            case "withdraw" -> {
                if (note == null) {
                    throw FieldValidationException.of("note", "say why it is withdrawn (investors see the reason)");
                }
                yield jdbc.update("update investor.scorecards set status = 'withdrawn', withdrawn_by = ?, withdrawn_at = now(), withdraw_reason = ?, "
                        + "version = version + 1 where id = ? and version = ?", me, note, id, r.version());
            }
            default -> jdbc.update("update investor.scorecards set deleted_at = now(), version = version + 1 where id = ? and version = ? and status = 'draft'",
                    id, r.version());
        };
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This scorecard was changed by someone else. Reload and try again.");
        }
        return "discard".equals(r.action()) ? s : get(id);
    }

    private Scorecard scorecard(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        UUID generatedBy = rs.getObject("generated_by", UUID.class);
        UUID me = CurrentUser.userIdOrNull();
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        if (support.manager()) {
            for (String a : ACTIONS.get(status)) {
                if ("publish".equals(a) && me != null && me.equals(generatedBy)) {
                    blocked.put(a, "You generated this scorecard, so someone else must publish it.");
                } else {
                    actions.add(a);
                }
            }
        }
        List<Pillar> pillars;
        try {
            pillars = json.readValue(rs.getString("components"), new TypeReference<List<Pillar>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        Timestamp created = rs.getTimestamp("created_at");
        return new Scorecard(rs.getObject("id", UUID.class), rs.getInt("reporting_year"), rs.getString("title"), rs.getString("commentary"),
                rs.getBigDecimal("total"), rs.getString("grade"), pillars, status, rs.getString("generated_by_name"), created.toInstant(),
                rs.getString("published_by_name"), instant(rs.getTimestamp("published_at")), rs.getString("withdraw_reason"),
                instant(rs.getTimestamp("withdrawn_at")), actions, blocked, rs.getLong("version"));
    }

    static java.time.Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
