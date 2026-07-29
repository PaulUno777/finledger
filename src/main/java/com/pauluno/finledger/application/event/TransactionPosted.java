package com.pauluno.finledger.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event emitted after a journal entry is successfully posted.
 * Carried through the transactional outbox (plan §9) — never published inline.
 */
public record TransactionPosted(
        UUID tenantId,
        UUID journalEntryId,
        String transactionReference,
        String type,
        List<PostingSummary> postings,
        Instant occurredAt
) {
    public record PostingSummary(
            UUID accountId,
            String amount,
            String currencyCode,
            String settlementStatus
    ) {
    }

    public static final String EVENT_TYPE = "TransactionPosted";
}
