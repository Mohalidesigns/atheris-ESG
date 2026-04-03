package com.esg.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public class DataPointDto {
    private String id;
    @NotBlank private String orgId;
    @NotBlank private String category;
    @NotBlank private String subCategory;
    @NotBlank private String metricKey;
    private BigDecimal numericValue;
    private String textValue;
    private String unit;
    @NotNull private LocalDate reportingPeriodStart;
    @NotNull private LocalDate reportingPeriodEnd;
    private BigDecimal qualityScore;
    private String validationStatus;
    private String metadata;
    private String sourceId;

    public DataPointDto() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getOrgId() { return this.orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
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
    public String getSourceId() { return this.sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
}
