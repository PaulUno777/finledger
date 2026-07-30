package com.pauluno.finledger.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pauluno.finledger.domain.DomainFixtures;
import com.pauluno.finledger.domain.service.BalanceCalculator;
import com.pauluno.finledger.domain.service.DoubleEntryValidator;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.IntRange;

class JournalEntryPropertyTest {

    private static final java.util.Currency USD = DomainFixtures.USD;

    @Property(tries = 50)
    void balanced_random_transfers_should_validate(
            @ForAll @BigRange(min = "0.01", max = "1000.00") BigDecimal amount
    ) {
        UUID tenantId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        LedgerAccount source = DomainFixtures.openAccount(from, tenantId, USD, true);
        LedgerAccount dest = DomainFixtures.openAccount(to, tenantId, USD, true);
        Map<UUID, LedgerAccount> accounts = Map.of(from, source, to, dest);

        Money credit = Money.of(amount, USD);
        List<Posting> postings = List.of(
                new Posting(from, credit.negated(), SettlementStatus.SETTLED),
                new Posting(to, credit, SettlementStatus.SETTLED)
        );

        DoubleEntryValidator.validate(postings, accounts, Map.of());
    }

    @Property(tries = 30)
    void reverse_should_sum_to_zero_and_link_original(
            @ForAll @BigRange(min = "0.01", max = "500.00") BigDecimal amount
    ) {
        UUID tenantId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        LedgerAccount source = DomainFixtures.openAccount(from, tenantId, USD, true);
        LedgerAccount dest = DomainFixtures.openAccount(to, tenantId, USD, true);
        Map<UUID, LedgerAccount> accounts = Map.of(from, source, to, dest);

        Money credit = Money.of(amount, USD);
        List<Posting> postings = List.of(
                new Posting(from, credit.negated(), SettlementStatus.SETTLED),
                new Posting(to, credit, SettlementStatus.SETTLED)
        );

        JournalEntry original = JournalEntry.create(
                tenantId,
                new IdempotencyKey("orig-" + UUID.randomUUID()),
                new TransactionReference("tx-" + UUID.randomUUID()),
                postings,
                accounts,
                Map.of(),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        Map<UUID, AccountBalance> afterOriginal = BalanceCalculator.applyPostings(
                accounts, Map.of(), original.postings());

        JournalEntry reversal = original.reverse(
                new IdempotencyKey("rev-" + UUID.randomUUID()),
                Instant.parse("2026-01-02T00:00:00Z"),
                accounts,
                afterOriginal
        );

        assertThat(reversal.type()).isEqualTo(JournalEntryType.REVERSAL);
        assertThat(reversal.reversesEntryId()).contains(original.id());
        assertThat(BalanceCalculator.sumByCurrency(reversal.postings(), USD).isZero()).isTrue();

        Money netFrom = original.postings().stream()
                .filter(p -> p.accountId().equals(from))
                .map(Posting::amount)
                .reduce(Money.zero(USD), Money::plus)
                .plus(reversal.postings().stream()
                        .filter(p -> p.accountId().equals(from))
                        .map(Posting::amount)
                        .reduce(Money.zero(USD), Money::plus));
        assertThat(netFrom.isZero()).isTrue();
    }

    @Property(tries = 20)
    void multi_leg_split_with_random_fee_should_validate(
            @ForAll @BigRange(min = "10.00", max = "1000.00") BigDecimal gross,
            @ForAll @IntRange(min = 1, max = 20) int feeCents
    ) {
        UUID tenantId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        UUID fee = UUID.randomUUID();

        LedgerAccount a = DomainFixtures.openAccount(payer, tenantId, USD, true);
        LedgerAccount b = DomainFixtures.openAccount(payee, tenantId, USD, true);
        LedgerAccount f = DomainFixtures.openAccount(fee, tenantId, USD, true);
        Map<UUID, LedgerAccount> accounts = Map.of(payer, a, payee, b, fee, f);

        Money grossMoney = Money.of(gross, USD);
        Money feeMoney = Money.of(BigDecimal.valueOf(feeCents, 2), USD);
        if (feeMoney.amount().compareTo(grossMoney.amount()) >= 0) {
            return;
        }
        Money net = grossMoney.minus(feeMoney);

        List<Posting> postings = List.of(
                new Posting(payer, grossMoney.negated(), SettlementStatus.SETTLED),
                new Posting(payee, net, SettlementStatus.SETTLED),
                new Posting(fee, feeMoney, SettlementStatus.SETTLED)
        );

        DoubleEntryValidator.validate(postings, accounts, Map.of());
        assertThat(BalanceCalculator.sumByCurrency(postings, USD).isZero()).isTrue();
    }
}
