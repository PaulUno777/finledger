package com.pauluno.finledger.infrastructure.fraud;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.fraud.RiskOutcome;
import com.pauluno.finledger.application.fraud.TenantFraudConfig;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.application.port.out.TenantFraudConfigRepository;
import com.pauluno.finledger.application.port.out.TransactionRiskCheckPort;

@Component
@ConditionalOnProperty(name = "finledger.fraud.enabled", havingValue = "true")
public class RuleBasedTransactionRiskCheck implements TransactionRiskCheckPort {

    private final TenantFraudConfigRepository fraudConfigRepository;
    private final RiskDecisionRepository riskDecisionRepository;

    public RuleBasedTransactionRiskCheck(
            TenantFraudConfigRepository fraudConfigRepository,
            RiskDecisionRepository riskDecisionRepository
    ) {
        this.fraudConfigRepository = fraudConfigRepository;
        this.riskDecisionRepository = riskDecisionRepository;
    }

    @Override
    public RiskDecision check(RiskCheckRequest request) {
        TenantFraudConfig config = fraudConfigRepository.findByTenantId(request.tenantId())
                .orElseGet(() -> TenantFraudConfig.defaults(request.tenantId()));

        if (!config.enabled()) {
            return RiskDecision.allow();
        }

        List<String> fired = new ArrayList<>();
        int score = 0;

        for (PostingLeg leg : request.postings()) {
            String owner = leg.ownerRef() == null ? "" : leg.ownerRef();
            if (config.denylistOwnerRefs().stream()
                    .anyMatch(d -> d.equalsIgnoreCase(owner))) {
                fired.add("denylist");
                return new RiskDecision(RiskOutcome.DENY, "OWNER_DENYLISTED", 100, fired);
            }
        }

        BigDecimal maxAbs = request.postings().stream()
                .map(p -> p.amount().abs())
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (config.maxAmount() != null && maxAbs.compareTo(config.maxAmount()) > 0) {
            fired.add("max-amount");
            score = Math.max(score, 90);
            return new RiskDecision(RiskOutcome.DENY, "AMOUNT_THRESHOLD", score, fired);
        }

        if (config.velocityMax() > 0) {
            Instant since = request.occurredAt().minusSeconds(config.velocityWindowSeconds());
            long recent = riskDecisionRepository.countSyncSince(request.tenantId(), since);
            if (recent >= config.velocityMax()) {
                fired.add("velocity");
                score = Math.max(score, 70);
                return new RiskDecision(RiskOutcome.REVIEW, "VELOCITY_EXCEEDED", score, fired);
            }
        }

        if (maxAbs.compareTo(BigDecimal.ZERO) > 0 && config.maxAmount() != null) {
            BigDecimal half = config.maxAmount().multiply(new BigDecimal("0.5"));
            if (maxAbs.compareTo(half) > 0) {
                fired.add("amount-review");
                return new RiskDecision(RiskOutcome.REVIEW, "AMOUNT_REVIEW", 50, fired);
            }
        }

        return new RiskDecision(RiskOutcome.ALLOW, "RULES_PASS", score, fired.isEmpty()
                ? List.of("pass")
                : List.copyOf(fired));
    }
}
