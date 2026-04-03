package com.esg.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;

    @Column(name = "mfa_enabled")
    private Boolean mfaEnabled;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    private String timezone;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "failed_login_count")
    private Integer failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (failedLoginCount == null) failedLoginCount = 0;
        if (isActive == null) isActive = true;
        if (mfaEnabled == null) mfaEnabled = false;
    }

    public User() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return this.tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEmail() { return this.email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return this.passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return this.firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return this.lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhone() { return this.phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Boolean getMfaEnabled() { return this.mfaEnabled; }
    public void setMfaEnabled(Boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    public String getMfaSecret() { return this.mfaSecret; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }
    public String getPreferredLanguage() { return this.preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getTimezone() { return this.timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Boolean getIsActive() { return this.isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Instant getLastLoginAt() { return this.lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getPasswordChangedAt() { return this.passwordChangedAt; }
    public void setPasswordChangedAt(Instant passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }
    public Integer getFailedLoginCount() { return this.failedLoginCount; }
    public void setFailedLoginCount(Integer failedLoginCount) { this.failedLoginCount = failedLoginCount; }
    public Instant getLockedUntil() { return this.lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public Instant getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return this.updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
