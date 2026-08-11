package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record ProvisionPlatformCommand(
        String recipe,
        String name,
        UUID tenantId,
        String currencyCode,
        String idempotencyKey
) {
}
