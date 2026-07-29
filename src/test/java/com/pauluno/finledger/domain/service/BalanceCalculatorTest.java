package com.pauluno.finledger.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.domain.DomainFixtures;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;

@Tag("unit")
class BalanceCalculatorTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void should_split_available_and_pending_by_settlement_status() {
        UUID accountId = UUID.randomUUID();
        LedgerAccount account = DomainFixtures.openAccount(accountId, tenantId, DomainFixtures.USD, false);

        List<Posting> postings = List.of(
                new Posting(accountId, Money.of("10.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(accountId, Money.of("4.00", DomainFixtures.USD), SettlementStatus.PENDING),
                new Posting(accountId, Money.of("-2.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        AccountBalance balance = BalanceCalculator.forAccount(account, postings);

        assertEquals(new BigDecimal("8.00"), balance.available().amount());
        assertEquals(new BigDecimal("4.00"), balance.pending().amount());
        assertEquals(new BigDecimal("0.00"), balance.held().amount());
    }

    @Test
    void should_project_held_as_sum_on_suspense_hold_accounts() {
        UUID holdId = UUID.randomUUID();
        LedgerAccount hold = DomainFixtures.holdAccount(holdId, tenantId, DomainFixtures.USD);

        List<Posting> postings = List.of(
                new Posting(holdId, Money.of("7.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(holdId, Money.of("3.00", DomainFixtures.USD), SettlementStatus.PENDING)
        );

        AccountBalance balance = BalanceCalculator.forAccount(hold, postings);

        assertEquals(new BigDecimal("7.00"), balance.available().amount());
        assertEquals(new BigDecimal("3.00"), balance.pending().amount());
        assertEquals(new BigDecimal("10.00"), balance.held().amount());
    }
}
