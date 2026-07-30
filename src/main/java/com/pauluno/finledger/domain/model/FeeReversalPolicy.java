package com.pauluno.finledger.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes refund postings for an original journal entry (plan §5.3).
 */
public interface FeeReversalPolicy {

    List<Posting> calculateReversal(
            JournalEntry originalEntry,
            Money refundAmount,
            Map<UUID, LedgerAccount> accounts
    );

    static boolean isFeeAccountType(AccountType type) {
        return switch (type) {
            case FEE_PLATFORM_REVENUE, FEE_INTERCHANGE_COST, FEE_AGGREGATOR_MARKUP,
                 TAX_VAT, RESERVE_HOLD -> true;
            default -> false;
        };
    }
}
