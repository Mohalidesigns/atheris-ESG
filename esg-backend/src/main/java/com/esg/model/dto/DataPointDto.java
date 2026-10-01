package com.esg.model.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create/update body for a data point (WP-20). Category, unit and sub-category default from the metric catalogue.
 * For currency metrics, {@code numericValue} is the amount in {@code currency} (default: the tenant base currency);
 * other currencies are converted with the tenant's dated exchange rate.
 */
public class DataPointDto {
    private UUID id;
    @NotNull private UUID orgId;
    /** Optional: derived from the metric's pillar; if sent it must match. */
    @Pattern(regexp = "^[ESG]$", message = "must be E, S or G") private String category;
    @Size(max = 100) private String subCategory;
    @NotBlank @Size(max = 100) private String metricKey;
    @Digits(integer = 14, fraction = 6) private BigDecimal numericValue;
    @Size(max = 2000) private String textValue;
    /** Optional: the metric's unit; if sent it must match. */
    @Size(max = 50) private String unit;
    @NotNull private LocalDate reportingPeriodStart;
    @NotNull private LocalDate reportingPeriodEnd;
    private BigDecimal qualityScore;
    private String validationStatus;
    private String metadata;
    private UUID sourceId;
    @Pattern(regexp = TenancyDtos.CURRENCY_REGEX, message = "must be one of NGN, USD, EUR, GBP, KES, ZAR, GHS, XOF, EGP") private String currency;
    @Size(max = 10, message = "at most 10 evidence files") private List<UUID> evidenceFileIds;
    /** Required on update (optimistic locking). */
    private Long version;

    public DataPointDto() {}

    public UUID getId() { return this.id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrgId() { return this.orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public String getCategory() { return this.category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubCategory() { return this.subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public String getMetricKey() { return this.metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public BigDecimal getNumericValue() { return this.numericValue; }
    public void setNumericValue(BigDecimal numericValue) { this.numericValue = numericValue; }
    public String getTextValue() { return this.textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
    public String getUnit() { return this.unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getReportingPeriodStart() { return this.reportingPeriodStart; }
    public void setReportingPeriodStart(LocalDate reportingPeriodStart) { this.reportingPeriodStart = reportingPeriodStart; }
    public LocalDate getReportingPeriodEnd() { return this.reportingPeriodEnd; }
    public void setReportingPeriodEnd(LocalDate reportingPeriodEnd) { this.reportingPeriodEnd = reportingPeriodEnd; }
    public BigDecimal getQualityScore() { return this.qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }
    public String getValidationStatus() { return this.validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public String getMetadata() { return this.metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public UUID getSourceId() { return this.sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public String getCurrency() { return this.currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public List<UUID> getEvidenceFileIds() { return this.evidenceFileIds; }
    public void setEvidenceFileIds(List<UUID> evidenceFileIds) { this.evidenceFileIds = evidenceFileIds; }
    public Long getVersion() { return this.version; }
    public void setVersion(Long version) { this.version = version; }
}
