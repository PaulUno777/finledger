package com.pauluno.finledger.domain.model;

/**
 * Settlement state of a posting. Balance views (AVAILABLE / PENDING / HELD) are
 * projections over this field and account type — see {@link AccountBalance}.
 */
public enum SettlementStatus {
    PENDING,
    SETTLED
}
