package com.esg.risk;

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

/** M5 Risk & Materiality API bodies (WP-32, D32). The frontend Zod schemas (features/risk/schemas.js) mirror these. */
public final class RiskDtos {
    private RiskDtos() {}

    public static final String GROUP_REGEX = "^(employees|communities|investors|lenders|regulators|customers|suppliers|civil_society|other)$";
    public static final String CATEGORY_REGEX = "^(E|S|G)$";
    public static final String RISK_CATEGORY_REGEX = "^(environmental|social|governance|climate_physical|climate_transition|regulatory|"
            + "reputational|operational)$";
    public static final String HORIZON_REGEX = "^(short|medium|long)$";
    public static final String RISK_STATUS_REGEX = "^(open|mitigating|accepted|closed)$";
    public static final String HAZARD_REGEX = "^(flooding|drought_desertification|heat_stress|water_scarcity|sea_level_rise|storms|wildfire)$";
    public static final String PATHWAY_REGEX = "^(RCP4\\.5|RCP8\\.5)$";

    public record ActionRequest(@NotBlank @Size(max = 30) String action, @Size(max = 1000) String note, @NotNull Long version) {}

    // ---------------------------------------------------------------- materiality

    public record StakeholderWeight(
            @NotBlank @Pattern(regexp = GROUP_REGEX, message = "must be a stakeholder group") String group,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal weight) {}

    public record AssessmentRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull @Min(2000) @Max(2100) Integer reportingYear,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.2") @DecimalMax("5") @Digits(integer = 1, fraction = 2) BigDecimal impactThreshold,
            @NotNull @DecimalMin("0.2") @DecimalMax("5") @Digits(integer = 1, fraction = 2) BigDecimal financialThreshold,
            @NotNull @DecimalMin("0") @DecimalMax("0.5") @Digits(integer = 1, fraction = 2) BigDecimal stakeholderBlend,
            @NotNull @Size(max = 9) List<@Valid StakeholderWeight> stakeholders,
            Long version) {}

    public record TopicRequest(
            @NotBlank @Size(max = 255) String topic,
            @NotBlank @Pattern(regexp = CATEGORY_REGEX, message = "must be E, S or G") String category,
            @Size(max = 2000) String description,
            @Size(max = 20) String griTopic,
            @NotBlank @Pattern(regexp = "^(actual|potential)$", message = "must be actual or potential") String impactType,
            @NotNull @Min(1) @Max(5) Integer severityScale,
            @NotNull @Min(1) @Max(5) Integer severityScope,
            @NotNull @Min(1) @Max(5) Integer severityIrremediability,
            @Min(1) @Max(5) Integer impactLikelihood,
            @NotNull @Min(1) @Max(5) Integer financialMagnitude,
            @NotNull @Min(1) @Max(5) Integer financialLikelihood,
            Map<@Pattern(regexp = GROUP_REGEX, message = "must be a stakeholder group") String, @NotNull @Min(1) @Max(5) Integer> ratings,
            Long version) {}

    public record Topic(UUID id, String topic, String category, String description, String griTopic, String impactType, int severityScale,
                        int severityScope, int severityIrremediability, Integer impactLikelihood, int financialMagnitude, int financialLikelihood,
                        Map<String, Integer> ratings, RiskMath.Position position, Long version) {}

    public record Assessment(UUID id, String name, int reportingYear, String description, BigDecimal impactThreshold,
                             BigDecimal financialThreshold, BigDecimal stakeholderBlend, List<StakeholderWeight> stakeholders, String status,
                             String statusReason, String submittedByName, Instant submittedAt, String approvedByName, Instant approvedAt,
                             int topics, int materialTopics, List<Topic> topicList, List<String> actions, Map<String, String> blockedActions,
                             Long version) {}

    // ---------------------------------------------------------------- risk register

    public record RiskRequest(
            @NotNull UUID orgId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = RISK_CATEGORY_REGEX, message = "must be a risk category") String category,
            @Size(max = 10) String sectorCode,
            @Size(max = 4000) String description,
            @NotBlank @Pattern(regexp = HORIZON_REGEX, message = "must be short, medium or long") String timeHorizon,
            @NotNull @Min(1) @Max(5) Integer likelihood,
            @NotNull @Min(1) @Max(5) Integer impact,
            @Size(max = 4000) String controls,
            @Min(1) @Max(5) Integer residualLikelihood,
            @Min(1) @Max(5) Integer residualImpact,
            UUID ownerUserId,
            @NotBlank @Pattern(regexp = RISK_STATUS_REGEX, message = "must be open, mitigating, accepted or closed") String status,
            LocalDate reviewDate,
            UUID topicId,
            Long version) {}

    public record Risk(UUID id, UUID orgId, String orgName, String title, String category, String sectorCode, String sectorName,
                       String description, String timeHorizon, int likelihood, int impact, int score, String band, String controls,
                       Integer residualLikelihood, Integer residualImpact, Integer residualScore, String residualBand, UUID ownerUserId,
                       String ownerName, String status, LocalDate reviewDate, boolean reviewDue, UUID topicId, String topicName, Long version) {}

    public record HeatmapGroup(String key, String label, UUID orgId, boolean hasChildren, int risks, int[][] grid, int maxScore, String maxBand) {}

    public record Heatmap(String level, boolean residual, int risks, int[][] grid, Map<String, Integer> byBand, List<HeatmapGroup> groups) {}

    // ---------------------------------------------------------------- climate scenarios

    public record Scenario(String code, String name, String category, String warming, String narrative, String source,
                           List<RiskMath.PricePoint> prices) {}

    public record CarbonPriceRequest(
            @NotBlank @Size(max = 30) String scenarioCode,
            @NotNull @Min(2020) @Max(2100) Integer priceYear,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal priceUsd,
            @NotBlank @Size(max = 500) String source) {}

    public record ScenarioExposure(String code, String name, String category, String warming, List<RiskMath.ExposureRow> rows) {}

    public record TransitionAnalysis(Integer baselineYear, BigDecimal baselineTonnes, String targetName, String baseCurrency, BigDecimal usdRate,
                                     LocalDate usdRateDate, List<ScenarioExposure> scenarios, String note) {}

    public record PhysicalRequest(
            @NotNull UUID orgId,
            @NotBlank @Pattern(regexp = HAZARD_REGEX, message = "must be a hazard") String hazard,
            @NotBlank @Pattern(regexp = PATHWAY_REGEX, message = "must be RCP4.5 or RCP8.5") String pathway,
            @NotNull Integer horizonYear,
            @NotNull @Min(1) @Max(5) Integer exposure,
            @NotNull @Min(1) @Max(5) Integer vulnerability,
            @NotBlank @Size(max = 500) String source,
            @Size(max = 2000) String notes,
            @NotNull LocalDate assessedOn,
            Long version) {}

    public record Physical(UUID id, UUID orgId, String orgName, String hazard, String pathway, int horizonYear, int exposure, int vulnerability,
                           int score, String band, String source, String notes, LocalDate assessedOn, Long version) {}

    // ---------------------------------------------------------------- overview

    public record Overview(Integer materialityYear, String assessmentName, String assessmentStatus, int materialTopics, List<String> topMaterial,
                           int openRisks, Map<String, Integer> risksByBand, List<Risk> topRisks, int physicalHighOrAbove, int scenariosWithPrices) {}

    public record Person(UUID id, String name) {}
}
