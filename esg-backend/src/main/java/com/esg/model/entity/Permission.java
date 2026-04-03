package com.esg.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permissions")
public class Permission {
    @Id
    private String id;

    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private String action;

    private String description;

    public Permission() {}

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getResource() { return resource; } public void setResource(String resource) { this.resource = resource; }
    public String getAction() { return action; } public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }

    public String getPermissionString() { return resource + ":" + action; }
}
