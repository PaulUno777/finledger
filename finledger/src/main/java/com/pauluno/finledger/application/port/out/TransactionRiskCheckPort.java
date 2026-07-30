package com.pauluno.finledger.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.pauluno.finledger.application.fraud.RiskOutcome;

/**
 * Synchronous fraud / risk gate (plan §17). Default in-box is no-op ALLOW.
 */
public interface TransactionRiskCheckPort {

    RiskDecision check(RiskCheckRequest request);

    record RiskCheckRequest(
            UUID tenantId,
            String transactionReference,
            List<PostingLeg> postings,
            Instant occurredAt
    ) {
        public RiskCheckRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(transactionReference, "transactionReference");
            Objects.requireNonNull(postings, "postings");
            Objects.requireNonNull(occurredAt, "occurredAt");
            postings = List.copyOf(postings);
        }
    }

    record PostingLeg(
            UUID accountId,
            String ownerRef,
            BigDecimal amount,
            String currencyCode
    ) {
        public PostingLeg {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currencyCode, "currencyCode");
        }
    }

    record RiskDecision(
            RiskOutcome outcome,
            String reasonCode,
            int score,
            List<String> ruleIds
    ) {
        public RiskDecision {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(reasonCode, "reasonCode");
            ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be 0..100");
            }
        }

        public static RiskDecision allow() {
            return new RiskDecision(RiskOutcome.ALLOW, "NOOP", 0, List.of());
        }
    }
}
