package com.esg.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** WP-50 Grounded AI API bodies (D38). The frontend Zod schemas (features/analytics/aiSchemas.js) mirror these. */
public final class AiDtos {
    private AiDtos() {}

    public static final String TARGET_REGEX = "^(emissions_total|scope_1|scope_2|scope_3|metric)$";

    /** Every AI output carries {@code aiGenerated: true} and the engine that produced it (D38 (5)). */
    public record Run(UUID id, String kind, String status, String engine, JsonNode parameters, JsonNode inputSummary, JsonNode output, String error,
                      UUID fileId, String fileName, String reviewStatus, String reviewNote, String reviewedByName, Instant reviewedAt,
                      String createdByName, Instant createdAt, Instant completedAt, boolean aiGenerated, long version) {}

    public record ForecastRequest(
            @NotBlank @Pattern(regexp = TARGET_REGEX, message = "must be emissions_total, scope_1, scope_2, scope_3 or metric") String target,
            @Size(max = 100) String metricKey,
            @NotBlank @Pattern(regexp = "^(month|year)$", message = "must be month or year") String frequency,
            @NotNull @Min(1) @Max(36) Integer horizon,
            @NotNull @Min(50) @Max(95) Integer levelPct) {}

    public record DismissRequest(@NotBlank @Size(max = 1000) String note, @NotNull Long version) {}

    public record ScanRequest(@NotNull @Min(2000) @Max(2100) Integer year, @NotNull @Min(1) @Max(20) Integer contaminationPct) {}

    public record ScanResult(UUID runId, int metricsScanned, int pointsScored, int flagged, int newFlags, List<String> skipped) {}

    public record ExtractionRequest(@NotNull UUID fileId) {}

    public record ExtractedValue(UUID id, UUID runId, int page, String metricKey, String metricName, String metricUnit, BigDecimal value, String rawValue,
                                 String unit, Integer year, String quote, String confidence, String status, UUID dataPointId, String reviewNote,
                                 String reviewedByName, Instant reviewedAt, long version) {}

    public record Extraction(Run run, List<ExtractedValue> values) {}

    public record AcceptRequest(
            @NotNull UUID orgId,
            @NotBlank @Size(max = 100) String metricKey,
            @NotNull @Digits(integer = 14, fraction = 6) BigDecimal value,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @Size(max = 500) String note,
            @NotNull Long version) {}

    public record RejectRequest(@Size(max = 1000) String note, @NotNull Long version) {}

    public record Status(boolean configured, boolean reachable, boolean llmAvailable, String detail) {}
}
