package com.pauluno.finledger.security.policy;

import java.util.UUID;

/**
 * Stable identity contract for the default ({@code simple}) sandbox seed under the
 * {@code sandbox} profile.
 *
 * <p>Used by {@code SandboxBootstrap}, the ephemeral JWT issuer ({@code tenant_id} claim),
 * dump curls, and ITs. Do not delete or randomize — copy-paste demos and tests depend on
 * these UUIDs. Richer demo packs (aggregator / remittance) are FL-157 and keep this class
 * as the {@code simple} pack contract.
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
