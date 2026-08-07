package com.pauluno.finledger.infrastructure.persistence.jpa.adapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.service.BalanceCalculator;
import com.pauluno.finledger.domain.service.DoubleEntryValidator;
import com.pauluno.finledger.infrastructure.persistence.jpa.mapper.JournalEntryMapper;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataJournalEntryRepository;

@Component
public class JournalEntryJpaAdapter implements JournalEntryRepository {

    private final SpringDataJournalEntryRepository springData;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public JournalEntryJpaAdapter(
            SpringDataJournalEntryRepository springData,
            LedgerAccountRepository ledgerAccountRepository,
            AccountBalanceRepository accountBalanceRepository
    ) {
        this.springData = springData;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Override
    @Transactional
    public JournalEntry save(JournalEntry entry) {
        if (springData.existsById(entry.id())) {
            throw new IllegalStateException("Journal entries are append-only; cannot update " + entry.id());
        }

        Set<UUID> accountIds = new HashSet<>();
        for (Posting posting : entry.postings()) {
            accountIds.add(posting.accountId());
        }

        Map<UUID, LedgerAccount> accounts = new HashMap<>();
        for (UUID accountId : accountIds) {
            LedgerAccount account = ledgerAccountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown account: " + accountId));
            if (!account.tenantId().equals(entry.tenantId())) {
                throw new IllegalArgumentException(
                        "Account " + accountId + " does not belong to tenant " + entry.tenantId());
            }
            accounts.put(accountId, account);
        }

        Map<UUID, AccountBalance> currentBalances = accountBalanceRepository.findByAccountIds(accountIds);
        // Re-validate overdraft/sum-zero on the versioned snapshot about to be written (§8.3 / FL-170)
        DoubleEntryValidator.validate(entry.postings(), accounts, currentBalances);
        Map<UUID, AccountBalance> nextBalances = BalanceCalculator.applyPostings(
                accounts, currentBalances, entry.postings());

        var saved = springData.save(JournalEntryMapper.toEntity(entry));

        for (AccountBalance balance : nextBalances.values()) {
            LedgerAccount account = accounts.get(balance.accountId());
            accountBalanceRepository.save(balance, account.tenantId());
        }

        return JournalEntryMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JournalEntry> findById(UUID id) {
        return springData.findById(id).map(JournalEntryMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JournalEntry> findByTenantAndIdempotencyKey(UUID tenantId, IdempotencyKey key) {
        return springData.findByTenantIdAndIdempotencyKey(tenantId, key.value())
                .map(JournalEntryMapper::toDomain);
    }
}
