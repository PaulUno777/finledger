package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.LedgerAccountEntity;

public interface SpringDataLedgerAccountRepository extends JpaRepository<LedgerAccountEntity, UUID> {

    Optional<LedgerAccountEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LedgerAccountEntity> findByTenantIdOrderByOwnerRefAscIdAsc(UUID tenantId);
}
