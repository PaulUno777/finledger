package com.pauluno.finledger.application.split;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.pauluno.finledger.application.port.out.SplitPlanResolver;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.SplitPlan;
import com.pauluno.finledger.domain.model.SplitRuleSet;
import com.pauluno.finledger.domain.service.SplitPlanEvaluator;

/**
 * In-box resolver: applies stored declarative percentages only (plan §5.1 / §5.2).
 */
@Component
public class DeclarativeSplitPlanResolver implements SplitPlanResolver {

    @Override
    public SplitPlan resolve(SplitRuleSet rules, Money totalAmount, SplitContext context) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(context.accountsByType(), "accountsByType");
        return SplitPlanEvaluator.evaluate(rules, totalAmount, context.accountsByType());
    }
}
