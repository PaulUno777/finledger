package com.pauluno.finledger.application.port.out;

import java.util.UUID;

/**
 * Broker-agnostic publish port. Concrete adapters (log, Kafka, …) live in infrastructure.
 */
public interface EventPublisher {

    void publish(PublishedEvent event);

    record PublishedEvent(
            UUID id,
            UUID tenantId,
            UUID aggregateId,
            String eventType,
            String payload
    ) {
    }
}
