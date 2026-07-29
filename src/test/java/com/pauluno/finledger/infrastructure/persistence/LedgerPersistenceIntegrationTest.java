package com.pauluno.finledger.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;
import com.pauluno.finledger.domain.model.TransactionReference;

@SpringBootTest
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class LedgerPersistenceIntegrationTest {

    private static final java.util.Currency USD = java.util.Currency.getInstance("USD");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("finledger")
            .withUsername("finledger")
            .withPassword("finledger");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration");
    }

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    @Test
    void should_persist_accounts_journal_and_update_balances() {
        UUID tenantId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        LedgerAccount from = new LedgerAccount(
                fromId, tenantId, "merchant-a", USD,
                AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true);
        LedgerAccount to = new LedgerAccount(
                toId, tenantId, "merchant-b", USD,
                AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true);

        ledgerAccountRepository.save(from);
        ledgerAccountRepository.save(to);

        assertThat(accountBalanceRepository.findByAccountId(fromId))
                .isPresent()
                .get()
                .extracting(AccountBalance::available)
                .extracting(Money::amount)
                .isEqualTo(Money.zero(USD).amount());

        Map<UUID, LedgerAccount> accounts = Map.of(fromId, from, toId, to);
        Map<UUID, AccountBalance> balancesBefore = accountBalanceRepository.findByAccountIds(
                List.of(fromId, toId));

        List<Posting> postings = List.of(
                new Posting(fromId, Money.of("-25.00", USD), SettlementStatus.SETTLED),
                new Posting(toId, Money.of("25.00", USD), SettlementStatus.SETTLED)
        );

        JournalEntry entry = JournalEntry.create(
                tenantId,
                new IdempotencyKey("idem-1-" + UUID.randomUUID()),
                new TransactionReference("tx-1"),
                postings,
                accounts,
                balancesBefore,
                Instant.parse("2026-07-29T20:00:00Z")
        );

        JournalEntry saved = journalEntryRepository.save(entry);
        JournalEntry loaded = journalEntryRepository.findById(saved.id()).orElseThrow();

        assertThat(loaded.postings()).hasSize(2);
        assertThat(loaded.idempotencyKey()).isEqualTo(saved.idempotencyKey());

        AccountBalance fromBalance = accountBalanceRepository.findByAccountId(fromId).orElseThrow();
        AccountBalance toBalance = accountBalanceRepository.findByAccountId(toId).orElseThrow();

        assertThat(fromBalance.available().amount()).isEqualByComparingTo("-25.00");
        assertThat(toBalance.available().amount()).isEqualByComparingTo("25.00");
    }

    @Test
    void should_reject_duplicate_idempotency_key_for_tenant() {
        UUID tenantId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        LedgerAccount from = new LedgerAccount(
                fromId, tenantId, "a", USD, AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true);
        LedgerAccount to = new LedgerAccount(
                toId, tenantId, "b", USD, AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true);
        ledgerAccountRepository.save(from);
        ledgerAccountRepository.save(to);

        IdempotencyKey key = new IdempotencyKey("same-key-" + tenantId);
        Map<UUID, LedgerAccount> accounts = Map.of(fromId, from, toId, to);
        List<Posting> postings = List.of(
                new Posting(fromId, Money.of("-1.00", USD), SettlementStatus.SETTLED),
                new Posting(toId, Money.of("1.00", USD), SettlementStatus.SETTLED)
        );

        JournalEntry first = JournalEntry.create(
                tenantId, key, new TransactionReference("tx-a"),
                postings, accounts, Map.of(), Instant.parse("2026-07-29T21:00:00Z"));
        journalEntryRepository.save(first);

        JournalEntry second = JournalEntry.reconstitute(
                UUID.randomUUID(),
                tenantId,
                key,
                new TransactionReference("tx-b"),
                first.type(),
                postings,
                Instant.parse("2026-07-29T21:01:00Z"),
                null
        );

        assertThatThrownBy(() -> journalEntryRepository.save(second))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(journalEntryRepository.findByTenantAndIdempotencyKey(tenantId, key))
                .isPresent()
                .get()
                .extracting(JournalEntry::id)
                .isEqualTo(first.id());
    }
}
