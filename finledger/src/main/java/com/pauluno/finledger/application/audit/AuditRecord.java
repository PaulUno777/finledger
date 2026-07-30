package com.pauluno.finledger.application.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Input to {@link com.pauluno.finledger.application.port.out.AuditLogWriter} before hashing.
 */
public record AuditRecord(
        UUID tenantId,
        Instant occurredAt,
        String actor,
        String action,
        String resourceType,
        UUID resourceId,
        String payloadJson,
        String traceId,
        String spanId
) {
}
