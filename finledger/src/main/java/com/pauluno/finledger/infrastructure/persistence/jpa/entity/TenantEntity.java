package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant")
public class TenantEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_type", nullable = false, length = 32)
    private String tenantType;

    @Column(name = "parent_tenant_id")
    private UUID parentTenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TenantEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantType() {
        return tenantType;
    }

    public void setTenantType(String tenantType) {
        this.tenantType = tenantType;
    }

    public UUID getParentTenantId() {
        return parentTenantId;
    }

    public void setParentTenantId(UUID parentTenantId) {
        this.parentTenantId = parentTenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
