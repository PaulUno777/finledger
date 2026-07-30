package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class IdempotencyRecordId implements Serializable {

    private UUID tenantId;
    private String idempotencyKey;

    public IdempotencyRecordId() {
    }

    public IdempotencyRecordId(UUID tenantId, String idempotencyKey) {
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyRecordId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, idempotencyKey);
    }
}
