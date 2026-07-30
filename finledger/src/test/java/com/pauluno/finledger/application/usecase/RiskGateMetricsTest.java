package com.pauluno.finledger.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.fraud.RiskOutcome;
import com.pauluno.finledger.application.port.out.LedgerMetrics;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort;

@Tag("unit")
class RiskGateMetricsTest {

    @Test
    void should_record_risk_denied_metric_on_deny() {
        RecordingMetrics metrics = new RecordingMetrics();
        RiskGateService gate = new RiskGateService(
                request -> new TransactionRiskCheckPort.RiskDecision(
                        RiskOutcome.DENY, "AMOUNT_THRESHOLD", 90, List.of("max-amount")),
                emptyConfigs(),
                discardingDecisions(),
                metrics
        );

        assertThatThrownBy(() -> gate.assessAndEnforce(
                new TransactionRiskCheckPort.RiskCheckRequest(
                        UUID.randomUUID(), "tx", List.of(), java.time.Instant.now())))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(metrics.deniedReasons).containsExactly("AMOUNT_THRESHOLD");
        assertThat(metrics.posted).isZero();
    }

    private static TenantFraudConfigRepository emptyConfigs() {
        return new TenantFraudConfigRepository() {
            @Override
            public com.pauluno.finledger.application.fraud.TenantFraudConfig save(
                    com.pauluno.finledger.application.fraud.TenantFraudConfig config) {
                return config;
            }

            @Override
            public Optional<com.pauluno.finledger.application.fraud.TenantFraudConfig> findByTenantId(UUID tenantId) {
                return Optional.empty();
            }
        };
    }

    private static RiskDecisionRepository discardingDecisions() {
        return new RiskDecisionRepository() {
            @Override
            public RiskDecisionRecord save(RiskDecisionRecord record) {
                return record;
            }

            @Override
            public long countSyncSince(UUID tenantId, java.time.Instant since) {
                return 0;
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

    private static final class RecordingMetrics implements LedgerMetrics {
        private final List<String> deniedReasons = new ArrayList<>();
        private int posted;

        @Override
        public void journalPosted() {
            posted++;
        }

        @Override
        public void riskDenied(String reasonCode) {
            deniedReasons.add(reasonCode);
        }
    }
}
