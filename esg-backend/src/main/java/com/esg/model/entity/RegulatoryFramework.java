package com.esg.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "regulatory_frameworks")
public class RegulatoryFramework {
    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String jurisdiction;

    @Column(name = "framework_type")
    private String frameworkType;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    private String description;
    private String version;

    @Column(name = "is_active")
    private Boolean isActive;

    public RegulatoryFramework() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return this.code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }
    public String getJurisdiction() { return this.jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }
    public String getFrameworkType() { return this.frameworkType; }
    public void setFrameworkType(String frameworkType) { this.frameworkType = frameworkType; }
    public LocalDate getEffectiveDate() { return this.effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getDescription() { return this.description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return this.version; }
    public void setVersion(String version) { this.version = version; }
    public Boolean getIsActive() { return this.isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
