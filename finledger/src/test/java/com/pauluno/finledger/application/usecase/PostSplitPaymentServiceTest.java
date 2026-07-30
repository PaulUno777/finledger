package com.pauluno.finledger.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

import com.pauluno.finledger.application.dto.PostSplitPaymentCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.exception.IdempotencyConflictException;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.IdempotencyStore;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.OutboxWriter;
import com.pauluno.finledger.application.port.out.SplitPlanResolver;
import com.pauluno.finledger.application.port.out.SplitRuleSetRepository;
import com.pauluno.finledger.application.split.DeclarativeSplitPlanResolver;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.SplitRule;
import com.pauluno.finledger.domain.model.SplitRuleSet;
import com.pauluno.finledger.domain.service.BalanceCalculator;

@Tag("unit")
class PostSplitPaymentServiceTest {

    private static final java.util.Currency USD = java.util.Currency.getInstance("USD");

    private InMemoryIdempotencyStore idempotencyStore;
    private InMemoryLedgerAccountRepository accountRepository;
    private InMemoryAccountBalanceRepository balanceRepository;
    private InMemoryJournalEntryRepository journalEntryRepository;
    private InMemoryOutboxWriter outboxWriter;
    private InMemorySplitRuleSetRepository splitRuleSetRepository;
    private PostSplitPaymentService service;

    private UUID tenantId;
    private UUID sourceId;
    private UUID merchantId;
    private UUID feeId;

    @BeforeEach
    void setUp() {
        idempotencyStore = new InMemoryIdempotencyStore();
        accountRepository = new InMemoryLedgerAccountRepository();
        balanceRepository = new InMemoryAccountBalanceRepository();
        journalEntryRepository = new InMemoryJournalEntryRepository(balanceRepository, accountRepository);
        outboxWriter = new InMemoryOutboxWriter();
        splitRuleSetRepository = new InMemorySplitRuleSetRepository();
        SplitPlanResolver resolver = new DeclarativeSplitPlanResolver();
        RiskGateService riskGate = new RiskGateService(
                request -> com.pauluno.finledger.application.port.out.TransactionRiskCheckPort.RiskDecision.allow(),
                new EmptyFraudConfigRepository(),
                new AllowAllRiskDecisionRepository()
        );
        service = new PostSplitPaymentService(
                idempotencyStore,
                accountRepository,
                balanceRepository,
                journalEntryRepository,
                outboxWriter,
                splitRuleSetRepository,
                resolver,
                riskGate
        );

        tenantId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        feeId = UUID.randomUUID();

        accountRepository.save(new LedgerAccount(
                sourceId, tenantId, "rail", USD,
                AccountType.RAIL_CLEARING, AccountStatus.OPEN, true));
        accountRepository.save(new LedgerAccount(
                merchantId, tenantId, "merchant", USD,
                AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true));
        accountRepository.save(new LedgerAccount(
                feeId, tenantId, "fee", USD,
                AccountType.FEE_PLATFORM_REVENUE, AccountStatus.OPEN, true));
        balanceRepository.seedZero(sourceId, USD);
        balanceRepository.seedZero(merchantId, USD);
        balanceRepository.seedZero(feeId, USD);

        splitRuleSetRepository.save(tenantId, new SplitRuleSet(
                "default",
                List.of(
                        new SplitRule(AccountType.MERCHANT_WALLET, new BigDecimal("95")),
                        new SplitRule(AccountType.FEE_PLATFORM_REVENUE, new BigDecimal("5"))
                ),
                AccountType.MERCHANT_WALLET
        ));
    }

    @Test
    void should_post_split_and_replay_same_key() {
        PostSplitPaymentCommand command = splitCommand("key-1", "tx-split-1");

        PostTransactionResult first = service.execute(command);
        assertThat(first.replayed()).isFalse();
        assertThat(first.postings()).hasSize(3);
        assertThat(balanceRepository.findByAccountId(merchantId))
                .get()
                .extracting(b -> b.available().amount().toPlainString())
                .isEqualTo("95.00");
        assertThat(outboxWriter.messages).hasSize(1);

        PostTransactionResult replay = service.execute(command);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.journalEntryId()).isEqualTo(first.journalEntryId());
        assertThat(journalEntryRepository.entries).hasSize(1);
        assertThat(outboxWriter.messages).hasSize(1);
    }

    private PostSplitPaymentCommand splitCommand(String key, String reference) {
        return new PostSplitPaymentCommand(
                tenantId,
                key,
                reference,
                "100.00",
                "USD",
                sourceId,
                Map.of(
                        "MERCHANT_WALLET", merchantId,
                        "FEE_PLATFORM_REVENUE", feeId
                ),
                "default"
        );
    }

    private static final class InMemorySplitRuleSetRepository implements SplitRuleSetRepository {
        private final Map<String, SplitRuleSet> store = new ConcurrentHashMap<>();

        @Override
        public SplitRuleSet save(UUID tenantId, SplitRuleSet ruleSet) {
            store.put(tenantId + ":" + ruleSet.key(), ruleSet);
            return ruleSet;
        }

        @Override
        public Optional<SplitRuleSet> findByTenantAndKey(UUID tenantId, String ruleSetKey) {
            return Optional.ofNullable(store.get(tenantId + ":" + ruleSetKey));
        }
    }

    private static final class InMemoryOutboxWriter implements OutboxWriter {
        private final List<OutboxMessage> messages = new java.util.ArrayList<>();

        @Override
        public void append(OutboxMessage message) {
            messages.add(message);
        }
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

    private static final class EmptyFraudConfigRepository
            implements com.pauluno.finledger.application.port.out.TenantFraudConfigRepository {
        @Override
        public com.pauluno.finledger.application.fraud.TenantFraudConfig save(
                com.pauluno.finledger.application.fraud.TenantFraudConfig config) {
            return config;
        }

        @Override
        public Optional<com.pauluno.finledger.application.fraud.TenantFraudConfig> findByTenantId(UUID tenantId) {
            return Optional.empty();
        }
    }

    private static final class AllowAllRiskDecisionRepository
            implements com.pauluno.finledger.application.port.out.RiskDecisionRepository {
        @Override
        public RiskDecisionRecord save(RiskDecisionRecord record) {
            return record;
        }

        @Override
        public long countSyncSince(UUID tenantId, java.time.Instant since) {
            return 0;
        }

        @Override
        public Optional<RiskDecisionRecord> findAsyncHoldForSource(UUID tenantId, UUID sourceJournalEntryId) {
            return Optional.empty();
        }

        @Override
        public List<RiskDecisionRecord> findByTransactionReference(UUID tenantId, String transactionReference) {
            return List.of();
        }
    }
}
