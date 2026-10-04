package com.esg.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
import java.util.Map;
import java.util.UUID;

/** M9 Analytics API bodies (WP-35, D34). The frontend Zod schemas (features/analytics/schemas.js) mirror these. */
public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public static final String PILLAR_REGEX = "^(E|S|G)$";
    public static final String OPERATOR_REGEX = "^(none|divide|multiply|add|subtract)$";
    public static final String DIRECTION_REGEX = "^(lower_better|higher_better)$";
    public static final String CODE_REGEX = "^[a-z][a-z0-9_]*$";
    public static final String STATUS_REGEX = "^(active|archived)$";

    public record ActionRequest(@NotBlank @Size(max = 30) String action, @Size(max = 2000) String note, @NotNull Long version) {}

    // ---------------------------------------------------------------- settings

    public record SettingsRequest(
            @NotNull @DecimalMin("2") @DecimalMax("5") @Digits(integer = 1, fraction = 2) BigDecimal zscoreThreshold,
            @NotNull @DecimalMin("1") @DecimalMax("3") @Digits(integer = 1, fraction = 1) BigDecimal iqrK,
            @NotNull @DecimalMin("5") @DecimalMax("500") @Digits(integer = 3, fraction = 1) BigDecimal variancePct,
            @NotNull @Min(3) @Max(20) Integer minHistory,
            Long version) {}

    public record Settings(BigDecimal zscoreThreshold, BigDecimal iqrK, BigDecimal variancePct, int minHistory, long version) {}

    // ---------------------------------------------------------------- anomalies

    public record Anomaly(UUID id, UUID orgId, String orgName, UUID dataPointId, String metricKey, String metricName, String unit,
                          LocalDate periodStart, LocalDate periodEnd, String kind, String method, BigDecimal value, BigDecimal baseline,
                          BigDecimal expectedLow, BigDecimal expectedHigh, BigDecimal zscore, BigDecimal changePct, Integer historyN,
                          String status, String explanation, String explainedByName, Instant explainedAt, String reviewNote, String reviewedByName,
                          Instant reviewedAt, Instant detectedAt, String detail, BigDecimal mlScore, UUID aiRunId, boolean aiGenerated,
                          List<String> actions, Map<String, String> blockedActions, long version) {}

    public record ScanRequest(@Min(2000) @Max(2100) Integer year) {}

    public record ScanResult(int pointsChecked, int outliers, int variances, int newFlags, int cleared, int reopened) {}

    public record AnomalyCounts(int open, int explained, int accepted, int dismissed) {}

    // ---------------------------------------------------------------- custom KPIs

    public record KpiRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = CODE_REGEX, message = "lower-case letters, digits and underscores, starting with a letter") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = PILLAR_REGEX, message = "must be E, S or G") String pillar,
            UUID orgId,
            @NotBlank @Size(max = 100) String metricA,
            @NotBlank @Pattern(regexp = OPERATOR_REGEX, message = "must be none, divide, multiply, add or subtract") String operator,
            @Size(max = 100) String metricB,
            @NotNull @Digits(integer = 14, fraction = 6) BigDecimal multiplier,
            @NotBlank @Size(max = 30) String unit,
            @NotNull @Min(0) @Max(4) Integer decimals,
            @NotBlank @Pattern(regexp = DIRECTION_REGEX, message = "must be lower_better or higher_better") String direction,
            @Min(2000) @Max(2100) Integer baselineYear,
            @Digits(integer = 14, fraction = 6) BigDecimal targetValue,
            @Min(2000) @Max(2100) Integer targetYear,
            @NotNull @Size(max = 17) List<@NotNull @Min(1) @Max(17) Integer> sdgGoals,
            @NotBlank @Pattern(regexp = STATUS_REGEX, message = "must be active or archived") String status,
            Long version) {}

    public record KpiPoint(int year, BigDecimal value, String note) {}

    public record Kpi(UUID id, String code, String name, String description, String pillar, UUID orgId, String orgName, String metricA,
                      String metricAName, String operator, String metricB, String metricBName, BigDecimal multiplier, String unit, int decimals,
                      String direction, Integer baselineYear, BigDecimal targetValue, Integer targetYear, List<Integer> sdgGoals, String status,
                      List<KpiPoint> series, KpiPoint latest, BigDecimal baselineValue, Boolean onTrack, String trackNote, long version) {}

    // ---------------------------------------------------------------- benchmarks

    public record BenchmarkValueRequest(
            @NotBlank @Size(max = 100) String metricKey,
            @NotNull @Min(2000) @Max(2100) Integer dataYear,
            @NotNull @Digits(integer = 14, fraction = 6) BigDecimal p25,
            @NotNull @Digits(integer = 14, fraction = 6) BigDecimal median,
            @NotNull @Digits(integer = 14, fraction = 6) BigDecimal p75,
            @Min(1) Integer sampleSize) {}

    public record BenchmarkDatasetRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 200) String publisher,
            @NotNull @Min(2000) @Max(2100) Integer publicationYear,
            @Size(max = 500) @Pattern(regexp = "^(https?://.+)?$", message = "must start with http:// or https://") String sourceUrl,
            @Size(max = 10) String sectorCode,
            @Pattern(regexp = "^([A-Z]{2})?$", message = "must be a 2-letter country code") String countryCode,
            @NotNull UUID sourceFileId,
            @Size(max = 1000) String notes,
            @NotBlank @Pattern(regexp = STATUS_REGEX, message = "must be active or archived") String status,
            @NotNull @Size(min = 1, max = 200, message = "add at least one benchmark value") List<@Valid @NotNull BenchmarkValueRequest> values,
            Long version) {}

    public record BenchmarkValue(String metricKey, String metricName, String unit, int dataYear, BigDecimal p25, BigDecimal median, BigDecimal p75,
                                 Integer sampleSize) {}

    public record BenchmarkDataset(UUID id, String name, String publisher, int publicationYear, String sourceUrl, String sectorCode, String sectorName,
                                   String countryCode, UUID sourceFileId, String sourceFileName, String notes, String status, List<BenchmarkValue> values,
                                   long version) {}

    public record Placement(UUID orgId, String orgName, BigDecimal value, Integer band) {}

    public record Comparison(String metricKey, String metricName, String unit, String direction, boolean comparable, UUID datasetId, String datasetName,
                             String publisher, int publicationYear, int dataYear, BigDecimal p25, BigDecimal median, BigDecimal p75, Integer sampleSize,
                             Placement total, List<Placement> organisations) {}

    public record InternalRow(UUID orgId, String orgName, BigDecimal value, int rank) {}

    public record InternalBenchmark(String metricKey, String metricName, String unit, String direction, boolean comparable, int year, List<InternalRow> rows) {}

    // ---------------------------------------------------------------- maturity

    public record Maturity(int year, BigDecimal overall, int level, String levelName, List<MaturityModel.Dimension> dimensions, boolean periodLocked) {}

    public record AssessRequest(@NotNull @Min(2000) @Max(2100) Integer year, @Size(max = 1000) String note) {}

    public record MaturitySnapshot(UUID id, int year, BigDecimal overall, int level, String levelName, List<MaturityModel.Dimension> dimensions, String note,
                                   String assessedByName, Instant assessedAt) {}

    // ---------------------------------------------------------------- SDG alignment

    public record SdgMetric(String metricKey, String name, String targets, boolean evidenced, String direction, BigDecimal previous, BigDecimal current,
                            Boolean improving) {}

    public record SdgKpi(UUID id, String name, Boolean onTrack, KpiPoint latest) {}

    public record SdgGoal(int goal, String title, boolean priority, String rationale, UUID priorityId, Long priorityVersion, int linked, int evidenced,
                          int comparable, int improving, int score, List<SdgMetric> metrics, List<SdgKpi> kpis) {}

    public record SdgAlignment(int year, List<SdgGoal> goals) {}

    public record SdgPriorityRequest(@NotNull @Min(1) @Max(17) Integer goal, @NotBlank @Size(max = 1000) String rationale, Long version) {}

    // ---------------------------------------------------------------- dashboard

    public record Overview(int year, BigDecimal maturity, Integer maturityLevel, String maturityLevelName, int anomaliesOpen, int anomaliesExplained,
                           int kpis, int kpisWithTarget, int kpisOnTrack) {}
}
