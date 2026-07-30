package com.pauluno.finledger.infrastructure.fraud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort;

@Component
@ConditionalOnProperty(name = "finledger.fraud.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpTransactionRiskCheck implements TransactionRiskCheckPort {

    @Override
    public RiskDecision check(RiskCheckRequest request) {
        return RiskDecision.allow();
    }
}
