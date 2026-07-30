package com.pauluno.finledger.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Declarative split configuration for a tenant (plan §5.1).
 */
public record SplitRuleSet(
        String key,
        List<SplitRule> rules,
        AccountType remainderTarget
) {
    public SplitRuleSet {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(remainderTarget, "remainderTarget");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        rules = List.copyOf(rules);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (SplitRule rule : rules) {
            sum = sum.add(rule.percentage());
        }
        if (sum.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "sum of rule percentages must be <= 100 (was " + sum + ")");
        }
    }
}
