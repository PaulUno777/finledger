package com.pauluno.finledger.infrastructure.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.fraud.FraudFailMode;
import com.pauluno.finledger.application.fraud.RiskOutcome;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort;

@Tag("unit")
class RuleBasedTransactionRiskCheckTest {

    @Test
    void should_deny_when_amount_exceeds_threshold() {
        UUID tenantId = UUID.randomUUID();
        TenantFraudConfigRepository configs = fixedConfig(new TenantFraudConfig(
                tenantId, true, FraudFailMode.OPEN, new BigDecimal("100.00"),
                0, 3600, null, List.of()));
        RuleBasedTransactionRiskCheck check = new RuleBasedTransactionRiskCheck(
                configs, emptyDecisions());

        TransactionRiskCheckPort.RiskDecision decision = check.check(new TransactionRiskCheckPort.RiskCheckRequest(
                tenantId,
                "tx-1",
                List.of(new TransactionRiskCheckPort.PostingLeg(
                        UUID.randomUUID(), "merchant", new BigDecimal("150.00"), "USD")),
                Instant.now()
        ));

        assertThat(decision.outcome()).isEqualTo(RiskOutcome.DENY);
        assertThat(decision.reasonCode()).isEqualTo("AMOUNT_THRESHOLD");
    }

    @Test
    void should_deny_denylisted_owner() {
        UUID tenantId = UUID.randomUUID();
        TenantFraudConfigRepository configs = fixedConfig(new TenantFraudConfig(
                tenantId, true, FraudFailMode.OPEN, null,
                0, 3600, null, List.of("bad-actor")));
        RuleBasedTransactionRiskCheck check = new RuleBasedTransactionRiskCheck(
                configs, emptyDecisions());

        TransactionRiskCheckPort.RiskDecision decision = check.check(new TransactionRiskCheckPort.RiskCheckRequest(
                tenantId,
                "tx-2",
                List.of(new TransactionRiskCheckPort.PostingLeg(
                        UUID.randomUUID(), "bad-actor", new BigDecimal("10.00"), "USD")),
                Instant.now()
        ));

        assertThat(decision.outcome()).isEqualTo(RiskOutcome.DENY);
        assertThat(decision.reasonCode()).isEqualTo("OWNER_DENYLISTED");
    }

    @Test
    void should_review_when_velocity_exceeded() {
        UUID tenantId = UUID.randomUUID();
        TenantFraudConfigRepository configs = fixedConfig(new TenantFraudConfig(
                tenantId, true, FraudFailMode.OPEN, null,
                2, 3600, null, List.of()));
        RuleBasedTransactionRiskCheck check = new RuleBasedTransactionRiskCheck(
                configs, countingDecisions(2));

        TransactionRiskCheckPort.RiskDecision decision = check.check(new TransactionRiskCheckPort.RiskCheckRequest(
                tenantId,
                "tx-3",
                List.of(new TransactionRiskCheckPort.PostingLeg(
                        UUID.randomUUID(), "m", new BigDecimal("1.00"), "USD")),
                Instant.now()
        ));

        assertThat(decision.outcome()).isEqualTo(RiskOutcome.REVIEW);
        assertThat(decision.reasonCode()).isEqualTo("VELOCITY_EXCEEDED");
    }

    private static TenantFraudConfigRepository fixedConfig(TenantFraudConfig config) {
        return new TenantFraudConfigRepository() {
            @Override
            public TenantFraudConfig save(TenantFraudConfig c) {
                return c;
            }

            @Override
            public Optional<TenantFraudConfig> findByTenantId(UUID tenantId) {
                return Optional.of(config);
            }
        };
    }

    private static RiskDecisionRepository emptyDecisions() {
        return countingDecisions(0);
    }

    private static RiskDecisionRepository countingDecisions(long count) {
        return new RiskDecisionRepository() {
            @Override
            public RiskDecisionRecord save(RiskDecisionRecord record) {
                return record;
            }

            @Override
            public long countSyncSince(UUID tenantId, Instant since) {
                return count;
            }

            @Override
            public Optional<RiskDecisionRecord> findAsyncHoldForSource(UUID tenantId, UUID sourceJournalEntryId) {
                return Optional.empty();
            }

            @Override
            public List<RiskDecisionRecord> findByTransactionReference(UUID tenantId, String transactionReference) {
                return List.of();
            }
        };
    }
}
