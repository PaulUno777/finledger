package com.pauluno.finledger.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.application.fraud.RiskOutcome;

public interface RiskDecisionRepository {

    RiskDecisionRecord save(RiskDecisionRecord record);

    long countSyncSince(UUID tenantId, Instant since);

    Optional<RiskDecisionRecord> findAsyncHoldForSource(UUID tenantId, UUID sourceJournalEntryId);

    List<RiskDecisionRecord> findByTransactionReference(UUID tenantId, String transactionReference);

    record RiskDecisionRecord(
            UUID id,
            UUID tenantId,
            UUID journalEntryId,
            UUID sourceJournalEntryId,
            String transactionReference,
            String phase,
            RiskOutcome outcome,
            String reasonCode,
            int score,
            List<String> ruleIds,
            UUID holdJournalEntryId,
            Instant createdAt
    ) {
    }
}
