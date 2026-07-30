package com.pauluno.finledger.application.port.in;

import java.util.UUID;

import com.pauluno.finledger.application.event.TransactionPosted;

public interface HoldFundsForReviewUseCase {

    /**
     * Moves credit legs of the source posting into the tenant hold account.
     * Skips risk re-check. Idempotent per source journal entry.
     *
     * @return hold journal entry id, or empty if skipped / already held
     */
    java.util.Optional<UUID> execute(TransactionPosted source, UUID holdAccountId);
}
