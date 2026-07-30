package com.pauluno.finledger.application.dto;

import java.util.List;
import java.util.UUID;

public record SplitRuleSetResult(
        UUID tenantId,
        String ruleSetKey,
        List<RuleLineView> rules,
        String remainderTarget
) {
    public record RuleLineView(String targetAccountType, String percentage) {
    }
}
