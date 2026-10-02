package com.esg.datahub;

import com.esg.model.dto.TenancyDtos;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Data Hub API bodies (WP-20). The frontend Zod schemas (features/dataHub/schemas.js) mirror these. */
public final class DataHubDtos {
    private DataHubDtos() {}

    public static final String SOURCE_TYPE_REGEX = "^(manual|csv|excel|api|erp|mobile|iot|sms)$";

    /** A data point as the API returns it, with the actions the caller may take now. */
    public record DataPointResponse(UUID id, UUID orgId, String orgName, UUID sourceId, String sourceName, String category,
                                    String subCategory, String metricKey, String metricName, BigDecimal numericValue,
                                    String textValue, String unit, LocalDate reportingPeriodStart,
                                    LocalDate reportingPeriodEnd, BigDecimal originalAmount, String originalCurrency,
                                    BigDecimal fxRate, LocalDate fxRateDate, String fxRateSource, BigDecimal qualityScore,
                                    JsonNode qualityBreakdown, JsonNode validationWarnings, String validationStatus,
                                    String statusReason, UUID submittedBy, Instant submittedAt, UUID validatedBy,
                                    Instant validatedAt, UUID approvedBy, Instant approvedAt, List<UUID> evidenceFileIds,
                                    UUID importJobId, UUID createdBy, String createdByName, Instant createdAt,
                                    Instant updatedAt, Long version, List<String> actions, String notes,
                                    java.util.Map<String, String> blockedActions) {}

    /** Body of a workflow step. A reason is required to reject or reopen. */
    public record TransitionRequest(Long version, @Size(max = 1000) String reason) {}

    /** One line of a data point's history, derived from the immutable audit trail. */
    public record HistoryEntry(Instant at, UUID userId, String userName, String action, String fromStatus, String toStatus,
                               List<String> changedFields, String reason) {}

    public record DataSourceRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = SOURCE_TYPE_REGEX, message = "must be one of manual, csv, excel, api, erp, mobile, iot, sms") String sourceType,
            @Size(max = 500) String description,
            @NotNull Boolean active,
            Long version) {}

    public record DataSourceResponse(UUID id, String name, String sourceType, String description, boolean active,
                                     Instant lastSyncAt, long dataPoints, Long version) {}

    public record FxRateRequest(
            @NotBlank @Pattern(regexp = TenancyDtos.CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") String fromCurrency,
            @NotBlank @Pattern(regexp = TenancyDtos.CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") String toCurrency,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 10, fraction = 10) BigDecimal rate,
            @NotNull LocalDate rateDate,
            @NotBlank @Size(max = 255) String source,
            Long version) {}

    public record FxRateResponse(UUID id, String fromCurrency, String toCurrency, BigDecimal rate, LocalDate rateDate,
                                 String source, Instant createdAt, Long version) {}
}
