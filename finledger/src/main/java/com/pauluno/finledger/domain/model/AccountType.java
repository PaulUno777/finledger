package com.pauluno.finledger.domain.model;

/**
 * Account taxonomy from the product plan (§1.2).
 */
public enum AccountType {
    MERCHANT_WALLET,
    AGGREGATOR_POOL,
    RAIL_CLEARING,
    SUSPENSE_HOLD,
    FEE_PLATFORM_REVENUE,
    FEE_INTERCHANGE_COST,
    FEE_AGGREGATOR_MARKUP,
    RESERVE_HOLD,
    TAX_VAT
}
