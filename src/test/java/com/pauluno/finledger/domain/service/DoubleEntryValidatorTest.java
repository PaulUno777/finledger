package com.pauluno.finledger.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.domain.DomainFixtures;
import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;

@Tag("unit")
class DoubleEntryValidatorTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();
    private final UUID accountEur = UUID.randomUUID();
    private final UUID accountEurB = UUID.randomUUID();

    @Test
    void should_accept_balanced_two_legged_entry() {
        LedgerAccount a = DomainFixtures.openAccount(accountA, tenantId, DomainFixtures.USD, true);
        LedgerAccount b = DomainFixtures.openAccount(accountB, tenantId, DomainFixtures.USD, true);
        Map<UUID, LedgerAccount> accounts = Map.of(accountA, a, accountB, b);

        List<Posting> postings = List.of(
                new Posting(accountA, Money.of("-10.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountB, Money.of("10.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        assertDoesNotThrow(() -> DoubleEntryValidator.validate(postings, accounts, Map.of()));
    }

    @Test
    void should_reject_when_postings_do_not_sum_to_zero() {
        LedgerAccount a = DomainFixtures.openAccount(accountA, tenantId, DomainFixtures.USD, true);
        LedgerAccount b = DomainFixtures.openAccount(accountB, tenantId, DomainFixtures.USD, true);
        Map<UUID, LedgerAccount> accounts = Map.of(accountA, a, accountB, b);

        List<Posting> postings = List.of(
                new Posting(accountA, Money.of("-10.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountB, Money.of("9.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        assertThrows(
                InvalidJournalEntryException.class,
                () -> DoubleEntryValidator.validate(postings, accounts, Map.of())
        );
    }

    @Test
    void should_accept_multi_posting_split_that_sums_to_zero() {
        UUID fee = UUID.randomUUID();
        LedgerAccount a = DomainFixtures.openAccount(accountA, tenantId, DomainFixtures.USD, true);
        LedgerAccount b = DomainFixtures.openAccount(accountB, tenantId, DomainFixtures.USD, true);
        LedgerAccount f = DomainFixtures.openAccount(fee, tenantId, DomainFixtures.USD, true);
        Map<UUID, LedgerAccount> accounts = Map.of(accountA, a, accountB, b, fee, f);

        List<Posting> postings = List.of(
                new Posting(accountA, Money.of("-100.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountB, Money.of("97.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(fee, Money.of("3.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        assertDoesNotThrow(() -> DoubleEntryValidator.validate(postings, accounts, Map.of()));
    }

    @Test
    void should_require_each_currency_bucket_to_sum_to_zero() {
        LedgerAccount usdA = DomainFixtures.openAccount(accountA, tenantId, DomainFixtures.USD, true);
        LedgerAccount usdB = DomainFixtures.openAccount(accountB, tenantId, DomainFixtures.USD, true);
        LedgerAccount eurA = DomainFixtures.openAccount(accountEur, tenantId, DomainFixtures.EUR, true);
        LedgerAccount eurB = DomainFixtures.openAccount(accountEurB, tenantId, DomainFixtures.EUR, true);
        Map<UUID, LedgerAccount> accounts = Map.of(
                accountA, usdA, accountB, usdB, accountEur, eurA, accountEurB, eurB);

        List<Posting> balanced = List.of(
                new Posting(accountA, Money.of("-5.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountB, Money.of("5.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountEur, Money.of("-2.00", DomainFixtures.EUR), SettlementStatus.SETTLED),
                new Posting(accountEurB, Money.of("2.00", DomainFixtures.EUR), SettlementStatus.SETTLED)
        );
        assertDoesNotThrow(() -> DoubleEntryValidator.validate(balanced, accounts, Map.of()));

        List<Posting> unbalancedEur = List.of(
                new Posting(accountA, Money.of("-5.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountB, Money.of("5.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountEur, Money.of("-2.00", DomainFixtures.EUR), SettlementStatus.SETTLED),
                new Posting(accountEurB, Money.of("1.00", DomainFixtures.EUR), SettlementStatus.SETTLED)
        );
        assertThrows(
                InvalidJournalEntryException.class,
                () -> DoubleEntryValidator.validate(unbalancedEur, accounts, Map.of())
        );
    }

    @Test
    void should_reject_single_posting() {
        LedgerAccount a = DomainFixtures.openAccount(accountA, tenantId, DomainFixtures.USD, true);
        assertThrows(
                InvalidJournalEntryException.class,
                () -> DoubleEntryValidator.validate(
                        List.of(new Posting(accountA, Money.of("1.00", DomainFixtures.USD), SettlementStatus.SETTLED)),
                        Map.of(accountA, a),
                        Map.of()
                )
        );
    }
}
