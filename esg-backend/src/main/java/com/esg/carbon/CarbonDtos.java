package com.esg.carbon;

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

/** M2 Carbon Accounting bodies (WP-21). The frontend Zod schemas (features/carbon/schemas.js) mirror these. */
public final class CarbonDtos {
    private CarbonDtos() {}

    public static final String SCOPE_REGEX = "^scope_[123]$";
    public static final String METHOD_REGEX = "^(activity_based|spend_based)$";
    public static final String ACTIVITY_REGEX = "^[a-z][a-z0-9_]*$";
    public static final String CURRENCY_REGEX = "^(NGN|USD|EUR|GBP|KES|ZAR|GHS|XOF|EGP)$";
    public static final String CURRENCY_MESSAGE = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP";

    // ---------------------------------------------------------------- emission records

    /** Activity-based: activityData + activityUnit. Spend-based: spendAmount + spendCurrency. CO2e is always calculated. */
    public record EmissionRequest(
            @NotNull UUID orgId,
            @NotBlank @Pattern(regexp = SCOPE_REGEX, message = "must be scope_1, scope_2 or scope_3") String scope,
            @Min(1) @Max(15) Integer scope3Category,
            @Pattern(regexp = METHOD_REGEX, message = "must be activity_based or spend_based") String calculationMethod,
            @NotBlank @Size(max = 100) @Pattern(regexp = ACTIVITY_REGEX, message = "lower-case letters, digits and underscores") String sourceActivity,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal activityData,
            @Size(max = 50) String activityUnit,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 2) BigDecimal spendAmount,
            @Pattern(regexp = CURRENCY_REGEX, message = CURRENCY_MESSAGE) String spendCurrency,
            UUID emissionFactorId,
            @NotNull LocalDate reportingPeriodStart,
            @NotNull LocalDate reportingPeriodEnd,
            @Size(max = 500) String notes,
            Long version) {

        public String method() {
            return calculationMethod == null || calculationMethod.isBlank() ? "activity_based" : calculationMethod;
        }
    }

    /**
     * @param reviewStatus the factor's review status when it was used (S7): pending_sme means the figure is
     *                     provisional; sme_verified; tenant_provided (custom factors)
     */
    public record FactorUsed(UUID id, String version, BigDecimal value, String unit, String level, String source,
                             String category, String region, String reviewStatus) {}

    public record EmissionResponse(UUID id, UUID orgId, String orgName, String scope, Integer scope3Category, String sourceActivity,
                                   BigDecimal activityData, String activityUnit, String calculationMethod, BigDecimal spendAmount,
                                   String spendCurrency, BigDecimal fxRate, FactorUsed factor, String gwpSet,
                                   BigDecimal co2eTonnes, BigDecimal co2Tonnes, BigDecimal ch4Tonnes, BigDecimal n2oTonnes,
                                   String recordSource, UUID dataPointId, UUID flaringRecordId, LocalDate reportingPeriodStart,
                                   LocalDate reportingPeriodEnd, String notes, boolean verified, Instant calculatedAt,
                                   Instant createdAt, Long version) {}

    public record Recalculation(int checked, int updated, List<String> changes) {}

    /** The calculator's result step: exactly what saving would store, without storing it. */
    public record Preview(BigDecimal activityData, String activityUnit, BigDecimal spendAmount, String spendCurrency, BigDecimal fxRate,
                          FactorUsed factor, String gwpSet, boolean perGas, BigDecimal co2eTonnes, BigDecimal co2Tonnes,
                          BigDecimal ch4Tonnes, BigDecimal n2oTonnes) {}

    // ---------------------------------------------------------------- gas flaring

    public record FlaringRequest(
            @NotNull UUID facilityId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal volumeMcf,
            @NotNull Map<String, BigDecimal> gasComposition,
            @DecimalMin(value = "0.5") @DecimalMax(value = "1") @Digits(integer = 1, fraction = 4) BigDecimal combustionEfficiency,
            @Pattern(regexp = "^(routine|safety|upset|maintenance)$", message = "must be routine, safety, upset or maintenance") String flaringReason,
            @NotNull LocalDate reportingPeriodStart,
            @NotNull LocalDate reportingPeriodEnd,
            @Size(max = 500) String notes,
            Long version) {}

    public record FlaringResponse(UUID id, UUID facilityId, String facilityName, BigDecimal volumeMcf, Map<String, BigDecimal> gasComposition,
                                  BigDecimal combustionEfficiency, String flaringReason, LocalDate reportingPeriodStart,
                                  LocalDate reportingPeriodEnd, BigDecimal co2Tonnes, BigDecimal ch4Tonnes, BigDecimal co2eTonnes,
                                  String gwpSet, boolean satelliteVerified, Map<String, Object> satelliteCheck, String notes,
                                  UUID emissionRecordId, Long version) {}

    // ---------------------------------------------------------------- energy attribute instruments

    public record InstrumentRequest(
            @NotNull UUID orgId,
            @NotBlank @Pattern(regexp = "^(rec|ppa|green_tariff)$", message = "must be rec, ppa or green_tariff") String instrumentType,
            @NotBlank @Pattern(regexp = "^(solar|wind|hydro|biomass|geothermal|other)$", message = "must be solar, wind, hydro, biomass, geothermal or other") String energySource,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal mwh,
            @DecimalMin(value = "0") @DecimalMax(value = "2") @Digits(integer = 1, fraction = 6) BigDecimal emissionFactor,
            @NotNull LocalDate reportingPeriodStart,
            @NotNull LocalDate reportingPeriodEnd,
            @Size(max = 100) String certificateRef,
            @NotBlank @Size(max = 255) String supplier,
            Long version) {}

    public record InstrumentResponse(UUID id, UUID orgId, String orgName, String instrumentType, String energySource, BigDecimal mwh,
                                     BigDecimal emissionFactor, LocalDate reportingPeriodStart, LocalDate reportingPeriodEnd,
                                     String certificateRef, String supplier, BigDecimal coveredMwh, BigDecimal avoidedTonnes,
                                     Long version) {}

    // ---------------------------------------------------------------- reduction targets

    public record TargetRequest(
            @NotBlank @Size(max = 255) String targetName,
            @NotBlank @Pattern(regexp = "^(absolute|intensity)$", message = "must be absolute or intensity") String targetType,
            @NotBlank @Pattern(regexp = "^(scope_1|scope_2|scope_3|scope_1_2|scope_1_2_3)$", message = "must be scope_1, scope_2, scope_3, scope_1_2 or scope_1_2_3") String scope,
            @NotNull @Min(2000) @Max(2100) Integer baseYear,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal baseValue,
            @NotNull @Min(2000) @Max(2100) Integer targetYear,
            @NotNull @DecimalMin(value = "0") @Digits(integer = 14, fraction = 6) BigDecimal targetValue,
            @Pattern(regexp = "^(linear|contraction)$", message = "must be linear or contraction") String pathway,
            Boolean sbtiAligned,
            @Pattern(regexp = "^(revenue|employees|production)$", message = "must be revenue, employees or production") String intensityDenominator,
            Long version) {}

    public record PathwayPoint(int year, BigDecimal expected, BigDecimal actual) {}

    public record TargetResponse(UUID id, String targetName, String targetType, String scope, int baseYear, BigDecimal baseValue,
                                 int targetYear, BigDecimal targetValue, String pathway, boolean sbtiAligned,
                                 String intensityDenominator, Integer progressYear, BigDecimal currentValue, BigDecimal expectedValue,
                                 BigDecimal progressPct, String status, BigDecimal annualReductionPct, List<String> sbtiFindings,
                                 List<PathwayPoint> pathwayPoints, Long version, String baselineWarning) {}

    // ---------------------------------------------------------------- PCAF financed emissions

    public record FinancedRequest(
            @NotNull @Min(2000) @Max(2100) Integer reportingYear,
            @NotBlank @Pattern(regexp = "^(listed_equity_bonds|business_loans_unlisted_equity|project_finance|commercial_real_estate|mortgages|motor_vehicle_loans)$",
                    message = "must be one of the six PCAF asset classes") String assetClass,
            @NotBlank @Size(max = 255) String counterparty,
            @Size(max = 100) String sector,
            @NotNull @DecimalMin(value = "0") @Digits(integer = 18, fraction = 2) BigDecimal outstandingAmount,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 18, fraction = 2) BigDecimal denominatorValue,
            @NotBlank @Pattern(regexp = CURRENCY_REGEX, message = CURRENCY_MESSAGE) String currency,
            @NotNull @DecimalMin(value = "0") @Digits(integer = 14, fraction = 6) BigDecimal counterpartyEmissions,
            @NotNull @Min(1) @Max(5) Integer dataQualityScore,
            @NotBlank @Size(max = 255) String emissionsSource,
            Long version) {}

    public record FinancedResponse(UUID id, int reportingYear, String assetClass, String counterparty, String sector,
                                   BigDecimal outstandingAmount, BigDecimal denominatorValue, String currency,
                                   BigDecimal counterpartyEmissions, int dataQualityScore, String emissionsSource,
                                   BigDecimal attributionFactor, BigDecimal financedEmissions, Long version) {}

    public record AssetClassTotal(String assetClass, int positions, BigDecimal outstanding, BigDecimal financedEmissions,
                                  BigDecimal weightedDataQuality) {}

    public record FinancedSummary(int reportingYear, BigDecimal totalFinancedEmissions, BigDecimal weightedDataQuality,
                                  List<AssetClassTotal> byAssetClass) {}

    // ---------------------------------------------------------------- dashboard

    public record Bucket(String key, String label, BigDecimal co2eTonnes) {}

    public record Intensity(String denominator, BigDecimal denominatorValue, String unit, BigDecimal tonnesPerUnit, String per) {}

    public record Summary(int year, BigDecimal scope1, BigDecimal scope2Location, BigDecimal scope2Market, BigDecimal scope3,
                          BigDecimal totalLocation, BigDecimal totalMarket, BigDecimal flaring, List<Bucket> byMonth,
                          List<Bucket> bySource, List<Bucket> byOrganisation, List<Bucket> byScope3Category,
                          List<Intensity> intensity, int records) {}
}
