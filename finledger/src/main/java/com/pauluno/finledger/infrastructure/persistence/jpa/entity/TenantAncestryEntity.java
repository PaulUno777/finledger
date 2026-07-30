package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_ancestry")
@IdClass(TenantAncestryId.class)
public class TenantAncestryEntity {

    @Id
    @Column(name = "ancestor_id", nullable = false)
    private UUID ancestorId;

    @Id
    @Column(name = "descendant_id", nullable = false)
    private UUID descendantId;

    public TenantAncestryEntity() {
    }

    public TenantAncestryEntity(UUID ancestorId, UUID descendantId) {
        this.ancestorId = ancestorId;
        this.descendantId = descendantId;
    }

    public UUID getAncestorId() {
        return ancestorId;
    }

    public void setAncestorId(UUID ancestorId) {
        this.ancestorId = ancestorId;
    }

    public UUID getDescendantId() {
        return descendantId;
    }

    public void setDescendantId(UUID descendantId) {
        this.descendantId = descendantId;
    }
}
