package com.pauluno.finledger.infrastructure.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.Session;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.audit.AuditRecord;
import com.pauluno.finledger.application.port.out.AuditLogWriter;
import com.pauluno.finledger.domain.audit.AuditHashChain;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.AuditLogEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataAuditLogRepository;

import jakarta.persistence.EntityManager;

@Component
public class JpaAuditLogWriter implements AuditLogWriter {

    private final SpringDataAuditLogRepository repository;
    private final EntityManager entityManager;

    public JpaAuditLogWriter(SpringDataAuditLogRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public void append(AuditRecord record) {
        // Ensure RLS visibility for this tenant even when TX began without
        // TenantContext
        // (e.g. CreateTenant) — SET LOCAL is scoped to the current transaction.
        setTenantGuc(record.tenantId());

        String prevHash = repository.findLatestForUpdate(record.tenantId())
                .map(AuditLogEntity::getCurrentHash)
                .orElse(AuditHashChain.GENESIS_PREV_HASH);

        Instant occurredAt = AuditHashChain.truncateToMicros(record.occurredAt());
        String payloadHash = AuditHashChain.payloadHash(record.payloadJson());
        String currentHash = AuditHashChain.currentHash(
                prevHash, payloadHash, occurredAt, record.actor());

        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(record.tenantId());
        entity.setOccurredAt(occurredAt);
        entity.setActor(record.actor());
        entity.setAction(record.action());
        entity.setResourceType(record.resourceType());
        entity.setResourceId(record.resourceId());
        entity.setPayload(record.payloadJson());
        entity.setPayloadHash(payloadHash);
        entity.setPrevHash(prevHash);
        entity.setCurrentHash(currentHash);
        entity.setTraceId(record.traceId());
        entity.setSpanId(record.spanId());
        repository.save(entity);
    }

    private void setTenantGuc(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.rls_bypass', 'off', true)");
                statement.execute(
                        "SELECT set_config('app.current_tenant_id', '"
                                + tenantId.toString().replace("'", "''")
                                + "', true)");
            }
        });
    }
}
