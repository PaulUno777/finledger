package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record InitiateRailPaymentCommand(
        UUID tenantId,
        String idempotencyKey,
        String railCode,
        String amount,
        String currencyCode,
        UUID clearingAccountId,
        UUID counterpartyAccountId,
        String clientReference
) {
}
