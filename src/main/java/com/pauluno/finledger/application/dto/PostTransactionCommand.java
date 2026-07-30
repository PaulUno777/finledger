package com.pauluno.finledger.application.dto;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record PostTransactionCommand(
        UUID tenantId,
        String idempotencyKey,
        String transactionReference,
        List<PostingLine> postings,
        ExchangeHint exchange
) {
    public PostTransactionCommand(
            UUID tenantId,
            String idempotencyKey,
            String transactionReference,
            List<PostingLine> postings
    ) {
        this(tenantId, idempotencyKey, transactionReference, postings, null);
    }

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

    /**
     * When present, the resolved FX rate is frozen onto the journal entry (plan §4.1).
     */
    public record ExchangeHint(
            String baseCurrencyCode,
            String quoteCurrencyCode,
            Instant asOf
    ) {
    }
}
