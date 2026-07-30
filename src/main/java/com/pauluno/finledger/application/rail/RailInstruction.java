package com.pauluno.finledger.application.rail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import com.pauluno.finledger.domain.rail.RailSettlementStatus;

/**
 * Persisted rail payment instruction (manual clearing default).
 */
public record RailInstruction(
        UUID id,
        UUID tenantId,
        String railCode,
        String railReference,
        BigDecimal amount,
        Currency currency,
        RailSettlementStatus status,
        UUID clearingAccountId,
        UUID counterpartyAccountId,
        UUID initiateJournalEntryId,
        UUID settleJournalEntryId,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt
) {
}
