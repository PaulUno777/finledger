package com.pauluno.finledger.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.SplitPlan;
import com.pauluno.finledger.domain.model.SplitRule;
import com.pauluno.finledger.domain.model.SplitRuleSet;

/**
 * Pure evaluator: percentages → credit legs with exact remainder (plan §5.1).
 */
public final class SplitPlanEvaluator {

    private SplitPlanEvaluator() {
    }

    /**
     * @param accountsByType map of account type → ledger account id for this payment
     */
    public static SplitPlan evaluate(
            SplitRuleSet ruleSet,
            Money totalAmount,
            Map<AccountType, UUID> accountsByType
    ) {
        Objects.requireNonNull(ruleSet, "ruleSet");
        Objects.requireNonNull(totalAmount, "totalAmount");
        Objects.requireNonNull(accountsByType, "accountsByType");
        if (totalAmount.isNegative() || totalAmount.isZero()) {
            throw new InvalidJournalEntryException("Split total must be positive");
        }

        Map<AccountType, Money> byType = new EnumMap<>(AccountType.class);
        Money allocated = Money.zero(totalAmount.currency());

        for (SplitRule rule : ruleSet.rules()) {
            UUID accountId = accountsByType.get(rule.targetAccountType());
            if (accountId == null) {
                throw new InvalidJournalEntryException(
                        "Missing account for split target type " + rule.targetAccountType());
            }
            BigDecimal raw = totalAmount.amount()
                    .multiply(rule.percentage())
                    .divide(new BigDecimal("100"), totalAmount.currency().getDefaultFractionDigits(),
                            RoundingMode.HALF_EVEN);
            Money leg = Money.of(raw, totalAmount.currency());
            byType.merge(rule.targetAccountType(), leg, Money::plus);
            allocated = allocated.plus(leg);
        }

        Money remainder = totalAmount.minus(allocated);
        if (!remainder.isZero()) {
            if (accountsByType.get(ruleSet.remainderTarget()) == null) {
                throw new InvalidJournalEntryException(
                        "Missing account for remainder target " + ruleSet.remainderTarget());
            }
            byType.merge(ruleSet.remainderTarget(), remainder, Money::plus);
        }

        List<SplitPlan.SplitLeg> legs = new ArrayList<>();
        Money credits = Money.zero(totalAmount.currency());
        for (Map.Entry<AccountType, Money> entry : byType.entrySet()) {
            if (entry.getValue().isZero()) {
                continue;
            }
            UUID accountId = accountsByType.get(entry.getKey());
            legs.add(new SplitPlan.SplitLeg(accountId, entry.getValue()));
            credits = credits.plus(entry.getValue());
        }

        if (!credits.amount().equals(totalAmount.amount())) {
            throw new InvalidJournalEntryException(
                    "Split credits (" + credits.amount() + ") do not equal total ("
                            + totalAmount.amount() + ")");
        }
        return new SplitPlan(legs);
    }
}
