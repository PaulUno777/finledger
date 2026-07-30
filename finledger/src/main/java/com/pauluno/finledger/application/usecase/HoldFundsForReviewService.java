package com.pauluno.finledger.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.event.TransactionPosted;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.HoldFundsForReviewUseCase;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.RiskDecisionRepository;
import com.pauluno.finledger.domain.exception.AccountClosedException;
import com.pauluno.finledger.domain.exception.CurrencyMismatchException;
import com.pauluno.finledger.domain.exception.InsufficientFundsException;
import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;
import com.pauluno.finledger.domain.model.TransactionReference;

@Service
public class HoldFundsForReviewService implements HoldFundsForReviewUseCase {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final RiskDecisionRepository riskDecisionRepository;

    public HoldFundsForReviewService(
            LedgerAccountRepository ledgerAccountRepository,
            AccountBalanceRepository accountBalanceRepository,
            JournalEntryRepository journalEntryRepository,
            RiskDecisionRepository riskDecisionRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.riskDecisionRepository = riskDecisionRepository;
    }

    @Override
    @Transactional
    @Auditable(action = "HOLD_FOR_REVIEW", resourceType = "JOURNAL_ENTRY")
    public Optional<UUID> execute(TransactionPosted source, UUID holdAccountId) {
        Optional<RiskDecisionRepository.RiskDecisionRecord> existing =
                riskDecisionRepository.findAsyncHoldForSource(source.tenantId(), source.journalEntryId());
        if (existing.isPresent()) {
            return Optional.ofNullable(existing.get().holdJournalEntryId());
        }

        LedgerAccount hold = ledgerAccountRepository
                .findByIdForTenant(holdAccountId, source.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Hold account not found: " + holdAccountId));
        if (hold.type() != AccountType.SUSPENSE_HOLD && hold.type() != AccountType.RESERVE_HOLD) {
            throw new BusinessRuleException("INVALID_HOLD_ACCOUNT", "Hold account must be SUSPENSE_HOLD or RESERVE_HOLD");
        }

        Map<String, BigDecimal> creditByCurrency = new HashMap<>();
        Map<UUID, BigDecimal> creditByAccount = new HashMap<>();
        String currencyCode = null;
        for (TransactionPosted.PostingSummary leg : source.postings()) {
            BigDecimal amt = new BigDecimal(leg.amount());
            if (amt.signum() <= 0) {
                continue;
            }
            currencyCode = leg.currencyCode();
            creditByCurrency.merge(leg.currencyCode(), amt, BigDecimal::add);
            creditByAccount.merge(leg.accountId(), amt, BigDecimal::add);
        }
        if (creditByAccount.isEmpty() || currencyCode == null || creditByCurrency.size() != 1) {
            return Optional.empty();
        }

        List<Posting> postings = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> e : creditByAccount.entrySet()) {
            postings.add(new Posting(
                    e.getKey(),
                    Money.of(e.getValue().negate(), java.util.Currency.getInstance(currencyCode)),
                    SettlementStatus.SETTLED
            ));
            total = total.add(e.getValue());
        }
        postings.add(new Posting(
                holdAccountId,
                Money.of(total, java.util.Currency.getInstance(currencyCode)),
                SettlementStatus.SETTLED
        ));

        List<UUID> accountIds = new ArrayList<>(creditByAccount.keySet());
        accountIds.add(holdAccountId);
        Map<UUID, LedgerAccount> accounts = new HashMap<>();
        for (UUID id : accountIds) {
            accounts.put(id, ledgerAccountRepository.findByIdForTenant(id, source.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id)));
        }
        Map<UUID, AccountBalance> balances = accountBalanceRepository.findByAccountIds(accountIds);

        try {
            String idem = "fraud-hold-" + source.journalEntryId();
            JournalEntry entry = JournalEntry.create(
                    source.tenantId(),
                    new IdempotencyKey(idem),
                    new TransactionReference("HOLD-" + source.transactionReference()),
                    postings,
                    accounts,
                    balances,
                    Instant.now(),
                    null
            );
            JournalEntry saved = journalEntryRepository.save(entry);
            return Optional.of(saved.id());
        } catch (InvalidJournalEntryException
                 | CurrencyMismatchException
                 | InsufficientFundsException
                 | AccountClosedException ex) {
            throw new BusinessRuleException("HOLD_FAILED", ex.getMessage(), ex);
        }
    }
}
