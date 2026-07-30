package com.pauluno.finledger.application.usecase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.ConfigureSplitRulesCommand;
import com.pauluno.finledger.application.dto.SplitRuleSetResult;
import com.pauluno.finledger.application.port.in.ConfigureSplitRulesUseCase;
import com.pauluno.finledger.application.port.out.SplitRuleSetRepository;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.SplitRule;
import com.pauluno.finledger.domain.model.SplitRuleSet;

@Service
public class ConfigureSplitRulesService implements ConfigureSplitRulesUseCase {

    private final SplitRuleSetRepository splitRuleSetRepository;

    public ConfigureSplitRulesService(SplitRuleSetRepository splitRuleSetRepository) {
        this.splitRuleSetRepository = splitRuleSetRepository;
    }

    @Override
    @Transactional
    @Auditable(action = "CONFIGURE_SPLIT_RULES", resourceType = "SPLIT_RULE_SET")
    public SplitRuleSetResult execute(ConfigureSplitRulesCommand command) {
        List<SplitRule> rules = new ArrayList<>();
        for (ConfigureSplitRulesCommand.RuleLine line : command.rules()) {
            rules.add(new SplitRule(
                    AccountType.valueOf(line.targetAccountType()),
                    new BigDecimal(line.percentage())
            ));
        }
        SplitRuleSet ruleSet = new SplitRuleSet(
                command.ruleSetKey(),
                rules,
                AccountType.valueOf(command.remainderTarget())
        );
        SplitRuleSet saved = splitRuleSetRepository.save(command.tenantId(), ruleSet);
        return toResult(command.tenantId(), saved);
    }

    static SplitRuleSetResult toResult(java.util.UUID tenantId, SplitRuleSet ruleSet) {
        List<SplitRuleSetResult.RuleLineView> views = ruleSet.rules().stream()
                .map(r -> new SplitRuleSetResult.RuleLineView(
                        r.targetAccountType().name(),
                        r.percentage().toPlainString()))
                .toList();
        return new SplitRuleSetResult(
                tenantId,
                ruleSet.key(),
                views,
                ruleSet.remainderTarget().name()
        );
    }
}
