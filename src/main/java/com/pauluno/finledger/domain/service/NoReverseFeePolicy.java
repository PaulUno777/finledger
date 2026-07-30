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
 * Default policy: fees are kept; only principal (non-fee) legs are reversed.
 */
public final class NoReverseFeePolicy implements FeeReversalPolicy {

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

        Money principalTotal = Money.zero(refundAmount.currency());
        UUID sourceAccountId = null;
        for (Posting posting : originalEntry.postings()) {
            LedgerAccount account = requireAccount(accounts, posting.accountId());
            if (posting.amount().isNegative()) {
                sourceAccountId = posting.accountId();
            } else if (!FeeReversalPolicy.isFeeAccountType(account.type())) {
                principalTotal = principalTotal.plus(posting.amount());
            }
        }
        if (sourceAccountId == null) {
            throw new InvalidJournalEntryException("Original entry has no source debit posting");
        }
        if (principalTotal.isZero()) {
            throw new InvalidJournalEntryException("Original entry has no principal credit postings");
        }
        if (refundAmount.amount().compareTo(principalTotal.amount()) > 0) {
            throw new InvalidJournalEntryException(
                    "Refund exceeds principal total (" + principalTotal.amount() + ")");
        }

        BigDecimal ratio = refundAmount.amount().divide(
                principalTotal.amount(), 12, RoundingMode.HALF_EVEN);

        Map<UUID, Money> debitLegs = new HashMap<>();
        Money allocated = Money.zero(refundAmount.currency());
        for (Posting posting : originalEntry.postings()) {
            LedgerAccount account = requireAccount(accounts, posting.accountId());
            if (posting.amount().isNegative() || FeeReversalPolicy.isFeeAccountType(account.type())) {
                continue;
            }
            BigDecimal raw = posting.amount().amount().multiply(ratio)
                    .setScale(refundAmount.currency().getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
            Money portion = Money.of(raw, refundAmount.currency());
            if (portion.isZero()) {
                continue;
            }
            debitLegs.merge(posting.accountId(), portion, Money::plus);
            allocated = allocated.plus(portion);
        }

        Money remainder = refundAmount.minus(allocated);
        if (!remainder.isZero()) {
            // Assign remainder to the largest principal credit account.
            UUID remainderAccount = debitLegs.entrySet().stream()
                    .max((a, b) -> a.getValue().amount().compareTo(b.getValue().amount()))
                    .map(Map.Entry::getKey)
                    .orElseThrow();
            debitLegs.merge(remainderAccount, remainder, Money::plus);
        }

        List<Posting> result = new ArrayList<>();
        result.add(new Posting(sourceAccountId, refundAmount, SettlementStatus.SETTLED));
        for (Map.Entry<UUID, Money> entry : debitLegs.entrySet()) {
            result.add(new Posting(entry.getKey(), entry.getValue().negated(), SettlementStatus.SETTLED));
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
