package com.pauluno.finledger.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.dto.PostTransactionCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.exception.IdempotencyConflictException;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.IdempotencyStore;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.service.BalanceCalculator;

@Tag("unit")
class PostTransactionServiceTest {

    private static final java.util.Currency USD = java.util.Currency.getInstance("USD");

    private InMemoryIdempotencyStore idempotencyStore;
    private InMemoryLedgerAccountRepository accountRepository;
    private InMemoryAccountBalanceRepository balanceRepository;
    private InMemoryJournalEntryRepository journalEntryRepository;
    private PostTransactionService service;

    private UUID tenantId;
    private UUID fromId;
    private UUID toId;

    @BeforeEach
    void setUp() {
        idempotencyStore = new InMemoryIdempotencyStore();
        accountRepository = new InMemoryLedgerAccountRepository();
        balanceRepository = new InMemoryAccountBalanceRepository();
        journalEntryRepository = new InMemoryJournalEntryRepository(balanceRepository, accountRepository);
        service = new PostTransactionService(
                idempotencyStore,
                accountRepository,
                balanceRepository,
                journalEntryRepository
        );

        tenantId = UUID.randomUUID();
        fromId = UUID.randomUUID();
        toId = UUID.randomUUID();

        accountRepository.save(new LedgerAccount(
                fromId, tenantId, "from", USD,
                AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true));
        accountRepository.save(new LedgerAccount(
                toId, tenantId, "to", USD,
                AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true));
        balanceRepository.seedZero(fromId, USD);
        balanceRepository.seedZero(toId, USD);
    }

    @Test
    void should_post_transfer_and_replay_same_key_and_hash() {
        PostTransactionCommand command = transferCommand("key-1", "tx-1", "-10.00", "10.00");

        PostTransactionResult first = service.execute(command);
        assertThat(first.replayed()).isFalse();
        assertThat(first.journalEntryId()).isNotNull();

        PostTransactionResult replay = service.execute(command);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.journalEntryId()).isEqualTo(first.journalEntryId());
        assertThat(journalEntryRepository.entries).hasSize(1);
    }

    @Test
    void should_reject_same_key_with_different_body() {
        PostTransactionCommand first = transferCommand("key-2", "tx-2", "-10.00", "10.00");
        service.execute(first);

        PostTransactionCommand conflict = transferCommand("key-2", "tx-2", "-20.00", "20.00");
        assertThatThrownBy(() -> service.execute(conflict))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different request body");
    }

    @Test
    void should_post_happy_path() {
        PostTransactionResult result = service.execute(
                transferCommand("key-3", "tx-3", "-5.50", "5.50"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.type()).isEqualTo("POSTING");
        assertThat(result.postings()).hasSize(2);
        assertThat(balanceRepository.findByAccountId(toId))
                .get()
                .extracting(b -> b.available().amount().toPlainString())
                .isEqualTo("5.50");
    }

    private PostTransactionCommand transferCommand(
            String key, String reference, String debit, String credit) {
        return new PostTransactionCommand(
                tenantId,
                key,
                reference,
                List.of(
                        new PostTransactionCommand.PostingLine(
                                fromId, debit, "USD", "SETTLED"),
                        new PostTransactionCommand.PostingLine(
                                toId, credit, "USD", "SETTLED")
                )
        );
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore {
        private final Map<String, StoredIdempotency> store = new ConcurrentHashMap<>();

        private static String id(UUID tenantId, IdempotencyKey key) {
            return tenantId + ":" + key.value();
        }

        @Override
        public BeginOutcome tryBegin(UUID tenantId, IdempotencyKey key, String requestHash) {
            String mapKey = id(tenantId, key);
            StoredIdempotency existing = store.get(mapKey);
            if (existing == null) {
                store.put(mapKey, new StoredIdempotency(requestHash, null, IdempotencyStatus.STARTED));
                return new BeginOutcome.Proceed();
            }
            if (!existing.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was reused with a different request body");
            }
            return switch (existing.status()) {
                case COMPLETED -> new BeginOutcome.Replay(existing.responseSnapshot());
                case STARTED, FAILED -> throw new IdempotencyConflictException(
                        "IDEMPOTENCY_IN_PROGRESS",
                        "Idempotency-Key is already in progress or previously failed; retry with a new key or wait");
            };
        }

        @Override
        public void complete(UUID tenantId, IdempotencyKey key, String responseSnapshot) {
            store.put(id(tenantId, key), new StoredIdempotency(
                    store.get(id(tenantId, key)).requestHash(),
                    responseSnapshot,
                    IdempotencyStatus.COMPLETED));
        }

        @Override
        public void fail(UUID tenantId, IdempotencyKey key) {
            StoredIdempotency existing = store.get(id(tenantId, key));
            if (existing != null) {
                store.put(id(tenantId, key), new StoredIdempotency(
                        existing.requestHash(), existing.responseSnapshot(), IdempotencyStatus.FAILED));
            }
        }

        @Override
        public Optional<StoredIdempotency> find(UUID tenantId, IdempotencyKey key) {
            return Optional.ofNullable(store.get(id(tenantId, key)));
        }
    }

    private static final class InMemoryLedgerAccountRepository implements LedgerAccountRepository {
        private final Map<UUID, LedgerAccount> accounts = new ConcurrentHashMap<>();

        @Override
        public LedgerAccount save(LedgerAccount account) {
            accounts.put(account.id(), account);
            return account;
        }

        @Override
        public Optional<LedgerAccount> findById(UUID id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public Optional<LedgerAccount> findByIdForTenant(UUID id, UUID tenantId) {
            return findById(id).filter(a -> a.tenantId().equals(tenantId));
        }
    }

    private static final class InMemoryAccountBalanceRepository implements AccountBalanceRepository {
        private final Map<UUID, AccountBalance> balances = new ConcurrentHashMap<>();

        void seedZero(UUID accountId, java.util.Currency currency) {
            balances.put(accountId, AccountBalance.zero(accountId, currency));
        }

        @Override
        public AccountBalance save(AccountBalance balance, UUID tenantId) {
            balances.put(balance.accountId(), balance);
            return balance;
        }

        @Override
        public Optional<AccountBalance> findByAccountId(UUID accountId) {
            return Optional.ofNullable(balances.get(accountId));
        }

        @Override
        public Map<UUID, AccountBalance> findByAccountIds(Collection<UUID> accountIds) {
            Map<UUID, AccountBalance> result = new HashMap<>();
            for (UUID id : accountIds) {
                AccountBalance balance = balances.get(id);
                if (balance != null) {
                    result.put(id, balance);
                }
            }
            return result;
        }
    }

    private static final class InMemoryJournalEntryRepository implements JournalEntryRepository {
        private final List<JournalEntry> entries = new java.util.ArrayList<>();
        private final InMemoryAccountBalanceRepository balances;
        private final InMemoryLedgerAccountRepository accounts;

        InMemoryJournalEntryRepository(
                InMemoryAccountBalanceRepository balances,
                InMemoryLedgerAccountRepository accounts) {
            this.balances = balances;
            this.accounts = accounts;
        }

        @Override
        public JournalEntry save(JournalEntry entry) {
            Map<UUID, LedgerAccount> accountMap = new HashMap<>();
            Map<UUID, AccountBalance> before = new HashMap<>();
            for (var posting : entry.postings()) {
                accountMap.put(posting.accountId(), accounts.findById(posting.accountId()).orElseThrow());
                before.put(posting.accountId(),
                        balances.findByAccountId(posting.accountId()).orElseThrow());
            }
            Map<UUID, AccountBalance> after = BalanceCalculator.applyPostings(
                    accountMap, before, entry.postings());
            after.forEach((id, balance) -> balances.save(balance, entry.tenantId()));
            entries.add(entry);
            return entry;
        }

        @Override
        public Optional<JournalEntry> findById(UUID id) {
            return entries.stream().filter(e -> e.id().equals(id)).findFirst();
        }

        @Override
        public Optional<JournalEntry> findByTenantAndIdempotencyKey(UUID tenantId, IdempotencyKey key) {
            return entries.stream()
                    .filter(e -> e.tenantId().equals(tenantId) && e.idempotencyKey().equals(key))
                    .findFirst();
        }
    }
}
