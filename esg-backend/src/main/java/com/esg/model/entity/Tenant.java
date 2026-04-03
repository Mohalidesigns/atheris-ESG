package com.esg.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    private String id;
    @Column(nullable = false) private String name;
    @Column(unique = true, nullable = false) private String slug;
    @Column(name = "subscription_tier", nullable = false) private String subscriptionTier;
    @Column(name = "country_code", nullable = false) private String countryCode;
    @Column(name = "industry_sector") private String industrySector;
    @Column(name = "billing_currency", nullable = false) private String billingCurrency;
    @Column(name = "is_active", nullable = false) private Boolean isActive;
    @Column(name = "max_users", nullable = false) private Integer maxUsers;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Tenant() {}

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; } public void setSlug(String slug) { this.slug = slug; }
    public String getSubscriptionTier() { return subscriptionTier; } public void setSubscriptionTier(String v) { this.subscriptionTier = v; }
    public String getCountryCode() { return countryCode; } public void setCountryCode(String v) { this.countryCode = v; }
    public String getIndustrySector() { return industrySector; } public void setIndustrySector(String v) { this.industrySector = v; }
    public String getBillingCurrency() { return billingCurrency; } public void setBillingCurrency(String v) { this.billingCurrency = v; }
    public Boolean getIsActive() { return isActive; } public void setIsActive(Boolean v) { this.isActive = v; }
    public Integer getMaxUsers() { return maxUsers; } public void setMaxUsers(Integer v) { this.maxUsers = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { this.updatedAt = v; }

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = Instant.now(); if (updatedAt == null) updatedAt = Instant.now(); }
    @PreUpdate public void preUpdate() { updatedAt = Instant.now(); }
}
