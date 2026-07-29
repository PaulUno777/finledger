package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.JournalEntryEntity;

public interface SpringDataJournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {

    Optional<JournalEntryEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
