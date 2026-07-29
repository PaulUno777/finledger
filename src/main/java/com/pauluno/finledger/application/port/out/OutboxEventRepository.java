package com.pauluno.finledger.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Poller-facing outbox operations (claim pending rows, mark published/failed).
 */
public interface OutboxEventRepository {

    List<OutboxEvent> claimPending(int limit);

    void markPublished(UUID id);

    void markFailed(UUID id);

    record OutboxEvent(
            UUID id,
            UUID tenantId,
            UUID aggregateId,
            String eventType,
            String payload,
            OutboxStatus status
    ) {
    }

    enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
}
