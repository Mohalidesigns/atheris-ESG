package com.esg.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {
    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_system_role")
    private Boolean isSystemRole;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions;

    public Role() {}

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return this.tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return this.description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsSystemRole() { return this.isSystemRole; }
    public void setIsSystemRole(Boolean isSystemRole) { this.isSystemRole = isSystemRole; }
    public Instant getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Set<Permission> getPermissions() { return this.permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
}
