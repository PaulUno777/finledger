package com.pauluno.finledger.infrastructure.security;

/**
 * Shared Spring Security authority names for ledger scopes.
 */
public final class LedgerAuthorities {

    public static final String SCOPE_LEDGER_READ = "SCOPE_ledger:read";
    public static final String SCOPE_LEDGER_WRITE = "SCOPE_ledger:write";
    public static final String SCOPE_LEDGER_ADMIN = "SCOPE_ledger:admin";

    public static final String TENANT_HEADER = "X-FinLedger-Tenant-Id";
    public static final String STATIC_TOKEN_SECRET_KEY = "FINLEDGER_STATIC_TOKEN";

    private LedgerAuthorities() {
    }
}
