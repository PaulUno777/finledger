package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.OutboxEventRepository;
import com.pauluno.finledger.application.port.out.OutboxWriter;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataOutboxEventRepository;

@Component
public class OutboxWriterJpaAdapter implements OutboxWriter {

    private final SpringDataOutboxEventRepository springData;
    private final Clock clock;

    public OutboxWriterJpaAdapter(SpringDataOutboxEventRepository springData) {
        this.springData = Objects.requireNonNull(springData, "springData");
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    public void append(OutboxMessage message) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(message.tenantId());
        entity.setAggregateId(message.aggregateId());
        entity.setEventType(message.eventType());
        entity.setPayload(message.payload());
        entity.setStatus(OutboxEventRepository.OutboxStatus.PENDING.name());
        entity.setCreatedAt(clock.instant());
        springData.save(entity);
    }
}
