package com.pauluno.finledger.infrastructure.security;

/**
 * Shared Spring Security authority names for ledger scopes.
 */
public final class LedgerAuthorities {

    public static final String SCOPE_LEDGER_READ = "SCOPE_ledger:read";
    public static final String SCOPE_LEDGER_WRITE = "SCOPE_ledger:write";
    public static final String SCOPE_LEDGER_ADMIN = "SCOPE_ledger:admin";
    /** Control-plane only (FL-158) — not for tenant-scoped ledger data-plane routes. */
    public static final String SCOPE_PLATFORM_ADMIN = "SCOPE_platform:admin";

    public static final String PLATFORM_ADMIN = "platform:admin";

    private LedgerAuthorities() {
    }
}
