package com.pauluno.finledger.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pauluno.finledger.application.port.out.EventPublisher;

/**
 * Default in-box EventPublisher — logs the event. Replace with Kafka/etc. via optional adapter.
 */
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(PublishedEvent event) {
        log.info(
                "Publishing outbox event id={} type={} tenantId={} aggregateId={} payload={}",
                event.id(),
                event.eventType(),
                event.tenantId(),
                event.aggregateId(),
                event.payload()
        );
    }
}
