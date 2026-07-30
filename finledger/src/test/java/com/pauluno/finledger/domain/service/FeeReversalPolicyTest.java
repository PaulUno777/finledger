package com.pauluno.finledger.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.FeeReversalPolicy;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;
import com.pauluno.finledger.domain.model.TransactionReference;

@Tag("unit")
class FeeReversalPolicyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void no_reverse_should_leave_fee_legs_untouched() {
        UUID tenantId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        UUID fee = UUID.randomUUID();

        Map<UUID, LedgerAccount> accounts = accounts(tenantId, source, merchant, fee);
        JournalEntry original = splitEntry(tenantId, source, merchant, fee, accounts);

        FeeReversalPolicy policy = new NoReverseFeePolicy();
        List<Posting> refund = policy.calculateReversal(original, Money.of("95.00", USD), accounts);

        Money feeDebit = refund.stream()
                .filter(p -> p.accountId().equals(fee))
                .map(Posting::amount)
                .reduce(Money.zero(USD), Money::plus);
        assertThat(feeDebit.isZero()).isTrue();

        Money sourceCredit = refund.stream()
                .filter(p -> p.accountId().equals(source))
                .map(Posting::amount)
                .reduce(Money.zero(USD), Money::plus);
        assertThat(sourceCredit.amount()).isEqualByComparingTo("95.00");

        Money merchantDebit = refund.stream()
                .filter(p -> p.accountId().equals(merchant))
                .map(Posting::amount)
                .reduce(Money.zero(USD), Money::plus);
        assertThat(merchantDebit.amount()).isEqualByComparingTo("-95.00");
    }

    @Test
    void pro_rata_should_scale_fee_legs() {
        UUID tenantId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        UUID fee = UUID.randomUUID();

        Map<UUID, LedgerAccount> accounts = accounts(tenantId, source, merchant, fee);
        JournalEntry original = splitEntry(tenantId, source, merchant, fee, accounts);

        FeeReversalPolicy policy = new ProRataFeePolicy();
        // Half of 100 → fees and principal both halved
        List<Posting> refund = policy.calculateReversal(original, Money.of("50.00", USD), accounts);

        Money feeDebit = refund.stream()
                .filter(p -> p.accountId().equals(fee))
                .map(Posting::amount)
                .reduce(Money.zero(USD), Money::plus);
        assertThat(feeDebit.amount()).isEqualByComparingTo("-2.50");

        Money sum = refund.stream()
                .map(Posting::amount)
                .reduce(Money.zero(USD), Money::plus);
        assertThat(sum.isZero()).isTrue();
    }

    private static Map<UUID, LedgerAccount> accounts(
            UUID tenantId, UUID source, UUID merchant, UUID fee) {
        Map<UUID, LedgerAccount> map = new HashMap<>();
        map.put(source, new LedgerAccount(
                source, tenantId, "rail", USD,
                AccountType.RAIL_CLEARING, AccountStatus.OPEN, true));
        map.put(merchant, new LedgerAccount(
                merchant, tenantId, "merchant", USD,
                AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true));
        map.put(fee, new LedgerAccount(
                fee, tenantId, "fee", USD,
                AccountType.FEE_PLATFORM_REVENUE, AccountStatus.OPEN, true));
        return map;
    }

    private static JournalEntry splitEntry(
            UUID tenantId,
            UUID source,
            UUID merchant,
            UUID fee,
            Map<UUID, LedgerAccount> accounts
    ) {
        List<Posting> postings = List.of(
                new Posting(source, Money.of("-100.00", USD), SettlementStatus.SETTLED),
                new Posting(merchant, Money.of("95.00", USD), SettlementStatus.SETTLED),
                new Posting(fee, Money.of("5.00", USD), SettlementStatus.SETTLED)
        );
        Map<UUID, AccountBalance> balances = new HashMap<>();
        balances.put(source, AccountBalance.zero(source, USD));
        balances.put(merchant, AccountBalance.zero(merchant, USD));
        balances.put(fee, AccountBalance.zero(fee, USD));
        // Fund source so debit does not fail overdraft check incorrectly — source allows overdraft.
        return JournalEntry.create(
                tenantId,
                new IdempotencyKey("orig-" + UUID.randomUUID()),
                new TransactionReference("split-orig"),
                postings,
                accounts,
                balances,
                Instant.now(),
                null
        );
    }
}
