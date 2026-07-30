package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.fraud.RiskOutcome;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.RiskDecisionEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataRiskDecisionRepository;

@Component
public class RiskDecisionJpaAdapter implements RiskDecisionRepository {

    private final SpringDataRiskDecisionRepository repository;

    public RiskDecisionJpaAdapter(SpringDataRiskDecisionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RiskDecisionRecord save(RiskDecisionRecord record) {
        RiskDecisionEntity entity = repository.findById(record.id()).orElseGet(RiskDecisionEntity::new);
        entity.setId(record.id());
        entity.setTenantId(record.tenantId());
        entity.setJournalEntryId(record.journalEntryId());
        entity.setSourceJournalEntryId(record.sourceJournalEntryId());
        entity.setTransactionReference(record.transactionReference());
        entity.setPhase(record.phase());
        entity.setOutcome(record.outcome().name());
        entity.setReasonCode(record.reasonCode());
        entity.setScore(record.score());
        entity.setRuleIds(String.join(",", record.ruleIds()));
        entity.setHoldJournalEntryId(record.holdJournalEntryId());
        entity.setCreatedAt(record.createdAt() == null ? Instant.now() : record.createdAt());
        return toRecord(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public long countSyncSince(UUID tenantId, Instant since) {
        return repository.countSyncAllowedSince(tenantId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskDecisionRecord> findAsyncHoldForSource(UUID tenantId, UUID sourceJournalEntryId) {
        return repository
                .findFirstByTenantIdAndSourceJournalEntryIdAndPhaseAndHoldJournalEntryIdIsNotNull(
                        tenantId, sourceJournalEntryId, "ASYNC")
                .map(RiskDecisionJpaAdapter::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskDecisionRecord> findByTransactionReference(UUID tenantId, String transactionReference) {
        return repository.findByTenantIdAndTransactionReferenceOrderByCreatedAtDesc(tenantId, transactionReference)
                .stream()
                .map(RiskDecisionJpaAdapter::toRecord)
                .toList();
    }

    private static RiskDecisionRecord toRecord(RiskDecisionEntity entity) {
        List<String> rules = Arrays.stream(entity.getRuleIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return new RiskDecisionRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getJournalEntryId(),
                entity.getSourceJournalEntryId(),
                entity.getTransactionReference(),
                entity.getPhase(),
                RiskOutcome.valueOf(entity.getOutcome()),
                entity.getReasonCode(),
                entity.getScore(),
                rules,
                entity.getHoldJournalEntryId(),
                entity.getCreatedAt()
        );
    }
}
