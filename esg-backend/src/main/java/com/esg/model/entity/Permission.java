package com.esg.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "permissions", schema = "auth")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private String action;

    private String description;

    public Permission() {}

    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public String getResource() { return resource; } public void setResource(String resource) { this.resource = resource; }
    public String getAction() { return action; } public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }

    public String getPermissionString() { return resource + ":" + action; }
}
