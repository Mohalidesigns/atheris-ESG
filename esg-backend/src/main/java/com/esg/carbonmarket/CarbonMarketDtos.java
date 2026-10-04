package com.esg.carbonmarket;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M11 Carbon Market API bodies (WP-41, D36). The frontend Zod schemas (features/carbonMarket/schemas.js) mirror these. */
public final class CarbonMarketDtos {
    private CarbonMarketDtos() {}

    public static final String STANDARD_REGEX = "^(verra_vcs|gold_standard|plan_vivo|cdm|article_6_4|acmi|other)$";
    public static final String TYPE_REGEX = "^(reforestation_arr|mangrove|redd_plus|soil_carbon|blue_carbon|agroforestry|clean_cookstoves|solar|wind|biogas|"
            + "methane_capture|water_purification|other)$";
    public static final String CURRENCY_REGEX = "^(NGN|USD|EUR|GBP|KES|ZAR|GHS|XOF|EGP)$";
    public static final String PURPOSE_REGEX = "^(scope_1|scope_2|scope_3|voluntary)$";

    public record ActionRequest(@NotBlank @Size(max = 30) String action, @Size(max = 1000) String note, @Size(max = 120) String registryReference,
                                LocalDate retiredOn, UUID fileId, @NotNull Long version) {}

    // ---------------------------------------------------------------- projects

    public record ProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = STANDARD_REGEX, message = "must be a carbon standard") String standard,
            @NotBlank @Size(max = 100) String registry,
            @NotBlank @Size(max = 60) String registryProjectId,
            @NotBlank @Pattern(regexp = TYPE_REGEX, message = "must be a project type") String projectType,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be a 2-letter country code") String countryCode,
            @Size(max = 100) String methodology,
            @Size(max = 500) @Pattern(regexp = "^(https?://.+)?$", message = "must start with http:// or https://") String registryUrl,
            @Size(max = 2000) String description,
            @NotBlank @Pattern(regexp = "^(active|archived)$", message = "must be active or archived") String status,
            Long version) {}

    public record Project(UUID id, String name, String standard, String registry, String registryProjectId, String projectType, boolean natureBased,
                          String countryCode, String methodology, String registryUrl, String description, String status, long held, long retired,
                          long version) {}

    // ---------------------------------------------------------------- credit lots

    public record LotRequest(
            @NotNull UUID projectId,
            @NotNull @Min(1990) @Max(2100) Integer vintage,
            @NotBlank @Size(max = 120) String serialPrefix,
            @NotNull @Min(0) Long serialFrom,
            @NotNull @Min(0) Long serialTo,
            @NotNull LocalDate acquiredOn,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal pricePerTonne,
            @Pattern(regexp = "^(NGN|USD|EUR|GBP|KES|ZAR|GHS|XOF|EGP)?$", message = "must be a supported currency") String currency,
            @Size(max = 200) String seller,
            @Size(max = 100) String transactionRef,
            @Size(max = 1000) String notes,
            Long version) {}

    public record Lot(UUID id, UUID projectId, String projectName, String standard, int vintage, String serialPrefix, long serialFrom, long serialTo,
                      long quantity, long retired, long reserved, long available, LocalDate acquiredOn, BigDecimal pricePerTonne, String currency,
                      String seller, String transactionRef, String verificationStatus, String verifiedByName, Instant verifiedAt, UUID verificationFileId,
                      String notes, List<String> actions, long version) {}

    // ---------------------------------------------------------------- retirements

    public record RetireRequest(
            @NotNull UUID lotId,
            @NotNull @Positive Long quantity,
            @NotBlank @Pattern(regexp = PURPOSE_REGEX, message = "must be scope_1, scope_2, scope_3 or voluntary") String purpose,
            @NotNull @Min(2000) @Max(2100) Integer claimedYear,
            @NotBlank @Size(max = 200) String beneficiary,
            @Size(max = 1000) String note) {}

    public record Retirement(UUID id, UUID lotId, String projectName, String standard, int vintage, String serialKey, long serialFrom, long serialTo,
                             long quantity, String purpose, int claimedYear, String beneficiary, String note, String status, String requestedByName,
                             Instant requestedAt, String approvedByName, LocalDate retiredOn, String registryReference, UUID certificateFileId,
                             String cancelReason, List<String> actions, Map<String, String> blockedActions, long version) {}

    // ---------------------------------------------------------------- pricing and offsetting

    public record SettingsRequest(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 1) BigDecimal offsetCeilingPct,
                                  Long version) {}

    public record Settings(BigDecimal offsetCeilingPct, long version) {}

    public record PriceRequest(
            @NotBlank @Pattern(regexp = "^(shadow|fee)$", message = "must be shadow or fee") String priceType,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal price,
            @NotBlank @Pattern(regexp = CURRENCY_REGEX, message = "must be a supported currency") String currency,
            @NotNull LocalDate effectiveFrom,
            @NotNull @Size(min = 1, max = 3, message = "choose at least one scope") List<@NotBlank @Pattern(regexp = "^scope_[123]$") String> scopes,
            @Size(max = 1000) String note,
            Long version) {}

    public record Price(UUID id, String priceType, BigDecimal price, String currency, LocalDate effectiveFrom, List<String> scopes, String note,
                        long version) {}

    public record ExposureRow(UUID orgId, String orgName, BigDecimal tonnes, BigDecimal cost) {}

    public record Exposure(int year, String priceType, Price price, BigDecimal tonnes, BigDecimal cost, List<ExposureRow> organisations, String note) {}

    public record Offsetting(int year, BigDecimal grossScope1, BigDecimal grossScope2, BigDecimal grossScope3, BigDecimal creditsScope1,
                             BigDecimal creditsScope2, BigDecimal creditsScope3, BigDecimal creditsVoluntary, BigDecimal netScope12, BigDecimal offsetShare,
                             BigDecimal baseline, String baselineBasis, BigDecimal reductions, BigDecimal offsetToReduction, BigDecimal ceilingPct,
                             boolean overReliance, String message) {}

    public record Adapter(String name, boolean sandbox, String status, String message) {}

    public record Overview(int year, long held, long available, long retiredThisYear, Offsetting offsetting, Adapter registry, Adapter exchange) {}
}
