package com.pauluno.finledger.infrastructure.boot.sandbox;

import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;

@FunctionalInterface
public interface SandboxScenarioSeeder {

    SandboxSeedSnapshot seed(TenantRepository tenants, LedgerAccountRepository accounts);
}
