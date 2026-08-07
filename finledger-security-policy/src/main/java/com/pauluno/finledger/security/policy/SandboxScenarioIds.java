package com.pauluno.finledger.security.policy;

import java.util.UUID;

/**
 * Stable UUIDs for non-{@code simple} sandbox scenario packs (FL-157).
 * {@link SandboxIds} remains the {@code simple} pack contract.
 */
public final class SandboxScenarioIds {

    // --- aggregator: EcoPay Network + Send Tunnel ---
    public static final UUID AGGREGATOR_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID SUB_MERCHANT_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    public static final UUID AGGREGATOR_POOL_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    public static final UUID AGGREGATOR_FEE_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    public static final UUID SUB_MERCHANT_FROM_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    public static final UUID SUB_MERCHANT_TO_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b4");

    public static final String AGGREGATOR_TENANT_NAME = "EcoPay Network";
    public static final String SUB_MERCHANT_TENANT_NAME = "Send Tunnel";
    public static final String AGGREGATOR_POOL_OWNER_REF = "ecopay-pool";
    public static final String AGGREGATOR_FEE_OWNER_REF = "ecopay-fee";
    public static final String SUB_MERCHANT_FROM_OWNER_REF = "sendtunnel-from";
    public static final String SUB_MERCHANT_TO_OWNER_REF = "sendtunnel-to";

    // --- remittance: Send Tunnel Remit ---
    public static final UUID REMITTANCE_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    public static final UUID REMITTANCE_USD_FROM_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    public static final UUID REMITTANCE_USD_TO_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    public static final UUID REMITTANCE_EUR_FROM_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c4");
    public static final UUID REMITTANCE_EUR_TO_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000c5");

    public static final String REMITTANCE_TENANT_NAME = "Send Tunnel Remit";
    public static final String REMITTANCE_USD_FROM_OWNER_REF = "remit-usd-from";
    public static final String REMITTANCE_USD_TO_OWNER_REF = "remit-usd-to";
    public static final String REMITTANCE_EUR_FROM_OWNER_REF = "remit-eur-from";
    public static final String REMITTANCE_EUR_TO_OWNER_REF = "remit-eur-to";

    private SandboxScenarioIds() {
    }
}
