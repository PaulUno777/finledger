package com.pauluno.finledger.application.dto;

import java.util.Map;
import java.util.UUID;

public record PostSplitPaymentCommand(
        UUID tenantId,
        String idempotencyKey,
        String transactionReference,
        String totalAmount,
        String currencyCode,
        UUID sourceAccountId,
        Map<String, UUID> accountsByType,
        String ruleSetKey
) {
}
