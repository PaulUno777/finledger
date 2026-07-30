package com.pauluno.finledger.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.SplitPlan;
import com.pauluno.finledger.domain.model.SplitRule;
import com.pauluno.finledger.domain.model.SplitRuleSet;

@Tag("unit")
class SplitPlanEvaluatorTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void should_allocate_exact_remainder_to_remainder_target() {
        UUID merchant = UUID.randomUUID();
        UUID fee = UUID.randomUUID();
        UUID tax = UUID.randomUUID();

        SplitRuleSet rules = new SplitRuleSet(
                "default",
                List.of(
                        new SplitRule(AccountType.MERCHANT_WALLET, new BigDecimal("95")),
                        new SplitRule(AccountType.FEE_PLATFORM_REVENUE, new BigDecimal("3")),
                        new SplitRule(AccountType.TAX_VAT, new BigDecimal("2"))
                ),
                AccountType.MERCHANT_WALLET
        );

        Map<AccountType, UUID> accounts = new EnumMap<>(AccountType.class);
        accounts.put(AccountType.MERCHANT_WALLET, merchant);
        accounts.put(AccountType.FEE_PLATFORM_REVENUE, fee);
        accounts.put(AccountType.TAX_VAT, tax);

        Money total = Money.of("100.00", USD);
        SplitPlan plan = SplitPlanEvaluator.evaluate(rules, total, accounts);

        Money credits = Money.zero(USD);
        for (SplitPlan.SplitLeg leg : plan.legs()) {
            credits = credits.plus(leg.amount());
        }
        assertThat(credits.amount()).isEqualByComparingTo("100.00");
        assertThat(plan.legs()).hasSize(3);
    }

    @Test
    void should_push_rounding_remainder_cents_to_remainder_target() {
        UUID merchant = UUID.randomUUID();
        UUID fee = UUID.randomUUID();

        // 33.33% * 1.00 = 0.33 (HALF_EVEN); two rules leave remainder
        SplitRuleSet rules = new SplitRuleSet(
                "odd",
                List.of(
                        new SplitRule(AccountType.FEE_PLATFORM_REVENUE, new BigDecimal("33.33")),
                        new SplitRule(AccountType.FEE_AGGREGATOR_MARKUP, new BigDecimal("33.33"))
                ),
                AccountType.MERCHANT_WALLET
        );

        Map<AccountType, UUID> accounts = new EnumMap<>(AccountType.class);
        accounts.put(AccountType.FEE_PLATFORM_REVENUE, fee);
        accounts.put(AccountType.FEE_AGGREGATOR_MARKUP, UUID.randomUUID());
        accounts.put(AccountType.MERCHANT_WALLET, merchant);

        SplitPlan plan = SplitPlanEvaluator.evaluate(rules, Money.of("1.00", USD), accounts);

        Money merchantCredit = plan.legs().stream()
                .filter(l -> l.accountId().equals(merchant))
                .map(SplitPlan.SplitLeg::amount)
                .reduce(Money.zero(USD), Money::plus);
        assertThat(merchantCredit.amount()).isEqualByComparingTo("0.34");

        Money sum = plan.legs().stream()
                .map(SplitPlan.SplitLeg::amount)
                .reduce(Money.zero(USD), Money::plus);
        assertThat(sum.amount()).isEqualByComparingTo("1.00");
    }

    @Test
    void should_reject_rule_set_when_percentages_sum_above_100() {
        assertThatThrownBy(() -> new SplitRuleSet(
                "bad",
                List.of(
                        new SplitRule(AccountType.MERCHANT_WALLET, new BigDecimal("60")),
                        new SplitRule(AccountType.FEE_PLATFORM_REVENUE, new BigDecimal("50"))
                ),
                AccountType.MERCHANT_WALLET
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<= 100");
    }

    @Test
    void should_reject_missing_account_for_target_type() {
        SplitRuleSet rules = new SplitRuleSet(
                "default",
                List.of(new SplitRule(AccountType.MERCHANT_WALLET, new BigDecimal("100"))),
                AccountType.MERCHANT_WALLET
        );
        assertThatThrownBy(() -> SplitPlanEvaluator.evaluate(
                rules, Money.of("10.00", USD), Map.of()))
                .isInstanceOf(InvalidJournalEntryException.class)
                .hasMessageContaining("Missing account");
    }
}
