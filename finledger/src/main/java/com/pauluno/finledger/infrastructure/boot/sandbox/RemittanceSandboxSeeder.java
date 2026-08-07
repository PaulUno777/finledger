package com.pauluno.finledger.infrastructure.boot.sandbox;

import java.util.Currency;

import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.TenantType;
import com.pauluno.finledger.security.policy.SandboxScenarioIds;

/**
 * Remittance demo: Send Tunnel Remit with USD + EUR wallets.
 */
public final class RemittanceSandboxSeeder implements SandboxScenarioSeeder {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Override
    public SandboxSeedSnapshot seed(TenantRepository tenants, LedgerAccountRepository accounts) {
        SandboxSeedSnapshot snap = new SandboxSeedSnapshot(SandboxScenario.REMITTANCE);

        SimpleSandboxSeeder.ensureTenant(
                tenants,
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                TenantType.STANDALONE,
                null,
                SandboxScenarioIds.REMITTANCE_TENANT_NAME);
        snap.addTenant(
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_TENANT_NAME,
                TenantType.STANDALONE.name());

        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.REMITTANCE_USD_FROM_ID,
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_USD_FROM_OWNER_REF,
                USD,
                AccountType.MERCHANT_WALLET);
        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.REMITTANCE_USD_TO_ID,
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_USD_TO_OWNER_REF,
                USD,
                AccountType.MERCHANT_WALLET);
        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.REMITTANCE_EUR_FROM_ID,
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_EUR_FROM_OWNER_REF,
                EUR,
                AccountType.MERCHANT_WALLET);
        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.REMITTANCE_EUR_TO_ID,
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_EUR_TO_OWNER_REF,
                EUR,
                AccountType.MERCHANT_WALLET);

        snap.addAccount(
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_USD_FROM_ID,
                SandboxScenarioIds.REMITTANCE_USD_FROM_OWNER_REF,
                "USD");
        snap.addAccount(
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_USD_TO_ID,
                SandboxScenarioIds.REMITTANCE_USD_TO_OWNER_REF,
                "USD");
        snap.addAccount(
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_EUR_FROM_ID,
                SandboxScenarioIds.REMITTANCE_EUR_FROM_OWNER_REF,
                "EUR");
        snap.addAccount(
                SandboxScenarioIds.REMITTANCE_TENANT_ID,
                SandboxScenarioIds.REMITTANCE_EUR_TO_ID,
                SandboxScenarioIds.REMITTANCE_EUR_TO_OWNER_REF,
                "EUR");
        return snap;
    }
}
