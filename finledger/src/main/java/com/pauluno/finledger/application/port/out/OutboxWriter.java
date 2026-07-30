package com.pauluno.finledger.application.port.out;

import java.util.UUID;

/**
 * Appends an outbox row in the current DB transaction (plan §9).
 */
public interface OutboxWriter {

    void append(OutboxMessage message);

    record OutboxMessage(
            UUID tenantId,
            UUID aggregateId,
            String eventType,
            String payload
    ) {
    }
}
