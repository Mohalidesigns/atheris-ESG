package com.esg.datahub;

import com.esg.model.dto.DataPointDto;
import com.esg.reference.ReferenceDtos.Metric;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Data point rules that depend on the metric catalogue (WP-16/17). The frontend mirrors these exactly in
 * features/dataHub/schemas.js; keep the two in step.
 */
public final class DataPointRules {
    private DataPointRules() {}

    static final Set<String> NUMERIC_TYPES = Set.of("decimal", "integer", "percentage", "currency");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Returns field errors (empty when valid). {@code metric} is null when the key isn't in the catalogue. */
    public static Map<String, String> check(DataPointDto dto, Metric metric) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (dto.getReportingPeriodStart() != null && dto.getReportingPeriodEnd() != null
                && dto.getReportingPeriodEnd().isBefore(dto.getReportingPeriodStart())) {
            errors.put("reportingPeriodEnd", "must be on or after the start date");
        }
        if (metric == null) {
            errors.put("metricKey", "is not in the metric catalogue");
            return errors;
        }
        if (dto.getCategory() != null && !dto.getCategory().equals(metric.pillar())) {
            errors.put("category", "must be " + metric.pillar() + " for " + metric.metricKey());
        }
        boolean money = "currency".equals(metric.dataType());
        // Currency metrics are stored in the tenant base currency, so their unit is set by the server.
        if (!money && dto.getUnit() != null && !dto.getUnit().isBlank() && !dto.getUnit().equals(metric.unit())) {
            errors.put("unit", "must be " + metric.unit() + " for " + metric.metricKey());
        }
        if (!money && dto.getCurrency() != null && !dto.getCurrency().isBlank()) {
            errors.put("currency", "only applies to currency metrics");
        }
        if (NUMERIC_TYPES.contains(metric.dataType())) {
            BigDecimal v = dto.getNumericValue();
            if (v == null) {
                errors.put("numericValue", "is required");
            } else if (v.signum() < 0) {
                errors.put("numericValue", "must be zero or more");
            } else if ("integer".equals(metric.dataType()) && v.stripTrailingZeros().scale() > 0) {
                errors.put("numericValue", "must be a whole number");
            } else if ("percentage".equals(metric.dataType()) && v.compareTo(HUNDRED) > 0) {
                errors.put("numericValue", "must be between 0 and 100");
            }
        } else if ("text".equals(metric.dataType()) && (dto.getTextValue() == null || dto.getTextValue().isBlank())) {
            errors.put("textValue", "is required");
        }
        return errors;
    }
}
