package com.pauluno.finledger.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.FeeReversalPolicy;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;

/**
 * Scales every original posting by refund/total (including fee legs).
 */
public final class ProRataFeePolicy implements FeeReversalPolicy {

    @Override
    public List<Posting> calculateReversal(
            JournalEntry originalEntry,
            Money refundAmount,
            Map<UUID, LedgerAccount> accounts
    ) {
        Objects.requireNonNull(originalEntry, "originalEntry");
        Objects.requireNonNull(refundAmount, "refundAmount");
        Objects.requireNonNull(accounts, "accounts");
        if (refundAmount.isNegative() || refundAmount.isZero()) {
            throw new InvalidJournalEntryException("Refund amount must be positive");
        }

        Money originalTotal = Money.zero(refundAmount.currency());
        for (Posting posting : originalEntry.postings()) {
            if (posting.amount().isNegative()) {
                originalTotal = originalTotal.plus(posting.amount().negated());
            }
        }
        if (originalTotal.isZero()) {
            throw new InvalidJournalEntryException("Original entry has no debit total");
        }
        if (refundAmount.amount().compareTo(originalTotal.amount()) > 0) {
            throw new InvalidJournalEntryException(
                    "Refund exceeds original total (" + originalTotal.amount() + ")");
        }

        BigDecimal ratio = refundAmount.amount().divide(
                originalTotal.amount(), 12, RoundingMode.HALF_EVEN);

        Map<UUID, Money> scaled = new HashMap<>();
        Money debitSum = Money.zero(refundAmount.currency());
        Money creditSum = Money.zero(refundAmount.currency());
        UUID largestCreditAccount = null;
        Money largestCredit = Money.zero(refundAmount.currency());

        for (Posting posting : originalEntry.postings()) {
            requireAccount(accounts, posting.accountId());
            BigDecimal raw = posting.amount().amount().multiply(ratio)
                    .setScale(refundAmount.currency().getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
            // Reversal: negate original direction.
            Money reversed = Money.of(raw.negate(), refundAmount.currency());
            if (reversed.isZero()) {
                continue;
            }
            scaled.merge(posting.accountId(), reversed, Money::plus);
        }

        for (Map.Entry<UUID, Money> entry : scaled.entrySet()) {
            if (entry.getValue().isNegative()) {
                debitSum = debitSum.plus(entry.getValue().negated());
            } else {
                creditSum = creditSum.plus(entry.getValue());
                if (entry.getValue().amount().compareTo(largestCredit.amount()) > 0) {
                    largestCredit = entry.getValue();
                    largestCreditAccount = entry.getKey();
                }
            }
        }

        Money imbalance = creditSum.minus(debitSum);
        if (!imbalance.isZero() && largestCreditAccount != null) {
            scaled.merge(largestCreditAccount, imbalance.negated(), Money::plus);
        }

        List<Posting> result = new ArrayList<>();
        for (Map.Entry<UUID, Money> entry : scaled.entrySet()) {
            if (entry.getValue().isZero()) {
                continue;
            }
            result.add(new Posting(entry.getKey(), entry.getValue(), SettlementStatus.SETTLED));
        }
        if (result.size() < 2) {
            throw new InvalidJournalEntryException("Pro-rata refund produced fewer than 2 postings");
        }
        return result;
    }

    private static LedgerAccount requireAccount(Map<UUID, LedgerAccount> accounts, UUID id) {
        LedgerAccount account = accounts.get(id);
        if (account == null) {
            throw new InvalidJournalEntryException("Unknown account in original entry: " + id);
        }
        return account;
    }
}
