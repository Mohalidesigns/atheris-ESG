package com.esg.risk;

import com.esg.carbon.EmissionService;
import com.esg.datahub.FxRateService;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.risk.RiskDtos.CarbonPriceRequest;
import com.esg.risk.RiskDtos.Physical;
import com.esg.risk.RiskDtos.PhysicalRequest;
import com.esg.risk.RiskDtos.Scenario;
import com.esg.risk.RiskDtos.ScenarioExposure;
import com.esg.risk.RiskDtos.TransitionAnalysis;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Climate scenario analysis (WP-32; TRD §8.5 ClimateScenarioAnalyser, AfricaClimateModels; IFRS S2 paragraph 22). NGFS
 * scenario families are reference data; the carbon price path for each is the tenant's input with its source.
 * Transition exposure = carbon price × Scope 1+2 emissions (RiskMath.exposure); physical risk is assessed per site,
 * hazard, pathway (RCP 4.5 / 8.5) and horizon with the data source cited. A regional climate data adapter
 * (ClimateHazardProvider) can suggest figures; the sandbox one says no source is connected.
 */
@Service
public class ClimateService {

    private final JdbcTemplate jdbc;
    private final RiskSupport support;
    private final EmissionService emissions;
    private final FxRateService fx;
    private final ClimateHazardProvider hazards;

    public ClimateService(JdbcTemplate jdbc, RiskSupport support, EmissionService emissions, FxRateService fx, ClimateHazardProvider hazards) {
        this.jdbc = jdbc;
        this.support = support;
        this.emissions = emissions;
        this.fx = fx;
        this.hazards = hazards;
    }

    public List<Scenario> scenarios() {
        Map<String, List<RiskMath.PricePoint>> prices = new LinkedHashMap<>();
        jdbc.query("select scenario_code, price_year, price_usd, source from risk.scenario_carbon_prices order by price_year", rs -> {
            prices.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new RiskMath.PricePoint(rs.getInt(2), rs.getBigDecimal(3), rs.getString(4)));
        });
        return jdbc.query("select * from reference.climate_scenarios order by sort_order", (rs, i) -> new Scenario(rs.getString("code"),
                rs.getString("name"), rs.getString("category"), rs.getString("warming"), rs.getString("narrative"), rs.getString("source"),
                prices.getOrDefault(rs.getString("code"), List.of())));
    }

    /** Sets (or replaces) the price for one scenario and year. */
    @Transactional
    public List<Scenario> setPrice(CarbonPriceRequest r) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from reference.climate_scenarios where code = ?)", Boolean.class,
                r.scenarioCode()))) {
            throw FieldValidationException.of("scenarioCode", "must be an NGFS scenario");
        }
        jdbc.update("insert into risk.scenario_carbon_prices (scenario_code, price_year, price_usd, source) values (?, ?, ?, ?) "
                        + "on conflict (tenant_id, scenario_code, price_year) do update set price_usd = excluded.price_usd, source = excluded.source, "
                        + "version = risk.scenario_carbon_prices.version + 1", r.scenarioCode(), r.priceYear(), r.priceUsd(), r.source().trim());
        return scenarios();
    }

    @Transactional
    public List<Scenario> deletePrice(String scenario, int year) {
        if (jdbc.update("delete from risk.scenario_carbon_prices where scenario_code = ? and price_year = ?", scenario, year) == 0) {
            throw ApiException.notFound("Carbon price");
        }
        return scenarios();
    }

    /**
     * Transition exposure for every scenario with prices: Scope 1 + Scope 2 (location-based) of the baseline year (the
     * latest full year with emission records, or the one asked for), in the user's organisation scope.
     */
    public TransitionAnalysis transition(Integer baselineYear) {
        int year = baselineYear != null ? baselineYear : latestYear();
        if (year == 0) {
            return new TransitionAnalysis(null, null, null, base(), null, null, List.of(), "No Scope 1 or 2 emissions recorded yet.");
        }
        Map<String, BigDecimal> scopes = emissions.scopeTotals(year, true);
        BigDecimal baseline = scopes.getOrDefault("scope_1", BigDecimal.ZERO).add(scopes.getOrDefault("scope_2", BigDecimal.ZERO));
        List<Map<String, Object>> t = jdbc.queryForList("select target_name, pathway, base_value, target_value, base_year, target_year "
                + "from carbon.reduction_targets where deleted_at is null and target_type = 'absolute' and scope in ('scope_1_2', 'scope_1_2_3') "
                + "and target_year > ? order by target_year limit 1", year);
        RiskMath.Target target = t.isEmpty() ? null : new RiskMath.Target((String) t.get(0).get("pathway"), (BigDecimal) t.get(0).get("base_value"),
                (BigDecimal) t.get(0).get("target_value"), ((Number) t.get(0).get("base_year")).intValue(),
                ((Number) t.get(0).get("target_year")).intValue());
        String base = base();
        FxRateService.Rate usd = "USD".equals(base) ? new FxRateService.Rate(BigDecimal.ONE, support.today(), "identity")
                : fx.resolve("USD", base, support.today()).orElse(null);
        List<ScenarioExposure> out = new ArrayList<>();
        for (Scenario s : scenarios()) {
            if (!s.prices().isEmpty()) {
                out.add(new ScenarioExposure(s.code(), s.name(), s.category(), s.warming(), RiskMath.exposure(baseline, year, target, s.prices())));
            }
        }
        String note = out.isEmpty() ? "Enter a carbon price path for at least one scenario to see the exposure." : null;
        return new TransitionAnalysis(year, baseline.setScale(3, java.math.RoundingMode.HALF_UP), t.isEmpty() ? null : (String) t.get(0).get("target_name"),
                base, usd == null ? null : usd.rate(), usd == null ? null : usd.rateDate(), out, note);
    }

    private int latestYear() {
        Integer y = jdbc.queryForObject("select max(extract(year from reporting_period_end))::int from carbon.emission_records "
                + "where deleted_at is null and scope in ('scope_1', 'scope_2') and reporting_period_end < ?", Integer.class,
                Date.valueOf(LocalDate.of(support.today().getYear(), 1, 1)));
        if (y != null) {
            return y;
        }
        Integer any = jdbc.queryForObject("select max(extract(year from reporting_period_end))::int from carbon.emission_records "
                + "where deleted_at is null and scope in ('scope_1', 'scope_2')", Integer.class);
        return any == null ? 0 : any;
    }

    String base() {
        return jdbc.queryForObject("select base_currency from auth.tenant_settings", String.class);
    }

    // ---------------------------------------------------------------- physical risk

    private static final String SELECT = "select p.*, o.name as org_name from risk.physical_assessments p join auth.organisations o on o.id = p.org_id ";

    public List<Physical> physical(UUID orgId) {
        StringBuilder where = new StringBuilder("where p.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (orgId != null) {
            where.append(" and p.org_id = ?");
            args.add(orgId);
        }
        support.scope(where, args, "p.org_id");
        return jdbc.query(SELECT + where + " order by p.exposure * p.vulnerability desc, o.name, p.hazard, p.assessed_on desc", (rs, i) -> physical(rs), args.toArray());
    }

    public Physical getPhysical(UUID id) {
        Physical p = jdbc.query(SELECT + "where p.id = ? and p.deleted_at is null", (rs, i) -> physical(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Physical risk assessment"));
        support.visible(p.orgId(), "Physical risk assessment");
        return p;
    }

    @Transactional
    public Physical createPhysical(PhysicalRequest r) {
        check(r);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into risk.physical_assessments (id, org_id, hazard, pathway, horizon_year, exposure, vulnerability, source, notes, "
                        + "assessed_on) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, r.orgId(), r.hazard(), r.pathway(), r.horizonYear(), r.exposure(),
                r.vulnerability(), r.source().trim(), MaterialityService.blank(r.notes()), Date.valueOf(r.assessedOn()));
        return getPhysical(id);
    }

    @Transactional
    public Physical updatePhysical(UUID id, PhysicalRequest r) {
        getPhysical(id);
        if (r.version() == null) {
            throw FieldValidationException.of("version", "is required");
        }
        check(r);
        int n = jdbc.update("update risk.physical_assessments set org_id = ?, hazard = ?, pathway = ?, horizon_year = ?, exposure = ?, "
                        + "vulnerability = ?, source = ?, notes = ?, assessed_on = ?, version = version + 1 where id = ? and version = ? "
                        + "and deleted_at is null", r.orgId(), r.hazard(), r.pathway(), r.horizonYear(), r.exposure(), r.vulnerability(),
                r.source().trim(), MaterialityService.blank(r.notes()), Date.valueOf(r.assessedOn()), id, r.version());
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This assessment was changed by someone else. Reload and try again.");
        }
        return getPhysical(id);
    }

    @Transactional
    public void deletePhysical(UUID id, long version) {
        getPhysical(id);
        if (jdbc.update("update risk.physical_assessments set deleted_at = now(), version = version + 1 where id = ? and version = ? "
                + "and deleted_at is null", id, version) == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This assessment was changed by someone else. Reload and try again.");
        }
    }

    /** Asks the climate data adapter for a suggestion (the sandbox says plainly that nothing is connected). */
    public Map<String, Object> suggest(UUID orgId, String hazard, String pathway, int horizonYear) {
        support.requireOrg(orgId);
        if (!hazard.matches(RiskDtos.HAZARD_REGEX)) {
            throw FieldValidationException.of("hazard", "must be a hazard");
        }
        String name = jdbc.queryForObject("select name from auth.organisations where id = ?", String.class, orgId);
        return hazards.lookup(name, null, null, hazard, pathway, horizonYear);
    }

    public Map<String, Object> dataSource() {
        return Map.of("provider", hazards.provider(), "sandbox", hazards.sandbox());
    }

    private void check(PhysicalRequest r) {
        support.requireOrg(r.orgId());
        Map<String, String> errors = new LinkedHashMap<>();
        if (r.horizonYear() != 2030 && r.horizonYear() != 2050) {
            errors.put("horizonYear", "must be 2030 or 2050");
        }
        if (r.assessedOn().isAfter(support.today())) {
            errors.put("assessedOn", "can't be in the future");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static Physical physical(ResultSet rs) throws SQLException {
        int score = rs.getInt("exposure") * rs.getInt("vulnerability");
        return new Physical(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class), rs.getString("org_name"), rs.getString("hazard"),
                rs.getString("pathway"), rs.getInt("horizon_year"), rs.getInt("exposure"), rs.getInt("vulnerability"), score, RiskMath.band(score),
                rs.getString("source"), rs.getString("notes"), rs.getObject("assessed_on", LocalDate.class), rs.getLong("version"));
    }
}
