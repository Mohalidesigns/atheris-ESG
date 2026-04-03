package com.esg.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "emission_factors")
public class EmissionFactor {
    @Id
    private String id;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String category;

    @Column(name = "sub_category")
    private String subCategory;

    @Column(name = "factor_value", nullable = false)
    private BigDecimal factorValue;

    @Column(nullable = false)
    private String unit;

    private String source;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_custom")
    private Boolean isCustom;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "created_at")
    private Instant createdAt;

    public EmissionFactor() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getRegion() { return this.region; }
    public void setRegion(String region) { this.region = region; }
    public String getCategory() { return this.category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubCategory() { return this.subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public BigDecimal getFactorValue() { return this.factorValue; }
    public void setFactorValue(BigDecimal factorValue) { this.factorValue = factorValue; }
    public String getUnit() { return this.unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSource() { return this.source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getValidFrom() { return this.validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return this.validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public Boolean getIsCustom() { return this.isCustom; }
    public void setIsCustom(Boolean isCustom) { this.isCustom = isCustom; }
    public String getTenantId() { return this.tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Instant getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
