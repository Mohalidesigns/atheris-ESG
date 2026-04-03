package com.esg.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "data_points")
public class DataPoint {
    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "source_id")
    private String sourceId;

    @Column(nullable = false, length = 10)
    private String category;

    @Column(name = "sub_category", nullable = false)
    private String subCategory;

    @Column(name = "metric_key", nullable = false)
    private String metricKey;

    @Column(name = "numeric_value")
    private BigDecimal numericValue;

    @Column(name = "text_value")
    private String textValue;

    private String unit;

    @Column(name = "reporting_period_start", nullable = false)
    private LocalDate reportingPeriodStart;

    @Column(name = "reporting_period_end", nullable = false)
    private LocalDate reportingPeriodEnd;

    @Column(name = "quality_score")
    private BigDecimal qualityScore;

    @Column(name = "validation_status")
    private String validationStatus;

    @Column(name = "validated_by")
    private String validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    private String metadata;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (validationStatus == null) validationStatus = "pending";
    }

    public DataPoint() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return this.tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getOrgId() { return this.orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getSourceId() { return this.sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
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
    public String getValidatedBy() { return this.validatedBy; }
    public void setValidatedBy(String validatedBy) { this.validatedBy = validatedBy; }
    public Instant getValidatedAt() { return this.validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
    public String getMetadata() { return this.metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getCreatedBy() { return this.createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return this.updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
