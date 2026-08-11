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

import com.pauluno.finledger.application.dto.AccountBalanceResult;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;

@Tag("unit")
class GetAccountBalanceServiceTest {

    private static final java.util.Currency USD = java.util.Currency.getInstance("USD");

    private InMemoryLedgerAccountRepository accounts;
    private InMemoryAccountBalanceRepository balances;
    private GetAccountBalanceService service;

    private UUID tenantId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryLedgerAccountRepository();
        balances = new InMemoryAccountBalanceRepository();
        service = new GetAccountBalanceService(accounts, balances);

        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        accounts.save(new LedgerAccount(
                accountId, tenantId, "merchant", USD,
                AccountType.MERCHANT_WALLET, AccountStatus.OPEN, true));
        balances.save(new AccountBalance(
                accountId,
                USD,
                Money.of("10.00", USD),
                Money.of("3.00", USD),
                Money.zero(USD)
        ), tenantId);
    }

    @Test
    void should_return_available_pending_and_held() {
        AccountBalanceResult result = service.execute(tenantId, accountId);

        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.currencyCode()).isEqualTo("USD");
        assertThat(result.accountType()).isEqualTo("MERCHANT_WALLET");
        assertThat(result.available()).isEqualTo("10.00");
        assertThat(result.pending()).isEqualTo("3.00");
        assertThat(result.held()).isEqualTo("0.00");
    }

    @Test
    void should_reject_unknown_account() {
        assertThatThrownBy(() -> service.execute(tenantId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void should_reject_account_for_other_tenant() {
        assertThatThrownBy(() -> service.execute(UUID.randomUUID(), accountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class InMemoryLedgerAccountRepository implements LedgerAccountRepository {
        private final Map<UUID, LedgerAccount> store = new ConcurrentHashMap<>();

        @Override
        public LedgerAccount save(LedgerAccount account) {
            store.put(account.id(), account);
            return account;
        }

        @Override
        public Optional<LedgerAccount> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<LedgerAccount> findByIdForTenant(UUID id, UUID tenantId) {
            return findById(id).filter(a -> a.tenantId().equals(tenantId));
        }

        @Override
        public List<LedgerAccount> listByTenant(UUID tenantId) {
            return store.values().stream().filter(a -> a.tenantId().equals(tenantId)).toList();
        }
    }

    private static final class InMemoryAccountBalanceRepository implements AccountBalanceRepository {
        private final Map<UUID, AccountBalance> store = new ConcurrentHashMap<>();

        @Override
        public AccountBalance save(AccountBalance balance, UUID tenantId) {
            store.put(balance.accountId(), balance);
            return balance;
        }

        @Override
        public Optional<AccountBalance> findByAccountId(UUID accountId) {
            return Optional.ofNullable(store.get(accountId));
        }

        @Override
        public Map<UUID, AccountBalance> findByAccountIds(Collection<UUID> accountIds) {
            Map<UUID, AccountBalance> result = new HashMap<>();
            for (UUID id : accountIds) {
                AccountBalance balance = store.get(id);
                if (balance != null) {
                    result.put(id, balance);
                }
            }
            return result;
        }
    }
}
