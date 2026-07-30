package com.pauluno.finledger.domain.model;

import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Rebuildable balance projection for one account (plan §6).
 * Not a source of truth — derived from {@link Posting} sums.
 */
public record AccountBalance(
        UUID accountId,
        Currency currency,
        Money available,
        Money pending,
        Money held
) {

    public AccountBalance {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(held, "held");
        if (!available.currency().equals(currency)
                || !pending.currency().equals(currency)
                || !held.currency().equals(currency)) {
            throw new IllegalArgumentException("Balance components must match account currency");
        }
    }

    public static AccountBalance zero(UUID accountId, Currency currency) {
        Money z = Money.zero(currency);
        return new AccountBalance(accountId, currency, z, z, z);
    }

    public AccountBalance apply(Posting posting) {
        if (!posting.accountId().equals(accountId)) {
            throw new IllegalArgumentException("Posting account does not match balance account");
        }
        if (!posting.amount().currency().equals(currency)) {
            throw new IllegalArgumentException("Posting currency does not match balance currency");
        }

        Money nextAvailable = available;
        Money nextPending = pending;
        Money nextHeld = held;

        if (posting.settlementStatus() == SettlementStatus.SETTLED) {
            nextAvailable = available.plus(posting.amount());
        } else {
            nextPending = pending.plus(posting.amount());
        }

        return new AccountBalance(accountId, currency, nextAvailable, nextPending, nextHeld);
    }

    public AccountBalance withHeld(Money heldAmount) {
        return new AccountBalance(accountId, currency, available, pending, heldAmount);
    }
}
