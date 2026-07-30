package com.pauluno.finledger.application.port.in;

import java.util.List;
import java.util.UUID;

import com.pauluno.finledger.application.dto.RiskDecisionResult;

public interface ListRiskDecisionsUseCase {
    List<RiskDecisionResult> execute(UUID tenantId, String transactionReference);
}
