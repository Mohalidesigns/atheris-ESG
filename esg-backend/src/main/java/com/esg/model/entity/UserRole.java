package com.esg.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_roles")
public class UserRole {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    @Column(name = "org_scope_id")
    private String orgScopeId;

    @Column(name = "granted_by")
    private String grantedBy;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    public UserRole() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return this.userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRoleId() { return this.roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getOrgScopeId() { return this.orgScopeId; }
    public void setOrgScopeId(String orgScopeId) { this.orgScopeId = orgScopeId; }
    public String getGrantedBy() { return this.grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public Instant getGrantedAt() { return this.grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
    public Instant getExpiresAt() { return this.expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Role getRole() { return this.role; }
    public void setRole(Role role) { this.role = role; }
}
