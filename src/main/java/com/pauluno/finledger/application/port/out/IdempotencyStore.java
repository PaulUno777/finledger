package com.pauluno.finledger.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.IdempotencyKey;

public interface IdempotencyStore {

    /**
     * Attempts to claim the idempotency key for a new request.
     * Returns {@link BeginOutcome.Proceed} when the caller should execute the business operation,
     * or {@link BeginOutcome.Replay} when a completed response must be returned as-is.
     */
    BeginOutcome tryBegin(UUID tenantId, IdempotencyKey key, String requestHash);

    void complete(UUID tenantId, IdempotencyKey key, String responseSnapshot);

    void fail(UUID tenantId, IdempotencyKey key);

    Optional<StoredIdempotency> find(UUID tenantId, IdempotencyKey key);

    sealed interface BeginOutcome {
        record Proceed() implements BeginOutcome {
        }

        record Replay(String responseSnapshot) implements BeginOutcome {
        }
    }

    record StoredIdempotency(
            String requestHash,
            String responseSnapshot,
            IdempotencyStatus status
    ) {
    }

    enum IdempotencyStatus {
        STARTED,
        COMPLETED,
        FAILED
    }
}
