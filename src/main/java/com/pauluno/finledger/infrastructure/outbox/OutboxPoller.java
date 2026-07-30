package com.pauluno.finledger.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.EventPublisher;
import com.pauluno.finledger.application.port.out.OutboxEventRepository;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;

    public OutboxPoller(OutboxEventRepository outboxEventRepository, EventPublisher eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void poll() {
        var pending = outboxEventRepository.claimPending(BATCH_SIZE);
        for (OutboxEventRepository.OutboxEvent event : pending) {
            try {
                eventPublisher.publish(new EventPublisher.PublishedEvent(
                        event.id(),
                        event.tenantId(),
                        event.aggregateId(),
                        event.eventType(),
                        event.payload()
                ));
                outboxEventRepository.markPublished(event.id());
            } catch (RuntimeException ex) {
                log.warn("Failed to publish outbox event id={}: {}", event.id(), ex.getMessage());
                outboxEventRepository.markFailed(event.id());
            }
        }
    }
}
