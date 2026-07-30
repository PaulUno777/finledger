package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.RiskDecisionEntity;

public interface SpringDataRiskDecisionRepository extends JpaRepository<RiskDecisionEntity, UUID> {

    @Query("""
            select count(r) from RiskDecisionEntity r
            where r.tenantId = :tenantId and r.phase = 'SYNC'
              and r.outcome <> 'DENY' and r.createdAt >= :since
            """)
    long countSyncAllowedSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    List<RiskDecisionEntity> findByTenantIdAndTransactionReferenceOrderByCreatedAtDesc(
            UUID tenantId, String transactionReference);

    Optional<RiskDecisionEntity> findFirstByTenantIdAndSourceJournalEntryIdAndPhaseAndHoldJournalEntryIdIsNotNull(
            UUID tenantId, UUID sourceJournalEntryId, String phase);
}
