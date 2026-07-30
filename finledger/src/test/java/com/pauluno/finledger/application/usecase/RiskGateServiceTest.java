package com.pauluno.finledger.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.fraud.FraudFailMode;
import com.pauluno.finledger.application.fraud.RiskOutcome;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort;

@Tag("unit")
class RiskGateServiceTest {

    @Test
    void should_fail_open_when_port_throws_and_mode_open() {
        RiskGateService gate = new RiskGateService(
                request -> {
                    throw new RuntimeException("boom");
                },
                emptyConfigs(),
                capturingDecisions()
        );

        TransactionRiskCheckPort.RiskDecision decision = gate.assessAndEnforce(
                new TransactionRiskCheckPort.RiskCheckRequest(
                        UUID.randomUUID(), "tx", List.of(), java.time.Instant.now()));
        assertThat(decision.outcome()).isEqualTo(RiskOutcome.ALLOW);
        assertThat(decision.reasonCode()).isEqualTo("RISK_CHECK_ERROR_OPEN");
    }

    @Test
    void should_fail_closed_when_port_throws_and_mode_closed() {
        UUID tenantId = UUID.randomUUID();
        RiskGateService gate = new RiskGateService(
                request -> {
                    throw new RuntimeException("boom");
                },
                fixedConfig(new TenantFraudConfig(
                        tenantId, true, FraudFailMode.CLOSED, null, 0, 3600, null, List.of())),
                capturingDecisions()
        );

        assertThatThrownBy(() -> gate.assessAndEnforce(
                new TransactionRiskCheckPort.RiskCheckRequest(
                        tenantId, "tx", List.of(), java.time.Instant.now())))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).code())
                .isEqualTo("RISK_DENIED");
    }

    private static TenantFraudConfigRepository emptyConfigs() {
        return new TenantFraudConfigRepository() {
            @Override
            public TenantFraudConfig save(TenantFraudConfig config) {
                return config;
            }

            @Override
            public Optional<TenantFraudConfig> findByTenantId(UUID tenantId) {
                return Optional.empty();
            }
        };
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

    private static RiskDecisionRepository capturingDecisions() {
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
}
