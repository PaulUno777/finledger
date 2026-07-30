package com.pauluno.finledger.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.fraud.FraudFailMode;
import com.pauluno.finledger.application.fraud.RiskOutcome;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.out.LedgerMetrics;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort.RiskCheckRequest;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort.RiskDecision;

/**
 * Sync risk gate for posting use cases (plan §17).
 */
@Service
public class RiskGateService {

    private static final Logger log = LoggerFactory.getLogger(RiskGateService.class);

    private final TransactionRiskCheckPort riskCheckPort;
    private final TenantFraudConfigRepository fraudConfigRepository;
    private final RiskDecisionRepository riskDecisionRepository;
    private final LedgerMetrics ledgerMetrics;

    public RiskGateService(
            TransactionRiskCheckPort riskCheckPort,
            TenantFraudConfigRepository fraudConfigRepository,
            RiskDecisionRepository riskDecisionRepository,
            LedgerMetrics ledgerMetrics
    ) {
        this.riskCheckPort = riskCheckPort;
        this.fraudConfigRepository = fraudConfigRepository;
        this.riskDecisionRepository = riskDecisionRepository;
        this.ledgerMetrics = ledgerMetrics;
    }

    /**
     * Runs sync check and persists the decision. Throws on DENY.
     *
     * @return decision (ALLOW or REVIEW)
     */
    public RiskDecision assessAndEnforce(RiskCheckRequest request) {
        TenantFraudConfig config = fraudConfigRepository.findByTenantId(request.tenantId())
                .orElseGet(() -> TenantFraudConfig.defaults(request.tenantId()));

        RiskDecision decision;
        try {
            decision = riskCheckPort.check(request);
        } catch (RuntimeException ex) {
            if (config.failMode() == FraudFailMode.CLOSED) {
                log.error("Risk check failed CLOSED for tenantId={}", request.tenantId(), ex);
                decision = new RiskDecision(RiskOutcome.DENY, "RISK_CHECK_ERROR", 100, List.of("fail-closed"));
            } else {
                log.warn("Risk check failed OPEN for tenantId={}: {}", request.tenantId(), ex.toString());
                decision = new RiskDecision(RiskOutcome.ALLOW, "RISK_CHECK_ERROR_OPEN", 0, List.of("fail-open"));
            }
        }

        persistSync(request, decision, null);

        if (decision.outcome() == RiskOutcome.DENY) {
            ledgerMetrics.riskDenied(decision.reasonCode());
            throw new BusinessRuleException(
                    "RISK_DENIED",
                    "Transaction denied by risk check: " + decision.reasonCode());
        }
        return decision;
    }

    public void attachJournalEntry(UUID tenantId, String transactionReference, UUID journalEntryId) {
        List<RiskDecisionRepository.RiskDecisionRecord> rows =
                riskDecisionRepository.findByTransactionReference(tenantId, transactionReference);
        for (RiskDecisionRepository.RiskDecisionRecord row : rows) {
            if ("SYNC".equals(row.phase()) && row.journalEntryId() == null) {
                riskDecisionRepository.save(new RiskDecisionRepository.RiskDecisionRecord(
                        row.id(),
                        row.tenantId(),
                        journalEntryId,
                        row.sourceJournalEntryId(),
                        row.transactionReference(),
                        row.phase(),
                        row.outcome(),
                        row.reasonCode(),
                        row.score(),
                        row.ruleIds(),
                        row.holdJournalEntryId(),
                        row.createdAt()
                ));
            }
        }
    }

    private void persistSync(RiskCheckRequest request, RiskDecision decision, UUID journalEntryId) {
        riskDecisionRepository.save(new RiskDecisionRepository.RiskDecisionRecord(
                UUID.randomUUID(),
                request.tenantId(),
                journalEntryId,
                null,
                request.transactionReference(),
                "SYNC",
                decision.outcome(),
                decision.reasonCode(),
                decision.score(),
                decision.ruleIds(),
                null,
                Instant.now()
        ));
    }
}
