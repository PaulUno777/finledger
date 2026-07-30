package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.IdempotencyRecordEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.IdempotencyRecordId;

public interface SpringDataIdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordId> {

    Optional<IdempotencyRecordEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
