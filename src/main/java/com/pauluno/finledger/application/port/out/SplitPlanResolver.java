package com.pauluno.finledger.application.port.out;

import java.util.Map;
import java.util.UUID;

import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.SplitPlan;
import com.pauluno.finledger.domain.model.SplitRuleSet;

public interface SplitPlanResolver {

    SplitPlan resolve(SplitRuleSet rules, Money totalAmount, SplitContext context);

    record SplitContext(UUID sourceAccountId, Map<AccountType, UUID> accountsByType) {
    }
}
