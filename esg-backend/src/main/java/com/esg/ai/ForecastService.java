package com.esg.ai;

import com.esg.ai.AiDtos.ForecastRequest;
import com.esg.ai.AiDtos.Run;
import com.esg.ai.AiEngine.SeriesPoint;
import com.esg.analytics.AnalyticsInsights;
import com.esg.carbon.EmissionService;
import com.esg.carbon.EmissionService.TrendPoint;
import com.esg.exception.FieldValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emission and metric forecasts (TRD §8.9 PredictiveEngine; TRD GET /analytics/predictions; D38 (2)). The history is
 * the caller's own data in their organisation scope; the AI service fits ARIMA or a linear trend; the run keeps the
 * series summary, the parameters and the model, so the forecast can be traced and reproduced.
 */
@Service
public class ForecastService {

    static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    static final int MONTHS_OF_HISTORY = 60;
    static final int YEARS_OF_HISTORY = 10;

    private final JdbcTemplate jdbc;
    private final AiEngine engine;
    private final AiRunService runs;
    private final EmissionService emissions;
    private final AnalyticsInsights analytics;
    private final Clock clock;

    public ForecastService(JdbcTemplate jdbc, AiEngine engine, AiRunService runs, EmissionService emissions, AnalyticsInsights analytics, Clock clock) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.runs = runs;
        this.emissions = emissions;
        this.analytics = analytics;
        this.clock = clock;
    }

    @Transactional
    public Run create(ForecastRequest r) {
        LocalDate today = LocalDate.now(clock.withZone(LAGOS));
        boolean metric = "metric".equals(r.target());
        String metricName = null;
        if (metric) {
            if (r.metricKey() == null || r.metricKey().isBlank()) {
                throw FieldValidationException.of("metricKey", "choose the metric to forecast");
            }
            List<String> names = jdbc.queryForList("select name from reference.metrics where metric_key = ? and data_type in ('decimal', 'integer', "
                    + "'currency', 'percentage')", String.class, r.metricKey());
            if (names.isEmpty()) {
                throw FieldValidationException.of("metricKey", "must be a numeric catalogue metric");
            }
            metricName = names.get(0);
            if (!"year".equals(r.frequency())) {
                throw FieldValidationException.of("frequency", "metrics are forecast by year");
            }
        }
        if ("year".equals(r.frequency()) && r.horizon() > 10) {
            throw FieldValidationException.of("horizon", "at most 10 years ahead");
        }
        List<SeriesPoint> series = metric ? metricSeries(r.metricKey(), today) : emissionSeries(r.target(), r.frequency(), today);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("target", r.target());
        if (metric) {
            summary.put("metricKey", r.metricKey());
            summary.put("metricName", metricName);
        }
        summary.put("source", metric ? "approved Data Hub values (annual)" : "emission records, split by month as on the Carbon overview");
        summary.put("scope", "your organisations");
        summary.put("from", series.isEmpty() ? null : series.get(0).period());
        summary.put("to", series.isEmpty() ? null : series.get(series.size() - 1).period());
        summary.put("points", series.size());
        summary.put("history", series);
        Map<String, Object> parameters = Map.of("frequency", r.frequency(), "horizon", r.horizon(), "levelPct", r.levelPct());
        JsonNode out;
        try {
            out = engine.forecast(series, r.frequency(), r.horizon(), r.levelPct() / 100.0);
        } catch (AiEngine.AiRefused e) {
            throw FieldValidationException.of("target", e.getMessage());
        }
        UUID id = runs.start("forecast", parameters, summary, null);
        runs.complete(id, out.path("model").asText() + " · " + out.path("engine").asText(), out);
        return runs.get(id);
    }

    private List<SeriesPoint> emissionSeries(String target, String frequency, LocalDate today) {
        LocalDate to = "month".equals(frequency) ? today.withDayOfMonth(1).minusDays(1) : LocalDate.of(today.getYear() - 1, 12, 31);
        LocalDate from = "month".equals(frequency) ? to.withDayOfMonth(1).minusMonths(MONTHS_OF_HISTORY - 1) : LocalDate.of(today.getYear() - YEARS_OF_HISTORY, 1, 1);
        List<SeriesPoint> out = new ArrayList<>();
        for (TrendPoint p : emissions.trend(from, to, frequency)) {
            BigDecimal v = switch (target) {
                case "scope_1" -> p.scope1();
                case "scope_2" -> p.scope2Location();
                case "scope_3" -> p.scope3();
                default -> p.total();
            };
            out.add(new SeriesPoint(p.period(), v.doubleValue()));
        }
        return out;
    }

    private List<SeriesPoint> metricSeries(String key, LocalDate today) {
        Map<Integer, BigDecimal> values = analytics.metricValues(key, today.getYear() - YEARS_OF_HISTORY, today.getYear() - 1);
        List<SeriesPoint> out = new ArrayList<>();
        values.keySet().stream().sorted().forEach(y -> out.add(new SeriesPoint(String.valueOf(y), values.get(y).doubleValue())));
        return out;
    }
}
