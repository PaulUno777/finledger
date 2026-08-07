package com.pauluno.finledger.infrastructure.boot.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;
import com.pauluno.finledger.security.policy.SandboxIds;
import com.pauluno.finledger.security.policy.SandboxScenarioIds;

@Tag("unit")
class SandboxScenarioSeederTest {

    private InMemoryTenantRepository tenants;
    private InMemoryLedgerAccountRepository accounts;

    @BeforeEach
    void setUp() {
        tenants = new InMemoryTenantRepository();
        accounts = new InMemoryLedgerAccountRepository();
    }

    @Test
    void simple_seeder_creates_ecopay_and_two_usd_wallets() {
        SandboxSeedSnapshot snap = new SimpleSandboxSeeder().seed(tenants, accounts);

        assertThat(snap.scenario()).isEqualTo(SandboxScenario.SIMPLE);
        assertThat(snap.tenants()).hasSize(1);
        assertThat(snap.tenants().getFirst().name()).isEqualTo("EcoPay");
        assertThat(snap.tenants().getFirst().tenantId()).isEqualTo(SandboxIds.TENANT_ID);
        assertThat(snap.accounts()).hasSize(2);

        Tenant t = tenants.findById(SandboxIds.TENANT_ID).orElseThrow();
        assertThat(t.type()).isEqualTo(TenantType.STANDALONE);
        assertThat(accounts.findById(SandboxIds.FROM_ACCOUNT_ID).orElseThrow().type())
                .isEqualTo(AccountType.MERCHANT_WALLET);
    }

    @Test
    void aggregator_seeder_creates_network_and_sub_merchant() {
        SandboxSeedSnapshot snap = new AggregatorSandboxSeeder().seed(tenants, accounts);

        assertThat(snap.scenario()).isEqualTo(SandboxScenario.AGGREGATOR);
        assertThat(snap.tenants()).hasSize(2);
        assertThat(tenants.findById(SandboxScenarioIds.AGGREGATOR_TENANT_ID).orElseThrow().type())
                .isEqualTo(TenantType.AGGREGATOR);
        assertThat(tenants.findById(SandboxScenarioIds.SUB_MERCHANT_TENANT_ID).orElseThrow().type())
                .isEqualTo(TenantType.SUB_MERCHANT);
        assertThat(tenants.findById(SandboxScenarioIds.SUB_MERCHANT_TENANT_ID).orElseThrow().parentId())
                .contains(SandboxScenarioIds.AGGREGATOR_TENANT_ID);
        assertThat(accounts.findById(SandboxScenarioIds.AGGREGATOR_FEE_ACCOUNT_ID).orElseThrow().type())
                .isEqualTo(AccountType.FEE_PLATFORM_REVENUE);
        assertThat(snap.accounts()).hasSize(4);
    }

    @Test
    void remittance_seeder_creates_usd_and_eur_wallets() {
        SandboxSeedSnapshot snap = new RemittanceSandboxSeeder().seed(tenants, accounts);

        assertThat(snap.scenario()).isEqualTo(SandboxScenario.REMITTANCE);
        assertThat(snap.tenants()).hasSize(1);
        assertThat(snap.tenants().getFirst().name()).isEqualTo(SandboxScenarioIds.REMITTANCE_TENANT_NAME);
        assertThat(snap.accounts()).hasSize(4);
        assertThat(accounts.findById(SandboxScenarioIds.REMITTANCE_EUR_FROM_ID).orElseThrow().currency())
                .isEqualTo(Currency.getInstance("EUR"));
    }

    @Test
    void seeders_are_idempotent() {
        new SimpleSandboxSeeder().seed(tenants, accounts);
        new SimpleSandboxSeeder().seed(tenants, accounts);
        assertThat(tenants.findById(SandboxIds.TENANT_ID)).isPresent();
        assertThat(accounts.findById(SandboxIds.FROM_ACCOUNT_ID)).isPresent();
    }

    @Test
    void fromConfig_parses_known_values() {
        assertThat(SandboxScenario.fromConfig("simple")).isEqualTo(SandboxScenario.SIMPLE);
        assertThat(SandboxScenario.fromConfig("AGGREGATOR")).isEqualTo(SandboxScenario.AGGREGATOR);
        assertThat(SandboxScenario.fromConfig(null)).isEqualTo(SandboxScenario.SIMPLE);
    }

    private static final class InMemoryTenantRepository implements TenantRepository {
        private final Map<UUID, Tenant> byId = new ConcurrentHashMap<>();
        private final Map<UUID, LinkedHashSet<UUID>> ancestors = new ConcurrentHashMap<>();

        @Override
        public Tenant save(Tenant tenant) {
            byId.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void replaceAncestry(UUID tenantId, List<UUID> ancestorIdsIncludingSelf) {
            LinkedHashSet<UUID> unique = new LinkedHashSet<>(ancestorIdsIncludingSelf);
            unique.add(tenantId);
            ancestors.put(tenantId, unique);
        }

        @Override
        public List<UUID> findAncestorIds(UUID tenantId) {
            return new ArrayList<>(ancestors.getOrDefault(tenantId, new LinkedHashSet<>()));
        }

        @Override
        public List<UUID> findDescendantIds(UUID tenantId) {
            List<UUID> result = new ArrayList<>();
            for (var e : ancestors.entrySet()) {
                if (e.getValue().contains(tenantId)) {
                    result.add(e.getKey());
                }
            }
            return result;
        }
    }

    private static final class InMemoryLedgerAccountRepository implements LedgerAccountRepository {
        private final Map<UUID, LedgerAccount> byId = new ConcurrentHashMap<>();

        @Override
        public LedgerAccount save(LedgerAccount account) {
            byId.put(account.id(), account);
            return account;
        }

        @Override
        public Optional<LedgerAccount> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<LedgerAccount> findByIdForTenant(UUID id, UUID tenantId) {
            return findById(id).filter(a -> a.tenantId().equals(tenantId));
        }
    }
}
