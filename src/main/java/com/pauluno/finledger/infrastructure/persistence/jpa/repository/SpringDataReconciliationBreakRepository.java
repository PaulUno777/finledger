package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.ReconciliationBreakEntity;

public interface SpringDataReconciliationBreakRepository
        extends JpaRepository<ReconciliationBreakEntity, UUID> {

    List<ReconciliationBreakEntity> findByTenantIdOrderByDetectedAtDesc(UUID tenantId);
}
