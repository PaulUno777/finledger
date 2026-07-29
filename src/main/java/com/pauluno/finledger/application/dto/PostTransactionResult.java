package com.pauluno.finledger.application.dto;

import java.util.List;
import java.util.UUID;

public record PostTransactionResult(
        UUID journalEntryId,
        UUID tenantId,
        String type,
        String transactionReference,
        List<PostingLineView> postings,
        boolean replayed
) {
    public record PostingLineView(
            UUID accountId,
            String amount,
            String currencyCode,
            String settlementStatus
    ) {
    }
}
