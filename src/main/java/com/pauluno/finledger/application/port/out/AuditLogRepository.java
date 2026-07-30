package com.pauluno.finledger.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository {

    Optional<StoredAuditEntry> findLatestForUpdate(UUID tenantId);

    List<StoredAuditEntry> findAllOrdered(UUID tenantId);

    List<UUID> findTenantIdsWithAuditActivity();

    record StoredAuditEntry(
            UUID id,
            UUID tenantId,
            Instant occurredAt,
            String actor,
            String action,
            String resourceType,
            UUID resourceId,
            String payload,
            String payloadHash,
            String prevHash,
            String currentHash,
            String traceId,
            String spanId
    ) {
    }
}
