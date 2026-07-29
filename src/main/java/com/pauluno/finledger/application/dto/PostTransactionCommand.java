package com.pauluno.finledger.application.dto;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record PostTransactionCommand(
        UUID tenantId,
        String idempotencyKey,
        String transactionReference,
        List<PostingLine> postings
) {
    public record PostingLine(
            UUID accountId,
            String amount,
            String currencyCode,
            String settlementStatus
    ) {
        public Currency currency() {
            return Currency.getInstance(currencyCode);
        }
    }
}
