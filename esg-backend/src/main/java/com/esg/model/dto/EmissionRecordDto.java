package com.esg.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EmissionRecordDto {
    private String id;
    @NotBlank private String orgId;
    @NotBlank private String scope;
    private Integer scope3Category;
    @NotBlank private String sourceActivity;
    @NotNull private BigDecimal activityData;
    @NotBlank private String activityUnit;
    private String emissionFactorId;
    private BigDecimal co2eTonnes;
    @NotNull private LocalDate reportingPeriodStart;
    @NotNull private LocalDate reportingPeriodEnd;
    private String calculationMethod;
    private Boolean isVerified;

    public EmissionRecordDto() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getOrgId() { return this.orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
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
    public String getEmissionFactorId() { return this.emissionFactorId; }
    public void setEmissionFactorId(String emissionFactorId) { this.emissionFactorId = emissionFactorId; }
    public BigDecimal getCo2eTonnes() { return this.co2eTonnes; }
    public void setCo2eTonnes(BigDecimal co2eTonnes) { this.co2eTonnes = co2eTonnes; }
    public LocalDate getReportingPeriodStart() { return this.reportingPeriodStart; }
    public void setReportingPeriodStart(LocalDate reportingPeriodStart) { this.reportingPeriodStart = reportingPeriodStart; }
    public LocalDate getReportingPeriodEnd() { return this.reportingPeriodEnd; }
    public void setReportingPeriodEnd(LocalDate reportingPeriodEnd) { this.reportingPeriodEnd = reportingPeriodEnd; }
    public String getCalculationMethod() { return this.calculationMethod; }
    public void setCalculationMethod(String calculationMethod) { this.calculationMethod = calculationMethod; }
    public Boolean getIsVerified() { return this.isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }
}
