package com.pauluno.finledger.infrastructure.fraud;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pauluno.finledger.application.event.TransactionPosted;
import com.pauluno.finledger.application.fraud.RiskOutcome;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.in.HoldFundsForReviewUseCase;
import com.pauluno.finledger.application.port.out.EventPublisher;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort;
import com.pauluno.finledger.application.tenant.TenantContext;
import com.pauluno.finledger.infrastructure.messaging.EventPublisherConfig;

/**
 * Async fraud scoring on TransactionPosted (plan §17 / ADR-011). Optional HOLD when configured.
 * HOLD is idempotent via {@code fraud-hold-{journalEntryId}} + risk_decision lookup.
 */
@Component
public class AsyncFraudHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncFraudHandler.class);

    private final TransactionRiskCheckPort riskCheckPort;
    private final TenantFraudConfigRepository fraudConfigRepository;
    private final RiskDecisionRepository riskDecisionRepository;
    private final HoldFundsForReviewUseCase holdFundsForReviewUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public AsyncFraudHandler(
            TransactionRiskCheckPort riskCheckPort,
            TenantFraudConfigRepository fraudConfigRepository,
            RiskDecisionRepository riskDecisionRepository,
            HoldFundsForReviewUseCase holdFundsForReviewUseCase
    ) {
        this.riskCheckPort = riskCheckPort;
        this.fraudConfigRepository = fraudConfigRepository;
        this.riskDecisionRepository = riskDecisionRepository;
        this.holdFundsForReviewUseCase = holdFundsForReviewUseCase;
    }

    @Async(EventPublisherConfig.FRAUD_ASYNC_EXECUTOR)
    public void onPublishedAsync(EventPublisher.PublishedEvent event) {
        onPublished(event);
    }

    public void onPublished(EventPublisher.PublishedEvent event) {
        if (!TransactionPosted.EVENT_TYPE.equals(event.eventType())) {
            return;
        }
        try {
            TransactionPosted posted = objectMapper.readValue(event.payload(), TransactionPosted.class);
            handle(posted);
        } catch (Exception ex) {
            log.warn("Async fraud handler failed for event {}: {}", event.id(), ex.toString());
        }
    }

    void handle(TransactionPosted posted) {
        TenantFraudConfig config = fraudConfigRepository.findByTenantId(posted.tenantId())
                .orElseGet(() -> TenantFraudConfig.defaults(posted.tenantId()));
        if (!config.enabled()) {
            return;
        }

        TenantContext.set(posted.tenantId());
        try {
            List<TransactionRiskCheckPort.PostingLeg> legs = posted.postings().stream()
                    .map(p -> new TransactionRiskCheckPort.PostingLeg(
                            p.accountId(),
                            null,
                            new java.math.BigDecimal(p.amount()),
                            p.currencyCode()
                    ))
                    .toList();
            TransactionRiskCheckPort.RiskDecision decision = riskCheckPort.check(
                    new TransactionRiskCheckPort.RiskCheckRequest(
                            posted.tenantId(),
                            posted.transactionReference(),
                            legs,
                            posted.occurredAt()
                    ));

            // Elevate borderline sync REVIEW / high score for async
            RiskOutcome outcome = decision.outcome();
            int score = Math.min(100, decision.score() + 10);
            if (outcome == RiskOutcome.ALLOW && score >= 60) {
                outcome = RiskOutcome.REVIEW;
            }

            UUID holdJournalId = null;
            if ((outcome == RiskOutcome.REVIEW || outcome == RiskOutcome.DENY)
                    && config.holdAccountId() != null) {
                Optional<UUID> held = holdFundsForReviewUseCase.execute(posted, config.holdAccountId());
                holdJournalId = held.orElse(null);
            }

            riskDecisionRepository.save(new RiskDecisionRepository.RiskDecisionRecord(
                    UUID.randomUUID(),
                    posted.tenantId(),
                    posted.journalEntryId(),
                    posted.journalEntryId(),
                    posted.transactionReference(),
                    "ASYNC",
                    outcome,
                    decision.reasonCode(),
                    score,
                    decision.ruleIds(),
                    holdJournalId,
                    Instant.now()
            ));
        } finally {
            TenantContext.clear();
        }
    }
}
