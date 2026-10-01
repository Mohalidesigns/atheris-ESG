package com.esg.reference;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Reference data, framework catalogue and emission factor bodies (WP-17). */
public final class ReferenceDtos {
    private ReferenceDtos() {}

    public record Currency(String code, String name, String symbol, int minorUnit, String source) {}

    public record Country(String code, String name, String region, String currency, String source) {}

    public record Sdg(int goal, String title, String source) {}

    public record Scope3Category(int category, String name, String direction, String source) {}

    public record Sector(String code, String name, String classification, String source) {}

    public record RequirementRef(String framework, String code, String title) {}

    public record Metric(String metricKey, String pillar, String category, String name, String description, String unit,
                         String dataType, String source, List<RequirementRef> requirements) {}

    public record Framework(UUID id, String code, String name, String jurisdiction, String frameworkType,
                            LocalDate effectiveDate, String version, String contentStatus, String source,
                            String description, int requirementCount) {}

    public record Requirement(UUID id, String code, String title, String category, String dataType, String parentCode,
                              String source, String verificationStatus, List<String> metricKeys,
                              List<RequirementRef> mappedTo) {}

    public record Mapping(RequirementRef source, RequirementRef target, String relation, String notes, String provenance) {}

    public record EmissionFactor(UUID id, String region, String category, String subCategory, BigDecimal factorValue,
                                 String unit, String source, Integer sourceYear, String gwpSet, String ghgBasis,
                                 String derivation, String factorVersion,
                                 @JsonFormat(pattern = "yyyy-MM-dd") LocalDate validFrom,
                                 @JsonFormat(pattern = "yyyy-MM-dd") LocalDate validTo,
                                 String verificationStatus, boolean custom, long version) {}

    /** Which level of the lookup hierarchy answered (TRD §8.2): tenant custom → country → region → global. */
    public record ResolvedFactor(EmissionFactor factor, String level, List<String> searched, BigDecimal activityData,
                                 BigDecimal co2eTonnes) {}

    static final String REGION_REGEX = "^([A-Z]{2}|WEST_AFRICA|EAST_AFRICA|SOUTHERN_AFRICA|NORTH_AFRICA|CENTRAL_AFRICA|GLOBAL)$";
    static final String CATEGORY_REGEX = "^[a-z][a-z0-9_]*$";

    /** A tenant's own factor. The value must name its source (e.g. supplier certificate, utility bill, study). */
    public record CustomFactorRequest(
            @NotBlank @Pattern(regexp = REGION_REGEX, message = "must be an ISO country code or a region such as WEST_AFRICA") String region,
            @NotBlank @Size(max = 100) @Pattern(regexp = CATEGORY_REGEX, message = "lower-case letters, digits and underscores") String category,
            @Size(max = 200) String subCategory,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 10, fraction = 10) BigDecimal factorValue,
            @NotBlank @Size(max = 50) String unit,
            @NotBlank @Size(max = 255) String source,
            @NotNull LocalDate validFrom,
            LocalDate validTo,
            Long version) {}
}
