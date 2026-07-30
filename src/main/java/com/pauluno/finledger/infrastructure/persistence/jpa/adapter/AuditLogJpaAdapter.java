package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.AuditLogRepository;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.AuditLogEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataAuditLogRepository;

@Component
public class AuditLogJpaAdapter implements AuditLogRepository {

    private final SpringDataAuditLogRepository repository;

    public AuditLogJpaAdapter(SpringDataAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Optional<StoredAuditEntry> findLatestForUpdate(UUID tenantId) {
        return repository.findLatestForUpdate(tenantId).map(AuditLogJpaAdapter::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredAuditEntry> findAllOrdered(UUID tenantId) {
        return repository.findByTenantIdOrderByOccurredAtAscIdAsc(tenantId).stream()
                .map(AuditLogJpaAdapter::toStored)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findTenantIdsWithAuditActivity() {
        return repository.findDistinctTenantIds();
    }

    static StoredAuditEntry toStored(AuditLogEntity entity) {
        return new StoredAuditEntry(
                entity.getId(),
                entity.getTenantId(),
                entity.getOccurredAt(),
                entity.getActor(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getPayload(),
                entity.getPayloadHash(),
                entity.getPrevHash(),
                entity.getCurrentHash(),
                entity.getTraceId(),
                entity.getSpanId()
        );
    }
}
