package com.pauluno.finledger.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Hierarchical tenant (plan §3). Balance and journal data are scoped by {@code id}.
 */
public record Tenant(
        UUID id,
        TenantType type,
        UUID parentTenantId,
        String name
) {
    public Tenant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        switch (type) {
            case STANDALONE, AGGREGATOR -> {
                if (parentTenantId != null) {
                    throw new IllegalArgumentException(type + " must not have a parent");
                }
            }
            case SUB_MERCHANT -> {
                if (parentTenantId == null) {
                    throw new IllegalArgumentException("SUB_MERCHANT requires a parent aggregator");
                }
            }
        }
    }

    public Optional<UUID> parentId() {
        return Optional.ofNullable(parentTenantId);
    }
}
