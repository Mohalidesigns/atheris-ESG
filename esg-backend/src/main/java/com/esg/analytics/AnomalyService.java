package com.esg.analytics;

import com.esg.analytics.AnalyticsDtos.ActionRequest;
import com.esg.analytics.AnalyticsDtos.Anomaly;
import com.esg.analytics.AnalyticsDtos.AnomalyCounts;
import com.esg.analytics.AnalyticsDtos.ScanResult;
import com.esg.analytics.AnalyticsDtos.Settings;
import com.esg.analytics.AnalyticsDtos.SettingsRequest;
import com.esg.analytics.AnalyticsMath.Outlier;
import com.esg.analytics.AnalyticsMath.Sensitivity;
import com.esg.analytics.AnalyticsMath.Variance;
import com.esg.exception.ApiException;
import com.esg.exception.FieldValidationException;
import com.esg.notifications.NotificationService;
import com.esg.security.CurrentUser;
import com.esg.security.TenantContext;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Anomaly flags and variance explanations (WP-35, D34 (2)–(3); TRD §8.9 AnomalyDetector with configurable sensitivity).
 * A scan compares each submitted, validated or approved data point in an open period with the earlier points of its
 * organisation, metric and period length (outliers: z-score or IQR) and with the same period a year earlier (variance).
 * Flags are explained (analytics:create), then accepted or returned by an approver (data_points:approve) who didn't
 * write the explanation; an approver may dismiss an open flag as a false positive.
 */
@Service
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);

    // no-mock-gate: config — anomaly workflow (V031 anomalies status check)
    static final Map<String, Set<String>> ACTIONS = Map.of(
            "open", Set.of("explain", "dismiss"),
            "explained", Set.of("accept", "return"),
            "accepted", Set.of(),
            "dismissed", Set.of(),
            "cleared", Set.of());
    private static final Set<String> APPROVE = Set.of("accept", "return", "dismiss");
    private static final Set<String> SCANNED = Set.of("submitted", "validated", "approved");

    private static final String SELECT = "select a.*, o.name as org_name, m.name as metric_name, m.unit, "
            + "eu.first_name || ' ' || eu.last_name as explained_by_name, ru.first_name || ' ' || ru.last_name as reviewed_by_name "
            + "from analytics.anomalies a join auth.organisations o on o.id = a.org_id join reference.metrics m on m.metric_key = a.metric_key "
            + "left join auth.users eu on eu.id = a.explained_by left join auth.users ru on ru.id = a.reviewed_by ";

    private final JdbcTemplate jdbc;
    private final AnalyticsSupport support;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;

    public AnomalyService(JdbcTemplate jdbc, AnalyticsSupport support, NotificationService notifications, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.support = support;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------------------------------------------------------------- settings

    public Settings settings() {
        List<Settings> rows = jdbc.query("select z_threshold, iqr_k, variance_pct, min_history, version from analytics.settings",
                (rs, i) -> new Settings(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getInt(4), rs.getLong(5)));
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        Sensitivity d = Sensitivity.DEFAULTS;
        return new Settings(new BigDecimal("3.00"), new BigDecimal("1.5"), new BigDecimal("25.0"), d.minHistory(), 0);
    }

    Sensitivity sensitivity() {
        Settings s = settings();
        return new Sensitivity(s.zscoreThreshold().doubleValue(), s.iqrK().doubleValue(), s.variancePct().doubleValue(), s.minHistory());
    }

    @Transactional
    public Settings updateSettings(SettingsRequest r) {
        long version = r.version() == null ? 0 : r.version();
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from analytics.settings)", Boolean.class));
        int n;
        if (!exists && version == 0) {
            n = jdbc.update("insert into analytics.settings (z_threshold, iqr_k, variance_pct, min_history) values (?, ?, ?, ?)",
                    r.zscoreThreshold(), r.iqrK(), r.variancePct(), r.minHistory());
        } else {
            n = jdbc.update("update analytics.settings set z_threshold = ?, iqr_k = ?, variance_pct = ?, min_history = ?, version = version + 1 "
                    + "where version = ?", r.zscoreThreshold(), r.iqrK(), r.variancePct(), r.minHistory(), version);
        }
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "The settings were changed by someone else. Reload and try again.");
        }
        return settings();
    }

    // ---------------------------------------------------------------- reading

    public List<Anomaly> list(Integer year, String status, String kind, UUID orgId, String metricKey) {
        StringBuilder where = new StringBuilder("where true");
        List<Object> args = new ArrayList<>();
        if (year != null) {
            where.append(" and extract(year from a.reporting_period_end) = ?");
            args.add(year);
        }
        if (status != null) {
            if (!ACTIONS.containsKey(status)) {
                throw FieldValidationException.of("status", "must be open, explained, accepted, dismissed or cleared");
            }
            where.append(" and a.status = ?");
            args.add(status);
        } else {
            where.append(" and a.status <> 'cleared'");
        }
        if (kind != null) {
            if (!Set.of("outlier", "variance", "ml").contains(kind)) {
                throw FieldValidationException.of("kind", "must be outlier, variance or ml");
            }
            where.append(" and a.kind = ?");
            args.add(kind);
        }
        if (orgId != null) {
            where.append(" and a.org_id = ?");
            args.add(orgId);
        }
        if (metricKey != null) {
            where.append(" and a.metric_key = ?");
            args.add(metricKey);
        }
        support.scope(where, args, "a.org_id");
        return jdbc.query(SELECT + where + " order by a.status <> 'open', a.status <> 'explained', a.reporting_period_end desc, o.name, m.name",
                (rs, i) -> anomaly(rs), args.toArray());
    }

    public Anomaly get(UUID id) {
        Anomaly a = jdbc.query(SELECT + "where a.id = ?", (rs, i) -> anomaly(rs), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Anomaly"));
        support.visible(a.orgId(), "Anomaly");
        return a;
    }

    public AnomalyCounts counts(int year) {
        StringBuilder where = new StringBuilder("where extract(year from a.reporting_period_end) = ?");
        List<Object> args = new ArrayList<>(List.of(year));
        support.scope(where, args, "a.org_id");
        Map<String, Object> r = jdbc.queryForMap("select count(*) filter (where status = 'open') as o, count(*) filter (where status = 'explained') as e, "
                + "count(*) filter (where status = 'accepted') as a, count(*) filter (where status = 'dismissed') as d from analytics.anomalies a " + where,
                args.toArray());
        return new AnomalyCounts(((Number) r.get("o")).intValue(), ((Number) r.get("e")).intValue(), ((Number) r.get("a")).intValue(),
                ((Number) r.get("d")).intValue());
    }

    /** Accepted explanations of the period (report variable), oldest period first. */
    public List<Anomaly> accepted(LocalDate start, LocalDate end) {
        return jdbc.query(SELECT + "where a.status = 'accepted' and a.reporting_period_end between ? and ? order by a.reporting_period_end, o.name, m.name",
                (rs, i) -> anomaly(rs), Date.valueOf(start), Date.valueOf(end));
    }

    // ---------------------------------------------------------------- workflow

    @Transactional
    public Anomaly act(UUID id, ActionRequest r) {
        Anomaly a = get(id);
        String action = r.action();
        String perm = APPROVE.contains(action) ? "data_points:approve" : "analytics:create";
        if (!ACTIONS.values().stream().anyMatch(s -> s.contains(action))) {
            throw FieldValidationException.of("action", "must be explain, accept, return or dismiss");
        }
        if (!CurrentUser.hasPermission(perm)) {
            throw ApiException.forbidden("You need the " + perm + " permission to " + action + " a flag.");
        }
        if (!a.actions().contains(action)) {
            String blocked = a.blockedActions().get(action);
            if (blocked != null) {
                throw ApiException.conflict(blocked.startsWith("The reporting period") ? "ESG-409-LOCKED" : "ESG-409-SOD", blocked);
            }
            throw ApiException.conflict("ESG-409-STATE", "You can't " + action + " a flag that is " + a.status() + ".");
        }
        String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();
        UUID me = CurrentUser.access().userId();
        int n = switch (action) {
            case "explain" -> {
                if (note == null) {
                    throw FieldValidationException.of("note", "explain why the value is what it is");
                }
                yield jdbc.update("update analytics.anomalies set status = 'explained', explanation = ?, explained_by = ?, explained_at = now(), "
                        + "version = version + 1 where id = ? and version = ?", note, me, id, r.version());
            }
            case "accept" -> jdbc.update("update analytics.anomalies set status = 'accepted', review_note = ?, reviewed_by = ?, reviewed_at = now(), "
                    + "version = version + 1 where id = ? and version = ?", note, me, id, r.version());
            case "return" -> {
                if (note == null) {
                    throw FieldValidationException.of("note", "say what the explanation is missing");
                }
                yield jdbc.update("update analytics.anomalies set status = 'open', review_note = ?, reviewed_by = ?, reviewed_at = now(), "
                        + "version = version + 1 where id = ? and version = ?", note, me, id, r.version());
            }
            default -> {
                if (note == null) {
                    throw FieldValidationException.of("note", "say why this isn't a real anomaly");
                }
                yield jdbc.update("update analytics.anomalies set status = 'dismissed', review_note = ?, reviewed_by = ?, reviewed_at = now(), "
                        + "version = version + 1 where id = ? and version = ?", note, me, id, r.version());
            }
        };
        if (n == 0) {
            throw ApiException.conflict("ESG-409-VERSION", "This flag was changed by someone else. Reload and try again.");
        }
        if ("explain".equals(action)) {
            for (UUID u : support.holders("data_points", "approve")) {
                if (!u.equals(me)) {
                    notifications.notifyAfterCommit(u, "ANOMALY_EXPLAINED", vars(a));
                }
            }
        }
        return get(id);
    }

    // ---------------------------------------------------------------- scanning

    private record Point(UUID id, UUID orgId, String metric, LocalDate start, LocalDate end, BigDecimal value, String status, UUID createdBy) {}

    private record Finding(Point point, String kind, String method, BigDecimal baseline, BigDecimal low, BigDecimal high, BigDecimal z,
                           BigDecimal change, Integer n) {}

    /** On-demand scan of the year, over the caller's organisations. */
    @Transactional
    public ScanResult scan(int year) {
        // Date bounds, not extract(year …): under RLS only leakproof comparisons on the column can use its index (WP-51).
        StringBuilder where = new StringBuilder("and d.reporting_period_end between ? and ?");
        List<Object> args = new ArrayList<>(List.of(java.sql.Date.valueOf(java.time.LocalDate.of(year, 1, 1)), java.sql.Date.valueOf(java.time.LocalDate.of(year, 12, 31))));
        support.scope(where, args, "d.org_id");
        return run(where.toString(), args, false);
    }

    /** Scan of one data point, after it is approved (a new flag notifies whoever entered it). */
    public void scanPoint(UUID dataPointId) {
        try {
            newTx.executeWithoutResult(s -> run("and d.id = ?", new ArrayList<>(List.of(dataPointId)), true));
        } catch (RuntimeException e) {
            log.warn("Anomaly scan of data point {} failed: {}", dataPointId, e.getMessage());
        }
    }

    /** Nightly: every tenant whose data changed in the last two days, its current and previous year. */
    public int runAll(Clock at) {
        Instant since = Instant.now(at).minus(2, ChronoUnit.DAYS);
        int thisYear = LocalDate.now(at.withZone(AnalyticsSupport.LAGOS)).getYear();
        String previousTenant = TenantContext.getTenantId();
        String previousUser = TenantContext.getUserId();
        List<UUID> tenants = jdbc.queryForList("select tenant_id from analytics.tenants_with_recent_data(?)", UUID.class, Timestamp.from(since));
        int flags = 0;
        try {
            for (UUID tenant : tenants) {
                TenantContext.setTenantId(tenant.toString());
                TenantContext.setUserId(null);
                try {
                    ScanResult r = tx.execute(s -> run("and d.reporting_period_end between ? and ?",
                            new ArrayList<>(List.of(java.sql.Date.valueOf(java.time.LocalDate.of(thisYear - 1, 1, 1)), java.sql.Date.valueOf(java.time.LocalDate.of(thisYear, 12, 31)))), false));
                    flags += r == null ? 0 : r.newFlags();
                } catch (RuntimeException e) {
                    log.warn("Anomaly scan for tenant {} failed: {}", tenant, e.getMessage());
                }
            }
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
        }
        return flags;
    }

    private static final String OPEN_PERIOD = " and not exists (select 1 from auth.reporting_periods p where p.status = 'locked' "
            + "and p.start_date <= d.reporting_period_end and p.end_date >= d.reporting_period_start)";

    private ScanResult run(String candidateFilter, List<Object> args, boolean notify) {
        Sensitivity s = sensitivity();
        List<Point> candidates = jdbc.query("select d.id, d.org_id, d.metric_key, d.reporting_period_start, d.reporting_period_end, d.numeric_value, "
                + "d.validation_status, d.created_by from data_hub.data_points d where d.deleted_at is null and d.numeric_value is not null "
                + "and d.validation_status in ('submitted', 'validated', 'approved') " + candidateFilter + OPEN_PERIOD, (rs, i) -> point(rs), args.toArray());
        int cleared = clearStale(candidateFilter, args);
        if (candidates.isEmpty()) {
            return new ScanResult(0, 0, 0, 0, cleared, 0);
        }
        // Every scanned point of the same organisations and metrics: the candidates' histories.
        List<Point> all = jdbc.query("select d.id, d.org_id, d.metric_key, d.reporting_period_start, d.reporting_period_end, d.numeric_value, "
                        + "d.validation_status, d.created_by from data_hub.data_points d where d.deleted_at is null and d.numeric_value is not null "
                        + "and d.validation_status in ('submitted', 'validated', 'approved') and (d.org_id, d.metric_key) in "
                        + "(select x.o, x.m from unnest(?::uuid[], ?::text[]) as x(o, m))", (rs, i) -> point(rs),
                candidates.stream().map(p -> p.orgId().toString()).toArray(String[]::new), candidates.stream().map(Point::metric).toArray(String[]::new));
        Map<String, List<Point>> series = new HashMap<>();
        for (Point p : all) {
            String cls = AnalyticsMath.lengthClass(p.start(), p.end());
            if (cls != null) {
                series.computeIfAbsent(p.orgId() + "|" + p.metric() + "|" + cls, k -> new ArrayList<>()).add(p);
            }
        }
        int outliers = 0;
        int variances = 0;
        int created = 0;
        int reopened = 0;
        for (Point p : candidates) {
            String cls = AnalyticsMath.lengthClass(p.start(), p.end());
            List<Point> same = cls == null ? List.of() : series.getOrDefault(p.orgId() + "|" + p.metric() + "|" + cls, List.of());
            Finding outlier = outlier(p, same, s);
            Finding variance = variance(p, same, s);
            outliers += outlier == null ? 0 : 1;
            variances += variance == null ? 0 : 1;
            for (Object[] kf : new Object[][] {{"outlier", outlier}, {"variance", variance}}) {
                int r = reconcile(p, (String) kf[0], (Finding) kf[1], notify);
                created += r == 1 ? 1 : 0;
                reopened += r == 2 ? 1 : 0;
                cleared += r == 3 ? 1 : 0;
            }
        }
        return new ScanResult(candidates.size(), outliers, variances, created, cleared, reopened);
    }

    private static Finding outlier(Point p, List<Point> same, Sensitivity s) {
        double[] history = same.stream().filter(h -> h.start().isBefore(p.start()) && !h.id().equals(p.id()))
                .mapToDouble(h -> h.value().doubleValue()).toArray();
        Outlier o = AnalyticsMath.outlier(p.value().doubleValue(), history, s);
        return o == null ? null : new Finding(p, "outlier", o.method(), AnalyticsMath.round(o.mean(), 6), AnalyticsMath.round(o.low(), 6),
                AnalyticsMath.round(o.high(), 6), AnalyticsMath.round(o.z(), 2), null, o.n());
    }

    private static Finding variance(Point p, List<Point> same, Sensitivity s) {
        LocalDate target = p.start().minusYears(1);
        Point previous = same.stream().filter(h -> !h.id().equals(p.id()) && Math.abs(ChronoUnit.DAYS.between(target, h.start())) <= 3)
                .findFirst().orElse(null);
        if (previous == null) {
            return null;
        }
        Variance v = AnalyticsMath.variance(p.value().doubleValue(), previous.value().doubleValue(), s);
        return v == null ? null : new Finding(p, "variance", "yoy", previous.value(), null, null, null, AnalyticsMath.round(v.changePct(), 1), null);
    }

    /** 0 nothing new, 1 new flag, 2 re-opened, 3 cleared. */
    private int reconcile(Point p, String kind, Finding f, boolean notify) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, status, value from analytics.anomalies where data_point_id = ? and kind = ?", p.id(), kind);
        Map<String, Object> row = rows.isEmpty() ? null : rows.get(0);
        if (f == null) {
            // Not unusual any more: an unresolved flag clears; a resolved one clears only if the value it judged has changed.
            if (row != null && (Set.of("open", "explained").contains(row.get("status")) || (Set.of("accepted", "dismissed").contains(row.get("status"))
                    && ((BigDecimal) row.get("value")).compareTo(p.value()) != 0))) {
                jdbc.update("update analytics.anomalies set status = 'cleared', review_note = 'The value is no longer unusual', scanned_at = now(), "
                        + "version = version + 1 where id = ?", row.get("id"));
                return 3;
            }
            return 0;
        }
        if (row == null) {
            jdbc.update("insert into analytics.anomalies (org_id, data_point_id, metric_key, reporting_period_start, reporting_period_end, kind, method, value, "
                    + "baseline, expected_low, expected_high, z_score, change_pct, history_n) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p.orgId(), p.id(), p.metric(), Date.valueOf(p.start()), Date.valueOf(p.end()), kind, f.method(), p.value(), f.baseline(), f.low(),
                    f.high(), f.z(), f.change(), f.n());
            if (notify) {
                notifyDetected(p, f);
            }
            return 1;
        }
        // A refresh of the statistics alone keeps the version, so a nightly scan never trips someone mid-explanation.
        String status = (String) row.get("status");
        boolean valueChanged = ((BigDecimal) row.get("value")).compareTo(p.value()) != 0;
        boolean reopen = "cleared".equals(status) || (valueChanged && !"open".equals(status));
        jdbc.update("update analytics.anomalies set method = ?, value = ?, baseline = ?, expected_low = ?, expected_high = ?, z_score = ?, change_pct = ?, "
                + "history_n = ?, scanned_at = now()" + (reopen ? ", status = 'open', explanation = null, explained_by = null, explained_at = null, "
                + "reviewed_by = null, reviewed_at = null, review_note = ?, detected_at = now(), version = version + 1" : "") + " where id = ?",
                reopen ? new Object[] {f.method(), p.value(), f.baseline(), f.low(), f.high(), f.z(), f.change(), f.n(),
                    "cleared".equals(status) ? null : "The value changed after the flag was " + status, row.get("id")}
                    : new Object[] {f.method(), p.value(), f.baseline(), f.low(), f.high(), f.z(), f.change(), f.n(), row.get("id")});
        return reopen ? 2 : 0;
    }

    /** Flags whose point was rejected, deleted or sent back to draft since: cleared (open periods only). */
    private int clearStale(String candidateFilter, List<Object> args) {
        String filter = candidateFilter.replace("d.", "a.").replace("a.id = ?", "a.data_point_id = ?");
        return jdbc.update("update analytics.anomalies a set status = 'cleared', review_note = 'The data point is no longer submitted or approved', "
                + "scanned_at = now(), version = version + 1 where a.status in ('open', 'explained') " + filter
                + " and not exists (select 1 from data_hub.data_points d where d.id = a.data_point_id and d.deleted_at is null "
                + "and d.validation_status in ('submitted', 'validated', 'approved'))"
                + OPEN_PERIOD.replace("d.reporting", "a.reporting"), args.toArray());
    }

    private void notifyDetected(Point p, Finding f) {
        Map<String, String> v = new HashMap<>();
        v.put("metric", jdbc.queryForObject("select name from reference.metrics where metric_key = ?", String.class, p.metric()));
        v.put("site", jdbc.queryForObject("select name from auth.organisations where id = ?", String.class, p.orgId()));
        v.put("period", p.start() + " to " + p.end());
        v.put("detail", detail(f.kind(), f.method(), f.z(), f.change(), f.baseline(), f.low(), f.high()));
        // Whoever entered the value explains it when they can; otherwise everyone who can explain flags hears of it.
        List<UUID> explainers = support.holders("analytics", "create");
        List<UUID> to = p.createdBy() != null && explainers.contains(p.createdBy()) ? List.of(p.createdBy())
                : explainers.stream().filter(u -> !u.equals(CurrentUser.userIdOrNull())).toList();
        to.forEach(u -> notifications.notifyAfterCommit(u, "ANOMALY_DETECTED", v));
    }

    // ---------------------------------------------------------------- mapping

    private static Point point(ResultSet rs) throws SQLException {
        return new Point(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getObject(4, LocalDate.class),
                rs.getObject(5, LocalDate.class), rs.getBigDecimal(6), rs.getString(7), rs.getObject(8, UUID.class));
    }

    private Map<String, String> vars(Anomaly a) {
        return Map.of("metric", a.metricName(), "site", a.orgName(), "period", a.periodStart() + " to " + a.periodEnd());
    }

    static String detail(String kind, String method, BigDecimal z, BigDecimal change, BigDecimal baseline, BigDecimal low, BigDecimal high) {
        if ("variance".equals(kind)) {
            if (change == null) {
                return "changed from zero a year earlier";
            }
            return (change.signum() > 0 ? "up " : "down ") + fmt(change.abs()) + "% on the same period a year earlier"
                    + (baseline == null ? "" : " (" + fmt(baseline) + ")");
        }
        String range = low == null || high == null ? "outside the usual range" : "outside the usual range " + fmt(low) + " to " + fmt(high);
        if ("z_score".equals(method) && z != null) {
            return fmt(z.abs()) + " standard deviations from the average" + (baseline == null ? "" : " of " + fmt(baseline));
        }
        return "both".equals(method) && z != null ? range + " (" + fmt(z.abs()) + " standard deviations from the average)" : range;
    }

    /** WP-50 (D38 (3)): an Isolation Forest finding is labelled as AI-generated with its score. */
    static String mlDetail(BigDecimal score, BigDecimal z) {
        return "AI-generated: isolated by the Isolation Forest model (score " + (score == null ? "?" : score.stripTrailingZeros().toPlainString())
                + " of 1" + (z == null ? "" : "; " + fmt(z.abs()) + " robust deviations from this site's usual level") + ")";
    }

    private static String fmt(BigDecimal v) {
        return new DecimalFormat("#,##0.##").format(v);
    }

    private Anomaly anomaly(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        UUID explainedBy = rs.getObject("explained_by", UUID.class);
        LocalDate start = rs.getObject("reporting_period_start", LocalDate.class);
        LocalDate end = rs.getObject("reporting_period_end", LocalDate.class);
        UUID me = CurrentUser.userIdOrNull();
        boolean locked = !ACTIONS.get(status).isEmpty() && support.locked(start, end);
        List<String> actions = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        for (String act : ACTIONS.get(status)) {
            if (!CurrentUser.hasPermission(APPROVE.contains(act) ? "data_points:approve" : "analytics:create")) {
                continue;
            }
            if (locked) {
                blocked.put(act, "The reporting period is locked.");
            } else if ("accept".equals(act) && me != null && me.equals(explainedBy)) {
                blocked.put(act, "You wrote the explanation, so someone else must accept it.");
            } else {
                actions.add(act);
            }
        }
        String kind = rs.getString("kind");
        BigDecimal z = rs.getBigDecimal("z_score");
        BigDecimal change = rs.getBigDecimal("change_pct");
        BigDecimal baseline = rs.getBigDecimal("baseline");
        BigDecimal low = rs.getBigDecimal("expected_low");
        BigDecimal high = rs.getBigDecimal("expected_high");
        String method = rs.getString("method");
        return new Anomaly(rs.getObject("id", UUID.class), rs.getObject("org_id", UUID.class), rs.getString("org_name"), rs.getObject("data_point_id", UUID.class),
                rs.getString("metric_key"), rs.getString("metric_name"), rs.getString("unit"), start, end, kind, method, rs.getBigDecimal("value"),
                baseline, low, high, z, change, (Integer) rs.getObject("history_n"), status, rs.getString("explanation"),
                rs.getString("explained_by_name"), instant(rs.getTimestamp("explained_at")), rs.getString("review_note"), rs.getString("reviewed_by_name"),
                instant(rs.getTimestamp("reviewed_at")), instant(rs.getTimestamp("detected_at")),
                "ml".equals(kind) ? mlDetail(rs.getBigDecimal("ml_score"), z) : detail(kind, method, z, change, baseline, low, high),
                rs.getBigDecimal("ml_score"), rs.getObject("ai_run_id", UUID.class), "ml".equals(kind), actions, blocked, rs.getLong("version"));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
