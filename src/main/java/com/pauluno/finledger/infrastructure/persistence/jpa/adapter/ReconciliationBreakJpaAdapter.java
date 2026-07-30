package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.ReconciliationBreakRepository;
import com.pauluno.finledger.application.reconciliation.ReconciliationBreak;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.ReconciliationBreakEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataReconciliationBreakRepository;

@Component
public class ReconciliationBreakJpaAdapter implements ReconciliationBreakRepository {

    private final SpringDataReconciliationBreakRepository repository;

    public ReconciliationBreakJpaAdapter(SpringDataReconciliationBreakRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ReconciliationBreak save(ReconciliationBreak reconciliationBreak) {
        ReconciliationBreakEntity entity = new ReconciliationBreakEntity();
        entity.setId(reconciliationBreak.id());
        entity.setTenantId(reconciliationBreak.tenantId());
        entity.setRailReference(reconciliationBreak.railReference());
        entity.setExpectedAmount(reconciliationBreak.expectedAmount());
        entity.setReportedAmount(reconciliationBreak.reportedAmount());
        entity.setCurrencyCode(reconciliationBreak.currency().getCurrencyCode());
        entity.setReason(reconciliationBreak.reason());
        entity.setDetectedAt(reconciliationBreak.detectedAt());
        entity.setReportBatchId(reconciliationBreak.reportBatchId());
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationBreak> findByTenant(UUID tenantId) {
        return repository.findByTenantIdOrderByDetectedAtDesc(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    private ReconciliationBreak toDomain(ReconciliationBreakEntity entity) {
        return new ReconciliationBreak(
                entity.getId(),
                entity.getTenantId(),
                entity.getRailReference(),
                entity.getExpectedAmount(),
                entity.getReportedAmount(),
                Currency.getInstance(entity.getCurrencyCode()),
                entity.getReason(),
                entity.getDetectedAt(),
                entity.getReportBatchId()
        );
    }
}
