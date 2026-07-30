package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TenantAncestryId implements Serializable {

    private UUID ancestorId;
    private UUID descendantId;

    public TenantAncestryId() {
    }

    public TenantAncestryId(UUID ancestorId, UUID descendantId) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TenantAncestryId that)) {
            return false;
        }
        return Objects.equals(ancestorId, that.ancestorId)
                && Objects.equals(descendantId, that.descendantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ancestorId, descendantId);
    }
}
