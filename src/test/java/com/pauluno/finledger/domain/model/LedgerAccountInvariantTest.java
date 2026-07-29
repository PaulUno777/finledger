package com.pauluno.finledger.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.domain.DomainFixtures;
import com.pauluno.finledger.domain.exception.AccountClosedException;
import com.pauluno.finledger.domain.exception.InsufficientFundsException;
import com.pauluno.finledger.domain.service.DoubleEntryValidator;

@Tag("unit")
class LedgerAccountInvariantTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void should_reject_postings_to_closed_account() {
        UUID openId = UUID.randomUUID();
        UUID closedId = UUID.randomUUID();
        LedgerAccount open = DomainFixtures.openAccount(openId, tenantId, DomainFixtures.USD, true);
        LedgerAccount closed = DomainFixtures.closedAccount(closedId, tenantId, DomainFixtures.USD);

        List<Posting> postings = List.of(
                new Posting(openId, Money.of("-10.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(closedId, Money.of("10.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        assertThrows(
                AccountClosedException.class,
                () -> DoubleEntryValidator.validate(
                        postings,
                        Map.of(openId, open, closedId, closed),
                        Map.of()
                )
        );
    }

    @Test
    void should_reject_negative_available_when_overdraft_disallowed() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        LedgerAccount source = DomainFixtures.openAccount(from, tenantId, DomainFixtures.USD, false);
        LedgerAccount dest = DomainFixtures.openAccount(to, tenantId, DomainFixtures.USD, true);

        Map<UUID, AccountBalance> balances = DomainFixtures.funded(source, "5.00");

        List<Posting> postings = List.of(
                new Posting(from, Money.of("-10.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(to, Money.of("10.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        assertThrows(
                InsufficientFundsException.class,
                () -> DoubleEntryValidator.validate(
                        postings,
                        Map.of(from, source, to, dest),
                        balances
                )
        );
    }

    @Test
    void should_allow_negative_available_when_overdraft_enabled() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        LedgerAccount source = DomainFixtures.openAccount(from, tenantId, DomainFixtures.USD, true);
        LedgerAccount dest = DomainFixtures.openAccount(to, tenantId, DomainFixtures.USD, true);

        List<Posting> postings = List.of(
                new Posting(from, Money.of("-10.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(to, Money.of("10.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        DoubleEntryValidator.validate(postings, Map.of(from, source, to, dest), Map.of());

        JournalEntry entry = JournalEntry.create(
                tenantId,
                new IdempotencyKey("key-1"),
                new TransactionReference("tx-1"),
                postings,
                Map.of(from, source, to, dest),
                Map.of(),
                Instant.parse("2026-07-29T12:00:00Z")
        );
        assertEquals(JournalEntryType.POSTING, entry.type());
        assertTrue(entry.reversesEntryId().isEmpty());
    }

    @Test
    void should_accept_debit_within_funded_available_balance() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        LedgerAccount source = DomainFixtures.openAccount(from, tenantId, DomainFixtures.USD, false);
        LedgerAccount dest = DomainFixtures.openAccount(to, tenantId, DomainFixtures.USD, false);

        Map<UUID, AccountBalance> balances = DomainFixtures.funded(source, "50.00");
        List<Posting> postings = List.of(
                new Posting(from, Money.of("-10.00", DomainFixtures.USD), SettlementStatus.SETTLED),
                new Posting(to, Money.of("10.00", DomainFixtures.USD), SettlementStatus.SETTLED)
        );

        DoubleEntryValidator.validate(postings, Map.of(from, source, to, dest), balances);
        assertEquals(new BigDecimal("50.00"), balances.get(from).available().amount());
    }
}
