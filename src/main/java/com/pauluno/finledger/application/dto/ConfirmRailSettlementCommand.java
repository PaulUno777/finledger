package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record ConfirmRailSettlementCommand(
        UUID tenantId,
        String railReference,
        String idempotencyKey
) {
}
