package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record CreateTenantCommand(
        String name,
        String type,
        UUID parentTenantId,
        UUID id
) {
    /** Convenience when caller does not supply a client UUID. */
    public CreateTenantCommand(String name, String type, UUID parentTenantId) {
        this(name, type, parentTenantId, null);
    }
}
