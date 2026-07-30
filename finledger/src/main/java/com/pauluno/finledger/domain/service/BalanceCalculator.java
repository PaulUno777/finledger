package com.pauluno.finledger.domain.service;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;

/**
 * Pure projection of balances from postings — never stores mutable state on accounts.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    public static AccountBalance forAccount(LedgerAccount account, List<Posting> postings) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(postings, "postings");

        Money available = Money.zero(account.currency());
        Money pending = Money.zero(account.currency());

        for (Posting posting : postings) {
            if (!posting.accountId().equals(account.id())) {
                continue;
            }
            if (!posting.amount().currency().equals(account.currency())) {
                throw new IllegalArgumentException(
                        "Posting currency does not match account " + account.id());
            }
            if (posting.settlementStatus() == SettlementStatus.SETTLED) {
                available = available.plus(posting.amount());
            } else {
                pending = pending.plus(posting.amount());
            }
        }

        Money held = account.isHoldAccount()
                ? available.plus(pending)
                : Money.zero(account.currency());

        return new AccountBalance(account.id(), account.currency(), available, pending, held);
    }

    public static Map<UUID, AccountBalance> projectAll(
            Map<UUID, LedgerAccount> accounts,
            List<Posting> postings
    ) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(postings, "postings");

        Map<UUID, AccountBalance> balances = new HashMap<>();
        for (LedgerAccount account : accounts.values()) {
            balances.put(account.id(), forAccount(account, postings));
        }
        return balances;
    }

    /**
     * Applies candidate postings onto existing balances (AVAILABLE / PENDING only;
     * held is recomputed for hold accounts).
     */
    public static Map<UUID, AccountBalance> applyPostings(
            Map<UUID, LedgerAccount> accounts,
            Map<UUID, AccountBalance> currentBalances,
            List<Posting> newPostings
    ) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(currentBalances, "currentBalances");
        Objects.requireNonNull(newPostings, "newPostings");

        Map<UUID, AccountBalance> next = new HashMap<>(currentBalances);
        for (Posting posting : newPostings) {
            LedgerAccount account = accounts.get(posting.accountId());
            if (account == null) {
                throw new IllegalArgumentException("Unknown account: " + posting.accountId());
            }
            AccountBalance balance = next.getOrDefault(
                    posting.accountId(),
                    AccountBalance.zero(account.id(), account.currency()));
            AccountBalance updated = balance.apply(posting);
            if (account.isHoldAccount()) {
                Money held = updated.available().plus(updated.pending());
                updated = updated.withHeld(held);
            }
            next.put(posting.accountId(), updated);
        }
        return next;
    }

    public static Money sumByCurrency(List<Posting> postings, Currency currency) {
        Money sum = Money.zero(currency);
        for (Posting posting : postings) {
            if (posting.amount().currency().equals(currency)) {
                sum = sum.plus(posting.amount());
            }
        }
        return sum;
    }
}
