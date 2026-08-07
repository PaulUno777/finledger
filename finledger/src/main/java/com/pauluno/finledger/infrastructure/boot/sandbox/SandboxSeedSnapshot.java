package com.pauluno.finledger.infrastructure.boot.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Summary of what a scenario seeder created (for sandbox-ready dump).
 */
public final class SandboxSeedSnapshot {

    public record AccountLine(UUID tenantId, UUID accountId, String ownerRef, String currency) {
    }

    public record TenantLine(UUID tenantId, String name, String type) {
    }

    private final SandboxScenario scenario;
    private final List<TenantLine> tenants = new ArrayList<>();
    private final List<AccountLine> accounts = new ArrayList<>();

    public SandboxSeedSnapshot(SandboxScenario scenario) {
        this.scenario = scenario;
    }

    public SandboxScenario scenario() {
        return scenario;
    }

    public void addTenant(UUID id, String name, String type) {
        tenants.add(new TenantLine(id, name, type));
    }

    public void addAccount(UUID tenantId, UUID accountId, String ownerRef, String currency) {
        accounts.add(new AccountLine(tenantId, accountId, ownerRef, currency));
    }

    public List<TenantLine> tenants() {
        return List.copyOf(tenants);
    }

    public List<AccountLine> accounts() {
        return List.copyOf(accounts);
    }
}
