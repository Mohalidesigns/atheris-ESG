package com.esg.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "organisations")
public class Organisation {
    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "parent_org_id")
    private String parentOrgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "org_type")
    private String orgType;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "state_region")
    private String stateRegion;

    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Organisation() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return this.tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getParentOrgId() { return this.parentOrgId; }
    public void setParentOrgId(String parentOrgId) { this.parentOrgId = parentOrgId; }
    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }
    public String getOrgType() { return this.orgType; }
    public void setOrgType(String orgType) { this.orgType = orgType; }
    public String getCountryCode() { return this.countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getStateRegion() { return this.stateRegion; }
    public void setStateRegion(String stateRegion) { this.stateRegion = stateRegion; }
    public String getAddress() { return this.address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getLatitude() { return this.latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return this.longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public Boolean getIsActive() { return this.isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Instant getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
