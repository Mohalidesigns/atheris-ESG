package com.esg.ai;

import com.esg.ai.AiDtos.ScanRequest;
import com.esg.ai.AiDtos.ScanResult;
import com.esg.ai.AiEngine.ScoredPoint;
import com.esg.analytics.AnalyticsMath;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolation Forest scan (TRD §8.9 AnomalyDetector; D38 (3)): per metric, every submitted or approved value of the
 * last five years is scored against how all sites behave; points of the chosen year the model isolates become anomaly
 * flags of kind {@code ml}, labelled as AI-generated and handled in the existing explain → accept / dismiss workflow.
 */
@Service
public class MlAnomalyService {

    static final int MIN_POINTS = 8;
    static final int YEARS = 5;
    private static final String OPEN_PERIOD = " and not exists (select 1 from auth.reporting_periods p where p.status = 'locked' "
            + "and p.start_date <= d.reporting_period_end and p.end_date >= d.reporting_period_start)";

    private record Point(UUID id, UUID orgId, String metric, LocalDate start, LocalDate end, BigDecimal value) {}

    private final JdbcTemplate jdbc;
    private final AiEngine engine;
    private final AiRunService runs;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public MlAnomalyService(JdbcTemplate jdbc, AiEngine engine, AiRunService runs, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.json = json;
        this.jdbc = jdbc;
        this.engine = engine;
        this.runs = runs;
    }

    @Transactional
    public ScanResult scan(ScanRequest r) {
        int year = r.year();
        double contamination = r.contaminationPct() / 100.0;
        List<Point> all = jdbc.query("select d.id, d.org_id, d.metric_key, d.reporting_period_start, d.reporting_period_end, d.numeric_value "
                + "from data_hub.data_points d where d.deleted_at is null and d.numeric_value is not null "
                + "and d.validation_status in ('submitted', 'validated', 'approved') and d.reporting_period_end between ? and ? "
                + "order by d.metric_key, d.reporting_period_start", (rs, i) -> new Point(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class), rs.getBigDecimal(6)), Date.valueOf(LocalDate.of(year - YEARS + 1, 1, 1)), Date.valueOf(LocalDate.of(year, 12, 31)));
        Map<String, List<Point>> byMetric = new LinkedHashMap<>();
        all.forEach(p -> byMetric.computeIfAbsent(p.metric(), k -> new ArrayList<>()).add(p));
        List<UUID> open = jdbc.queryForList("select d.id from data_hub.data_points d where d.reporting_period_end between ? and ?" + OPEN_PERIOD,
                UUID.class, Date.valueOf(LocalDate.of(year, 1, 1)), Date.valueOf(LocalDate.of(year, 12, 31)));
        java.util.Set<UUID> inYear = new java.util.HashSet<>(open);

        UUID run = runs.start("isolation_forest", Map.of("year", year, "contaminationPct", r.contaminationPct(), "yearsOfHistory", YEARS),
                Map.of("metrics", byMetric.size(), "points", all.size(), "statuses", List.of("submitted", "validated", "approved")), null);
        int scanned = 0;
        int scored = 0;
        int flagged = 0;
        int created = 0;
        String engineName = null;
        List<String> skipped = new ArrayList<>();
        List<Map<String, Object>> flags = new ArrayList<>();
        for (Map.Entry<String, List<Point>> e : byMetric.entrySet()) {
            List<Point> points = e.getValue();
            if (points.size() < MIN_POINTS || points.stream().noneMatch(p -> inYear.contains(p.id()))) {
                skipped.add(e.getKey());
                continue;
            }
            Map<String, Point> byId = new HashMap<>();
            Map<String, Integer> groupSize = new HashMap<>();
            List<ScoredPoint> request = new ArrayList<>();
            for (Point p : points) {
                String group = p.orgId() + "|" + AnalyticsMath.lengthClass(p.start(), p.end());
                groupSize.merge(group, 1, Integer::sum);
                byId.put(p.id().toString(), p);
                request.add(new ScoredPoint(p.id().toString(), group, p.start().toString(), p.value().doubleValue()));
            }
            JsonNode out = engine.isolationForest(request, contamination);
            engineName = out.path("engine").asText();
            scanned++;
            scored += points.size();
            for (JsonNode s : out.path("scores")) {
                Point p = byId.get(s.path("id").asText());
                if (p == null || !s.path("flagged").asBoolean() || !inYear.contains(p.id())) {
                    continue;
                }
                flagged++;
                BigDecimal score = s.path("score").decimalValue().setScale(3, RoundingMode.HALF_UP);
                BigDecimal z = s.path("zscore").decimalValue().max(BigDecimal.valueOf(-99999999)).min(BigDecimal.valueOf(99999999)).setScale(2, RoundingMode.HALF_UP);
                int n = groupSize.get(p.orgId() + "|" + AnalyticsMath.lengthClass(p.start(), p.end()));
                created += upsert(p, run, score, z, n);
                flags.add(Map.of("dataPointId", p.id(), "metricKey", p.metric(), "score", score));
            }
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("metricsScanned", scanned);
        output.put("pointsScored", scored);
        output.put("flagged", flagged);
        output.put("newFlags", created);
        output.put("skippedMetrics", skipped);
        output.put("flags", flags);
        runs.complete(run, engineName == null ? "scikit-learn IsolationForest (no metric had enough data)" : engineName,
                json.valueToTree(output));
        return new ScanResult(run, scanned, scored, flagged, created, skipped);
    }

    /** 1 when a new flag is raised; an open flag takes the new score; a reviewed one is left alone unless the value changed. */
    private int upsert(Point p, UUID run, BigDecimal score, BigDecimal z, int n) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, status, value from analytics.anomalies where data_point_id = ? and kind = 'ml'", p.id());
        if (rows.isEmpty()) {
            jdbc.update("insert into analytics.anomalies (org_id, data_point_id, metric_key, reporting_period_start, reporting_period_end, kind, method, value, "
                    + "z_score, history_n, ml_score, ai_run_id) values (?, ?, ?, ?, ?, 'ml', 'iforest', ?, ?, ?, ?, ?)", p.orgId(), p.id(), p.metric(),
                    Date.valueOf(p.start()), Date.valueOf(p.end()), p.value(), z, n, score, run);
            return 1;
        }
        Map<String, Object> row = rows.get(0);
        boolean changed = ((BigDecimal) row.get("value")).compareTo(p.value()) != 0;
        if ("open".equals(row.get("status")) || changed || "cleared".equals(row.get("status"))) {
            jdbc.update("update analytics.anomalies set value = ?, z_score = ?, history_n = ?, ml_score = ?, ai_run_id = ?, scanned_at = now()"
                    + (changed || "cleared".equals(row.get("status")) ? ", status = 'open', explanation = null, explained_by = null, explained_at = null, "
                    + "reviewed_by = null, reviewed_at = null, review_note = null, detected_at = now(), version = version + 1" : "") + " where id = ?",
                    p.value(), z, n, score, run, row.get("id"));
        }
        return 0;
    }
}
