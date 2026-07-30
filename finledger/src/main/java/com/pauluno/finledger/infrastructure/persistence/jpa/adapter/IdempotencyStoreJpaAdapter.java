package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.exception.IdempotencyConflictException;
import com.pauluno.finledger.application.port.out.IdempotencyStore;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.IdempotencyRecordEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataIdempotencyRecordRepository;

@Component
public class IdempotencyStoreJpaAdapter implements IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);

    private final SpringDataIdempotencyRecordRepository springData;
    private final Clock clock;

    public IdempotencyStoreJpaAdapter(SpringDataIdempotencyRecordRepository springData) {
        this.springData = Objects.requireNonNull(springData, "springData");
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional
    public BeginOutcome tryBegin(UUID tenantId, IdempotencyKey key, String requestHash) {
        Optional<IdempotencyRecordEntity> existing = springData
                .findByTenantIdAndIdempotencyKey(tenantId, key.value());
        if (existing.isPresent()) {
            return interpretExisting(existing.get(), requestHash);
        }

        Instant now = clock.instant();
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity();
        entity.setTenantId(tenantId);
        entity.setIdempotencyKey(key.value());
        entity.setRequestHash(requestHash);
        entity.setStatus(IdempotencyStatus.STARTED.name());
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(TTL));

        try {
            springData.saveAndFlush(entity);
            return new BeginOutcome.Proceed();
        } catch (DataIntegrityViolationException ex) {
            IdempotencyRecordEntity raced = springData
                    .findByTenantIdAndIdempotencyKey(tenantId, key.value())
                    .orElseThrow(() -> ex);
            return interpretExisting(raced, requestHash);
        }
    }

    private BeginOutcome interpretExisting(IdempotencyRecordEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was reused with a different request body");
        }
        IdempotencyStatus status = IdempotencyStatus.valueOf(existing.getStatus());
        return switch (status) {
            case COMPLETED -> new BeginOutcome.Replay(existing.getResponseSnapshot());
            case STARTED, FAILED -> throw new IdempotencyConflictException(
                    "IDEMPOTENCY_IN_PROGRESS",
                    "Idempotency-Key is already in progress or previously failed; retry with a new key or wait");
        };
    }

    @Override
    @Transactional
    public void complete(UUID tenantId, IdempotencyKey key, String responseSnapshot) {
        IdempotencyRecordEntity entity = springData
                .findByTenantIdAndIdempotencyKey(tenantId, key.value())
                .orElseThrow(() -> new IllegalStateException("Missing idempotency record for " + key.value()));
        entity.setStatus(IdempotencyStatus.COMPLETED.name());
        entity.setResponseSnapshot(responseSnapshot);
        springData.save(entity);
    }

    @Override
    @Transactional
    public void fail(UUID tenantId, IdempotencyKey key) {
        springData.findByTenantIdAndIdempotencyKey(tenantId, key.value()).ifPresent(entity -> {
            entity.setStatus(IdempotencyStatus.FAILED.name());
            springData.save(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredIdempotency> find(UUID tenantId, IdempotencyKey key) {
        return springData.findByTenantIdAndIdempotencyKey(tenantId, key.value())
                .map(entity -> new StoredIdempotency(
                        entity.getRequestHash(),
                        entity.getResponseSnapshot(),
                        IdempotencyStatus.valueOf(entity.getStatus())
                ));
    }
}
