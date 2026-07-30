package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record CreateTenantResult(
        UUID tenantId,
        String name,
        String type,
        UUID parentTenantId
) {
}
