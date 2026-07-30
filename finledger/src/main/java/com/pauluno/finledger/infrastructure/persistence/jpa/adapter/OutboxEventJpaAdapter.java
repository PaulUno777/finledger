package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.OutboxEventRepository;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataOutboxEventRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
public class OutboxEventJpaAdapter implements OutboxEventRepository {

    private final SpringDataOutboxEventRepository springData;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public OutboxEventJpaAdapter(SpringDataOutboxEventRepository springData) {
        this.springData = Objects.requireNonNull(springData, "springData");
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<OutboxEvent> claimPending(int limit) {
        List<OutboxEventEntity> rows = entityManager.createNativeQuery(
                        """
                        SELECT * FROM outbox_event
                        WHERE status = 'PENDING'
                        ORDER BY created_at ASC
                        FOR UPDATE SKIP LOCKED
                        """,
                        OutboxEventEntity.class)
                .setMaxResults(limit)
                .getResultList();

        return rows.stream()
                .map(entity -> new OutboxEvent(
                        entity.getId(),
                        entity.getTenantId(),
                        entity.getAggregateId(),
                        entity.getEventType(),
                        entity.getPayload(),
                        OutboxStatus.valueOf(entity.getStatus())
                ))
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id) {
        OutboxEventEntity entity = springData.findById(id)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + id));
        entity.setStatus(OutboxStatus.PUBLISHED.name());
        entity.setPublishedAt(clock.instant());
        springData.save(entity);
    }

    @Override
    @Transactional
    public void markFailed(UUID id) {
        OutboxEventEntity entity = springData.findById(id)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + id));
        entity.setStatus(OutboxStatus.FAILED.name());
        springData.save(entity);
    }
}
