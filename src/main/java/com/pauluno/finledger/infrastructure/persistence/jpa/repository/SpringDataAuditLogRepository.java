package com.pauluno.finledger.infrastructure.persistence.jpa.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pauluno.finledger.infrastructure.persistence.jpa.entity.AuditLogEntity;

public interface SpringDataAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    @Query(value = """
            SELECT * FROM audit_log
            WHERE tenant_id = :tenantId
            ORDER BY occurred_at DESC, id DESC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<AuditLogEntity> findLatestForUpdate(@Param("tenantId") UUID tenantId);

    List<AuditLogEntity> findByTenantIdOrderByOccurredAtAscIdAsc(UUID tenantId);

    @Query("select distinct a.tenantId from AuditLogEntity a")
    List<UUID> findDistinctTenantIds();
}
