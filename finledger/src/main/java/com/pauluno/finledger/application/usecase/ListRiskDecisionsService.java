package com.pauluno.finledger.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.RiskDecisionResult;
import com.pauluno.finledger.application.port.in.ListRiskDecisionsUseCase;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;

@Service
public class ListRiskDecisionsService implements ListRiskDecisionsUseCase {

    private final RiskDecisionRepository repository;

    public ListRiskDecisionsService(RiskDecisionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskDecisionResult> execute(UUID tenantId, String transactionReference) {
        return repository.findByTransactionReference(tenantId, transactionReference).stream()
                .map(r -> new RiskDecisionResult(
                        r.id(),
                        r.tenantId(),
                        r.journalEntryId(),
                        r.transactionReference(),
                        r.phase(),
                        r.outcome().name(),
                        r.reasonCode(),
                        r.score(),
                        r.ruleIds(),
                        r.holdJournalEntryId(),
                        r.createdAt()
                ))
                .toList();
    }
}
