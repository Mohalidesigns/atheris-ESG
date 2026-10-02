package com.esg.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An ESG data point (TRD §4.3 data_hub.data_points, WP-20). Workflow: draft → submitted → validated → approved, or
 * rejected back to the submitter; approved points are locked by a database trigger until reopened with a reason.
 */
@Entity
@Table(name = "data_points", schema = "data_hub")
public class DataPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(nullable = false, length = 10)
    private String category;

    @Column(name = "sub_category", nullable = false)
    private String subCategory;

    /** Free-text note, e.g. a meter remark or an import comment (F3). */
    @Column(name = "notes", length = 1000)
    private String notes;

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

    @Column(name = "original_amount")
    private BigDecimal originalAmount;

    @Column(name = "original_currency")
    private String originalCurrency;

    @Column(name = "fx_rate")
    private BigDecimal fxRate;

    @Column(name = "fx_rate_date")
    private LocalDate fxRateDate;

    @Column(name = "fx_rate_source")
    private String fxRateSource;

    @Column(name = "quality_score")
    private BigDecimal qualityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_breakdown")
    private String qualityBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_warnings", nullable = false)
    private String validationWarnings = "[]";

    @Column(name = "validation_status", nullable = false)
    private String validationStatus;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "evidence_file_ids", columnDefinition = "uuid[]")
    private UUID[] evidenceFileIds;

    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (validationStatus == null) validationStatus = "draft";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public DataPoint() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getVersion() { return version; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getOrgId() { return orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public BigDecimal getNumericValue() { return numericValue; }
    public void setNumericValue(BigDecimal numericValue) { this.numericValue = numericValue; }
    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getReportingPeriodStart() { return reportingPeriodStart; }
    public void setReportingPeriodStart(LocalDate reportingPeriodStart) { this.reportingPeriodStart = reportingPeriodStart; }
    public LocalDate getReportingPeriodEnd() { return reportingPeriodEnd; }
    public void setReportingPeriodEnd(LocalDate reportingPeriodEnd) { this.reportingPeriodEnd = reportingPeriodEnd; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public String getOriginalCurrency() { return originalCurrency; }
    public void setOriginalCurrency(String originalCurrency) { this.originalCurrency = originalCurrency; }
    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }
    public LocalDate getFxRateDate() { return fxRateDate; }
    public void setFxRateDate(LocalDate fxRateDate) { this.fxRateDate = fxRateDate; }
    public String getFxRateSource() { return fxRateSource; }
    public void setFxRateSource(String fxRateSource) { this.fxRateSource = fxRateSource; }
    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }
    public String getQualityBreakdown() { return qualityBreakdown; }
    public void setQualityBreakdown(String qualityBreakdown) { this.qualityBreakdown = qualityBreakdown; }
    public String getValidationWarnings() { return validationWarnings; }
    public void setValidationWarnings(String validationWarnings) { this.validationWarnings = validationWarnings; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public UUID getValidatedBy() { return validatedBy; }
    public void setValidatedBy(UUID validatedBy) { this.validatedBy = validatedBy; }
    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public UUID[] getEvidenceFileIds() { return evidenceFileIds; }
    public void setEvidenceFileIds(UUID[] evidenceFileIds) { this.evidenceFileIds = evidenceFileIds; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public UUID getImportJobId() { return importJobId; }
    public void setImportJobId(UUID importJobId) { this.importJobId = importJobId; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
