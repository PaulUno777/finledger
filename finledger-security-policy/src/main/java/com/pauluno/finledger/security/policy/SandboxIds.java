package com.pauluno.finledger.security.policy;

import java.util.UUID;

/**
 * Well-known sandbox identities for {@code disabled} / eval seeding (FL-151).
 */
public final class SandboxIds {

    public static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID FROM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID TO_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    public static final String TENANT_NAME = "sandbox";
    public static final String FROM_OWNER_REF = "sandbox-from";
    public static final String TO_OWNER_REF = "sandbox-to";

    private SandboxIds() {
    }
}
