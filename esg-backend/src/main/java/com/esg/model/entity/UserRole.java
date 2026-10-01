package com.esg.model.entity;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "user_roles", schema = "auth")
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "org_scope_id")
    private UUID orgScopeId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    public UserRole() {}

    public UUID getId() { return this.id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return this.userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getRoleId() { return this.roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public UUID getOrgScopeId() { return this.orgScopeId; }
    public void setOrgScopeId(UUID orgScopeId) { this.orgScopeId = orgScopeId; }
    public UUID getGrantedBy() { return this.grantedBy; }
    public void setGrantedBy(UUID grantedBy) { this.grantedBy = grantedBy; }
    public Instant getGrantedAt() { return this.grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
    public Instant getExpiresAt() { return this.expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Role getRole() { return this.role; }
    public void setRole(Role role) { this.role = role; }

    public Long getVersion() { return this.version; }
}
