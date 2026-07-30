package com.pauluno.finledger.application.dto;

import java.util.List;
import java.util.UUID;

public record ConfigureSplitRulesCommand(
        UUID tenantId,
        String ruleSetKey,
        List<RuleLine> rules,
        String remainderTarget
) {
    public record RuleLine(String targetAccountType, String percentage) {
    }
}
