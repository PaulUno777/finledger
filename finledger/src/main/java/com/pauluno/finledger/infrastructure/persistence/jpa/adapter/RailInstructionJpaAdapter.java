package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.RailInstructionRepository;
import com.pauluno.finledger.application.rail.RailInstruction;
import com.pauluno.finledger.domain.rail.RailSettlementStatus;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.RailInstructionEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataRailInstructionRepository;

@Component
public class RailInstructionJpaAdapter implements RailInstructionRepository {

    private final SpringDataRailInstructionRepository repository;

    public RailInstructionJpaAdapter(SpringDataRailInstructionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RailInstruction save(RailInstruction instruction) {
        RailInstructionEntity entity = repository.findById(instruction.id())
                .orElseGet(RailInstructionEntity::new);
        entity.setId(instruction.id());
        entity.setTenantId(instruction.tenantId());
        entity.setRailCode(instruction.railCode());
        entity.setRailReference(instruction.railReference());
        entity.setAmount(instruction.amount());
        entity.setCurrencyCode(instruction.currency().getCurrencyCode());
        entity.setStatus(instruction.status().name());
        entity.setClearingAccountId(instruction.clearingAccountId());
        entity.setCounterpartyAccountId(instruction.counterpartyAccountId());
        entity.setInitiateJournalEntryId(instruction.initiateJournalEntryId());
        entity.setSettleJournalEntryId(instruction.settleJournalEntryId());
        entity.setIdempotencyKey(instruction.idempotencyKey());
        entity.setCreatedAt(instruction.createdAt());
        entity.setUpdatedAt(instruction.updatedAt());
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RailInstruction> findByTenantAndReference(UUID tenantId, String railReference) {
        return repository.findByTenantIdAndRailReference(tenantId, railReference).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RailInstruction> findByTenantAndIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RailInstruction> findByTenantAndReferences(UUID tenantId, List<String> railReferences) {
        if (railReferences == null || railReferences.isEmpty()) {
            return List.of();
        }
        return repository.findByTenantIdAndRailReferenceIn(tenantId, railReferences).stream()
                .map(this::toDomain)
                .toList();
    }

    private RailInstruction toDomain(RailInstructionEntity entity) {
        return new RailInstruction(
                entity.getId(),
                entity.getTenantId(),
                entity.getRailCode(),
                entity.getRailReference(),
                entity.getAmount(),
                Currency.getInstance(entity.getCurrencyCode()),
                RailSettlementStatus.valueOf(entity.getStatus()),
                entity.getClearingAccountId(),
                entity.getCounterpartyAccountId(),
                entity.getInitiateJournalEntryId(),
                entity.getSettleJournalEntryId(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
