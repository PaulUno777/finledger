package com.pauluno.finledger.infrastructure.boot.sandbox;

import java.util.Currency;

import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.TenantType;
import com.pauluno.finledger.security.policy.SandboxScenarioIds;

/**
 * Aggregator EcoPay Network + sub-merchant Send Tunnel + pool/fee/wallets.
 */
public final class AggregatorSandboxSeeder implements SandboxScenarioSeeder {

    private static final Currency USD = Currency.getInstance("USD");

    @Override
    public SandboxSeedSnapshot seed(TenantRepository tenants, LedgerAccountRepository accounts) {
        SandboxSeedSnapshot snap = new SandboxSeedSnapshot(SandboxScenario.AGGREGATOR);

        SimpleSandboxSeeder.ensureTenant(
                tenants,
                SandboxScenarioIds.AGGREGATOR_TENANT_ID,
                TenantType.AGGREGATOR,
                null,
                SandboxScenarioIds.AGGREGATOR_TENANT_NAME);
        snap.addTenant(
                SandboxScenarioIds.AGGREGATOR_TENANT_ID,
                SandboxScenarioIds.AGGREGATOR_TENANT_NAME,
                TenantType.AGGREGATOR.name());

        SimpleSandboxSeeder.ensureTenant(
                tenants,
                SandboxScenarioIds.SUB_MERCHANT_TENANT_ID,
                TenantType.SUB_MERCHANT,
                SandboxScenarioIds.AGGREGATOR_TENANT_ID,
                SandboxScenarioIds.SUB_MERCHANT_TENANT_NAME);
        snap.addTenant(
                SandboxScenarioIds.SUB_MERCHANT_TENANT_ID,
                SandboxScenarioIds.SUB_MERCHANT_TENANT_NAME,
                TenantType.SUB_MERCHANT.name());

        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.AGGREGATOR_POOL_ACCOUNT_ID,
                SandboxScenarioIds.AGGREGATOR_TENANT_ID,
                SandboxScenarioIds.AGGREGATOR_POOL_OWNER_REF,
                USD,
                AccountType.AGGREGATOR_POOL);
        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.AGGREGATOR_FEE_ACCOUNT_ID,
                SandboxScenarioIds.AGGREGATOR_TENANT_ID,
                SandboxScenarioIds.AGGREGATOR_FEE_OWNER_REF,
                USD,
                AccountType.FEE_PLATFORM_REVENUE);
        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.SUB_MERCHANT_FROM_ACCOUNT_ID,
                SandboxScenarioIds.SUB_MERCHANT_TENANT_ID,
                SandboxScenarioIds.SUB_MERCHANT_FROM_OWNER_REF,
                USD,
                AccountType.MERCHANT_WALLET);
        SimpleSandboxSeeder.ensureAccount(
                accounts,
                SandboxScenarioIds.SUB_MERCHANT_TO_ACCOUNT_ID,
                SandboxScenarioIds.SUB_MERCHANT_TENANT_ID,
                SandboxScenarioIds.SUB_MERCHANT_TO_OWNER_REF,
                USD,
                AccountType.MERCHANT_WALLET);

        snap.addAccount(
                SandboxScenarioIds.AGGREGATOR_TENANT_ID,
                SandboxScenarioIds.AGGREGATOR_POOL_ACCOUNT_ID,
                SandboxScenarioIds.AGGREGATOR_POOL_OWNER_REF,
                "USD");
        snap.addAccount(
                SandboxScenarioIds.AGGREGATOR_TENANT_ID,
                SandboxScenarioIds.AGGREGATOR_FEE_ACCOUNT_ID,
                SandboxScenarioIds.AGGREGATOR_FEE_OWNER_REF,
                "USD");
        snap.addAccount(
                SandboxScenarioIds.SUB_MERCHANT_TENANT_ID,
                SandboxScenarioIds.SUB_MERCHANT_FROM_ACCOUNT_ID,
                SandboxScenarioIds.SUB_MERCHANT_FROM_OWNER_REF,
                "USD");
        snap.addAccount(
                SandboxScenarioIds.SUB_MERCHANT_TENANT_ID,
                SandboxScenarioIds.SUB_MERCHANT_TO_ACCOUNT_ID,
                SandboxScenarioIds.SUB_MERCHANT_TO_OWNER_REF,
                "USD");
        return snap;
    }
}
