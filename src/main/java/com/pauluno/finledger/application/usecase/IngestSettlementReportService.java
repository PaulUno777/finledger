package com.pauluno.finledger.application.usecase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.IngestSettlementReportCommand;
import com.pauluno.finledger.application.dto.IngestSettlementReportResult;
import com.pauluno.finledger.application.port.in.IngestSettlementReportUseCase;
import com.pauluno.finledger.application.port.out.RailInstructionRepository;
import com.pauluno.finledger.application.port.out.ReconciliationBreakRepository;
import com.pauluno.finledger.application.rail.RailInstruction;
import com.pauluno.finledger.application.reconciliation.RailSettlementReconciler;
import com.pauluno.finledger.application.reconciliation.ReconciliationBreak;

@Service
public class IngestSettlementReportService implements IngestSettlementReportUseCase {

    private final RailInstructionRepository railInstructionRepository;
    private final ReconciliationBreakRepository reconciliationBreakRepository;

    public IngestSettlementReportService(
            RailInstructionRepository railInstructionRepository,
            ReconciliationBreakRepository reconciliationBreakRepository
    ) {
        this.railInstructionRepository = railInstructionRepository;
        this.reconciliationBreakRepository = reconciliationBreakRepository;
    }

    @Override
    @Transactional
    @Auditable(action = "INGEST_SETTLEMENT_REPORT", resourceType = "RECONCILIATION")
    public IngestSettlementReportResult execute(IngestSettlementReportCommand command) {
        UUID batchId = command.reportBatchId() != null ? command.reportBatchId() : UUID.randomUUID();
        List<String> refs = command.lines().stream()
                .map(IngestSettlementReportCommand.ReportLine::railReference)
                .toList();
        List<RailInstruction> instructions =
                railInstructionRepository.findByTenantAndReferences(command.tenantId(), refs);

        List<ReconciliationBreak> breaks = RailSettlementReconciler.match(
                command.tenantId(),
                batchId,
                command.lines(),
                instructions,
                Instant.now()
        );

        List<UUID> breakIds = new ArrayList<>();
        for (ReconciliationBreak br : breaks) {
            breakIds.add(reconciliationBreakRepository.save(br).id());
        }

        int matched = command.lines().size() - breaks.size();
        return new IngestSettlementReportResult(batchId, matched, breaks.size(), breakIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationBreak> listBreaks(UUID tenantId) {
        return reconciliationBreakRepository.findByTenant(tenantId);
    }
}
