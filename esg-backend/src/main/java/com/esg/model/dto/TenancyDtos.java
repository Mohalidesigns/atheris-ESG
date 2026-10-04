package com.esg.model.dto;

import com.esg.security.PasswordPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
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

/**
 * WP-14 request/response bodies: organisations, reporting periods, tenant settings, platform tenants.
 * The frontend Zod schemas (features/admin/schemas.js) mirror these rules exactly.
 */
public final class TenancyDtos {
    private TenancyDtos() {}

    public static final String ORG_TYPE_REGEX = "^(group|headquarters|subsidiary|business_unit|facility|site)$";
    public static final String COUNTRY_REGEX = "^[A-Z]{2,3}$";
    public static final String CURRENCY_REGEX = "^(NGN|USD|EUR|GBP|KES|ZAR|GHS|XOF|EGP)$";
    public static final String PERIOD_TYPE_REGEX = "^(monthly|quarterly|annual)$";
    public static final String TIER_REGEX = "^(starter|professional|enterprise)$";
    public static final String SLUG_REGEX = "^[a-z0-9]+(-[a-z0-9]+)*$";

    // ---------------------------------------------------------------- organisations

    public record OrganisationRequest(
            @NotBlank @Size(max = 255) String name,
            UUID parentOrgId,
            @NotBlank @Pattern(regexp = ORG_TYPE_REGEX, message = "must be one of group, headquarters, subsidiary, business_unit, facility, site") String orgType,
            @Pattern(regexp = COUNTRY_REGEX, message = "must be an ISO country code, e.g. NG") String countryCode,
            @Size(max = 100) String stateRegion,
            @Size(max = 500) String address,
            @DecimalMin(value = "-90") @DecimalMax(value = "90") BigDecimal latitude,
            @DecimalMin(value = "-180") @DecimalMax(value = "180") BigDecimal longitude,
            Long version) {}

    public record OrganisationResponse(UUID id, UUID parentOrgId, String name, String orgType, String countryCode,
                                       String stateRegion, String address, BigDecimal latitude, BigDecimal longitude,
                                       boolean active, Long version) {}

    // ---------------------------------------------------------------- reporting periods

    public record PeriodRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = PERIOD_TYPE_REGEX, message = "must be monthly, quarterly or annual") String periodType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            Long version) {}

    public record PeriodResponse(UUID id, String name, String periodType, LocalDate startDate, LocalDate endDate,
                                 String status, Instant lockedAt, Long version) {}

    // ---------------------------------------------------------------- tenant settings

    public record SettingsRequest(
            @NotBlank @Pattern(regexp = CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") String baseCurrency,
            @NotNull @Min(1) @Max(12) Integer fiscalYearStartMonth,
            @NotBlank @Size(max = 50) String reportingTimezone,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 50) String> defaultFrameworkCodes,
            @NotNull Long version,
            /** GWP-100 set for per-gas emission factors (WP-21); null keeps the current one. */
            @Pattern(regexp = "^(AR5|AR6)$", message = "must be AR5 or AR6") String gwpSet,
            /** Emission factors still pending SME review (S7): allow_flagged (used, marked provisional) or block; null keeps it. */
            @Pattern(regexp = "^(allow_flagged|block)$", message = "must be allow_flagged or block") String provisionalFactorPolicy) {}

    public record SettingsResponse(String tenantName, String baseCurrency, int fiscalYearStartMonth,
                                   String reportingTimezone, List<String> defaultFrameworkCodes, Long version, String gwpSet,
                                   String provisionalFactorPolicy) {}

    // ---------------------------------------------------------------- platform tenants (SUPER_ADMIN)

    public record FirstAdmin(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password) {}

    public record CreateTenantRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = SLUG_REGEX, message = "lower-case letters, digits and single hyphens") String slug,
            @NotBlank @Pattern(regexp = COUNTRY_REGEX, message = "must be an ISO country code, e.g. NG") String countryCode,
            @Size(max = 100) String industrySector,
            @NotBlank @Pattern(regexp = TIER_REGEX, message = "must be starter, professional or enterprise") String subscriptionTier,
            @NotBlank @Pattern(regexp = CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") String billingCurrency,
            @NotNull @Min(1) @Max(100000) Integer maxUsers,
            @NotNull @Valid FirstAdmin admin) {}

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String industrySector,
            @NotBlank @Pattern(regexp = TIER_REGEX, message = "must be starter, professional or enterprise") String subscriptionTier,
            @NotNull @Min(1) @Max(100000) Integer maxUsers,
            @NotNull Boolean active,
            @NotNull Long version) {}

    public record TenantResponse(UUID id, String name, String slug, String countryCode, String industrySector,
                                 String subscriptionTier, String billingCurrency, boolean active, int maxUsers,
                                 Instant createdAt, Long version, long users) {}

    // ---------------------------------------------------------------- admin account recovery

    public record SetPasswordRequest(@NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password) {}
}
