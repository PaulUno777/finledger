package com.pauluno.finledger.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskDecisionResult(
        UUID id,
        UUID tenantId,
        UUID journalEntryId,
        String transactionReference,
        String phase,
        String outcome,
        String reasonCode,
        int score,
        List<String> ruleIds,
        UUID holdJournalEntryId,
        Instant createdAt
) {
}
