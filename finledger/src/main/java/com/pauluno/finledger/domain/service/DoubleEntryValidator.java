package com.pauluno.finledger.domain.service;

import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.pauluno.finledger.domain.exception.AccountClosedException;
import com.pauluno.finledger.domain.exception.CurrencyMismatchException;
import com.pauluno.finledger.domain.exception.InsufficientFundsException;
import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountStatus;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;

/**
 * Enforces double-entry and account invariants for a candidate journal entry.
 */
public final class DoubleEntryValidator {

    private DoubleEntryValidator() {
    }

    public static void validate(
            List<Posting> postings,
            Map<UUID, LedgerAccount> accounts,
            Map<UUID, AccountBalance> balancesBefore) {
        Objects.requireNonNull(postings, "postings");
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(balancesBefore, "balancesBefore");

        if (postings.size() < 2) {
            throw new InvalidJournalEntryException("Journal entry requires at least 2 postings");
        }

        validateAccountsAndCurrencies(postings, accounts);
        validateSumsToZeroPerCurrency(postings);

        Map<UUID, AccountBalance> after = BalanceCalculator.applyPostings(
                accounts, balancesBefore, postings);
        validateOverdraft(accounts, after);
    }

    private static void validateAccountsAndCurrencies(
            List<Posting> postings,
            Map<UUID, LedgerAccount> accounts) {
        for (Posting posting : postings) {
            LedgerAccount account = accounts.get(posting.accountId());
            if (account == null) {
                throw new InvalidJournalEntryException(
                        "Unknown account in posting: " + posting.accountId());
            }
            if (account.status() == AccountStatus.CLOSED) {
                throw new AccountClosedException(
                        "Account is CLOSED and cannot receive postings: " + account.id());
            }
            if (!account.currency().equals(posting.amount().currency())) {
                throw new CurrencyMismatchException(
                        "Posting currency " + posting.amount().currency().getCurrencyCode()
                                + " does not match account currency "
                                + account.currency().getCurrencyCode()
                                + " for account " + account.id());
            }
        }
    }

    private static void validateSumsToZeroPerCurrency(List<Posting> postings) {
        Map<Currency, Money> sums = new HashMap<>();
        Set<Currency> currencies = new HashSet<>();

        for (Posting posting : postings) {
            Currency currency = posting.amount().currency();
            currencies.add(currency);
            sums.merge(currency, posting.amount(), Money::plus);
        }

        for (Currency currency : currencies) {
            Money sum = sums.get(currency);
            if (!sum.isZero()) {
                throw new InvalidJournalEntryException(
                        "Postings do not sum to zero for currency "
                                + currency.getCurrencyCode()
                                + " (sum=" + sum.amount() + ")");
            }
        }
    }

    private static void validateOverdraft(
            Map<UUID, LedgerAccount> accounts,
            Map<UUID, AccountBalance> balancesAfter) {
        for (Map.Entry<UUID, AccountBalance> entry : balancesAfter.entrySet()) {
            LedgerAccount account = accounts.get(entry.getKey());
            if (account == null) {
                continue;
            }
            Money available = entry.getValue().available();
            if (available.isNegative() && !account.allowsOverdraft()) {
                throw new InsufficientFundsException(
                        "Insufficient available funds on account " + account.id()
                                + " (available=" + available.amount() + " "
                                + available.currency().getCurrencyCode() + ")");
            }
        }
    }
}
