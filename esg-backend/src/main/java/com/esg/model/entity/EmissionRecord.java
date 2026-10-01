package com.esg.model.entity;

import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "emission_records", schema = "carbon")
public class EmissionRecord {
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

    @Column(nullable = false, length = 10)
    private String scope;

    @Column(name = "scope3_category")
    private Integer scope3Category;

    @Column(name = "source_activity", nullable = false)
    private String sourceActivity;

    @Column(name = "activity_data", nullable = false)
    private BigDecimal activityData;

    @Column(name = "activity_unit", nullable = false)
    private String activityUnit;

    @Column(name = "emission_factor_id")
    private UUID emissionFactorId;

    @Column(name = "co2e_tonnes", nullable = false)
    private BigDecimal co2eTonnes;

    @Column(name = "co2_tonnes")
    private BigDecimal co2Tonnes;

    @Column(name = "ch4_tonnes")
    private BigDecimal ch4Tonnes;

    @Column(name = "n2o_tonnes")
    private BigDecimal n2oTonnes;

    @Column(name = "reporting_period_start", nullable = false)
    private LocalDate reportingPeriodStart;

    @Column(name = "reporting_period_end", nullable = false)
    private LocalDate reportingPeriodEnd;

    @Column(name = "calculation_method")
    private String calculationMethod;

    @Column(name = "data_quality_score")
    private BigDecimal dataQualityScore;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (isVerified == null) isVerified = false;
    }

    public EmissionRecord() {}

    public UUID getId() { return this.id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return this.tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getOrgId() { return this.orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public String getScope() { return this.scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Integer getScope3Category() { return this.scope3Category; }
    public void setScope3Category(Integer scope3Category) { this.scope3Category = scope3Category; }
    public String getSourceActivity() { return this.sourceActivity; }
    public void setSourceActivity(String sourceActivity) { this.sourceActivity = sourceActivity; }
    public BigDecimal getActivityData() { return this.activityData; }
    public void setActivityData(BigDecimal activityData) { this.activityData = activityData; }
    public String getActivityUnit() { return this.activityUnit; }
    public void setActivityUnit(String activityUnit) { this.activityUnit = activityUnit; }
    public UUID getEmissionFactorId() { return this.emissionFactorId; }
    public void setEmissionFactorId(UUID emissionFactorId) { this.emissionFactorId = emissionFactorId; }
    public BigDecimal getCo2eTonnes() { return this.co2eTonnes; }
    public void setCo2eTonnes(BigDecimal co2eTonnes) { this.co2eTonnes = co2eTonnes; }
    public BigDecimal getCo2Tonnes() { return this.co2Tonnes; }
    public void setCo2Tonnes(BigDecimal co2Tonnes) { this.co2Tonnes = co2Tonnes; }
    public BigDecimal getCh4Tonnes() { return this.ch4Tonnes; }
    public void setCh4Tonnes(BigDecimal ch4Tonnes) { this.ch4Tonnes = ch4Tonnes; }
    public BigDecimal getN2oTonnes() { return this.n2oTonnes; }
    public void setN2oTonnes(BigDecimal n2oTonnes) { this.n2oTonnes = n2oTonnes; }
    public LocalDate getReportingPeriodStart() { return this.reportingPeriodStart; }
    public void setReportingPeriodStart(LocalDate reportingPeriodStart) { this.reportingPeriodStart = reportingPeriodStart; }
    public LocalDate getReportingPeriodEnd() { return this.reportingPeriodEnd; }
    public void setReportingPeriodEnd(LocalDate reportingPeriodEnd) { this.reportingPeriodEnd = reportingPeriodEnd; }
    public String getCalculationMethod() { return this.calculationMethod; }
    public void setCalculationMethod(String calculationMethod) { this.calculationMethod = calculationMethod; }
    public BigDecimal getDataQualityScore() { return this.dataQualityScore; }
    public void setDataQualityScore(BigDecimal dataQualityScore) { this.dataQualityScore = dataQualityScore; }
    public Boolean getIsVerified() { return this.isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }
    public UUID getVerifiedBy() { return this.verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public Instant getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Long getVersion() { return this.version; }
}
