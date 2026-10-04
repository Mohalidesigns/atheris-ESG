package com.esg.integrations;

import com.esg.datahub.DataPointEvents;
import com.esg.reporting.ReportEvents;
import com.esg.social.SafetyEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * WP-46 (D44): turns domain events into webhook deliveries. Runs synchronously inside the transaction that published
 * the event, so the delivery rows commit (or roll back) with the change itself — the transactional outbox.
 */
@Component
public class WebhookEventListener {

    private final WebhookService webhooks;
    private final JdbcTemplate jdbc;

    public WebhookEventListener(WebhookService webhooks, JdbcTemplate jdbc) {
        this.webhooks = webhooks;
        this.jdbc = jdbc;
    }

    @EventListener
    public void on(DataPointEvents.Approved e) {
        dataPoint("data_point.approved", e.dataPointId());
    }

    @EventListener
    public void on(DataPointEvents.Reopened e) {
        dataPoint("data_point.reopened", e.dataPointId());
    }

    @EventListener
    public void on(ReportEvents.Approved e) {
        first("select id, title, framework_code, report_version, reporting_period_start, reporting_period_end, approved_at "
                + "from reporting.reports where id = ?", e.reportId())
                .ifPresent(data -> webhooks.enqueue("report.approved", data));
    }

    @EventListener
    public void on(SafetyEvents.IncidentRecorded e) {
        first("select id, org_id, title, incident_type, outcome, occurred_at from social.safety_incidents where id = ?", e.incidentId())
                .ifPresent(data -> webhooks.enqueue("safety_incident.recorded", data));
    }

    private void dataPoint(String type, UUID id) {
        first("select id, org_id, metric_key, numeric_value, text_value, unit, reporting_period_start, reporting_period_end, "
                + "validation_status from data_hub.data_points where id = ?", id).ifPresent(data -> webhooks.enqueue(type, data));
    }

    /** The row as camelCase keys with JSON-friendly values (ids, dates and numbers as text). */
    private java.util.Optional<Map<String, Object>> first(String sql, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) {
            return java.util.Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        rows.get(0).forEach((k, v) -> data.put(camel(k), v == null ? null : v instanceof Number || v instanceof Boolean ? v : v.toString()));
        return java.util.Optional.of(data);
    }

    static String camel(String snake) {
        StringBuilder out = new StringBuilder();
        boolean up = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                out.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return out.toString();
    }
}
