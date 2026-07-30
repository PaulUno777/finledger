package com.pauluno.finledger.application.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record ReconciliationBreak(
        UUID id,
        UUID tenantId,
        String railReference,
        BigDecimal expectedAmount,
        BigDecimal reportedAmount,
        Currency currency,
        String reason,
        Instant detectedAt,
        UUID reportBatchId
) {
}
