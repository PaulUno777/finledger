package com.pauluno.finledger.application.dto;

import java.util.UUID;

public record RefundTransactionCommand(
        UUID tenantId,
        String idempotencyKey,
        String transactionReference,
        UUID originalJournalEntryId,
        String refundAmount,
        String currencyCode
) {
}
