package com.pauluno.finledger.infrastructure.boot.sandbox;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Tenant;
import com.pauluno.finledger.domain.model.TenantType;
import com.pauluno.finledger.security.policy.SandboxIds;

/**
 * Default pack: EcoPay standalone + two USD wallets ({@link SandboxIds} contract).
 */
public final class SimpleSandboxSeeder implements SandboxScenarioSeeder {

    private static final Currency USD = Currency.getInstance("USD");

    @Override
    public SandboxSeedSnapshot seed(TenantRepository tenants, LedgerAccountRepository accounts) {
        SandboxSeedSnapshot snap = new SandboxSeedSnapshot(SandboxScenario.SIMPLE);
        ensureTenant(tenants, SandboxIds.TENANT_ID, TenantType.STANDALONE, null, SandboxIds.TENANT_NAME);
        snap.addTenant(SandboxIds.TENANT_ID, SandboxIds.TENANT_NAME, TenantType.STANDALONE.name());
        ensureAccount(accounts, SandboxIds.FROM_ACCOUNT_ID, SandboxIds.TENANT_ID,
                SandboxIds.FROM_OWNER_REF, USD, AccountType.MERCHANT_WALLET);
        ensureAccount(accounts, SandboxIds.TO_ACCOUNT_ID, SandboxIds.TENANT_ID,
                SandboxIds.TO_OWNER_REF, USD, AccountType.MERCHANT_WALLET);
        snap.addAccount(SandboxIds.TENANT_ID, SandboxIds.FROM_ACCOUNT_ID, SandboxIds.FROM_OWNER_REF, "USD");
        snap.addAccount(SandboxIds.TENANT_ID, SandboxIds.TO_ACCOUNT_ID, SandboxIds.TO_OWNER_REF, "USD");
        return snap;
    }

    static void ensureTenant(
            TenantRepository tenants,
            UUID id,
            TenantType type,
            UUID parentId,
            String name
    ) {
        if (tenants.findById(id).isEmpty()) {
            tenants.save(new Tenant(id, type, parentId, name));
            List<UUID> ancestry = parentId == null
                    ? List.of(id)
                    : List.of(id, parentId);
            // For SUB_MERCHANT under aggregator, ancestry should be [self, parent, ...]
            if (parentId != null) {
                List<UUID> ancestors = new java.util.ArrayList<>();
                ancestors.add(id);
                ancestors.addAll(tenants.findAncestorIds(parentId));
                tenants.replaceAncestry(id, ancestors);
            } else {
                tenants.replaceAncestry(id, ancestry);
            }
        }
    }

    static void ensureAccount(
            LedgerAccountRepository accounts,
            UUID accountId,
            UUID tenantId,
            String ownerRef,
            Currency currency,
            AccountType type
    ) {
        if (accounts.findById(accountId).isPresent()) {
            return;
        }
        accounts.save(new LedgerAccount(
                accountId,
                tenantId,
                ownerRef,
                currency,
                type,
                AccountStatus.OPEN,
                true));
    }
}
