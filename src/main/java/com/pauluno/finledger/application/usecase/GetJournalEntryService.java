package com.pauluno.finledger.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.GetJournalEntryUseCase;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.TenantRepository;
import com.pauluno.finledger.domain.model.JournalEntry;

@Service
public class GetJournalEntryService implements GetJournalEntryUseCase {

    private final JournalEntryRepository journalEntryRepository;
    private final TenantRepository tenantRepository;

    public GetJournalEntryService(
            JournalEntryRepository journalEntryRepository,
            TenantRepository tenantRepository
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PostTransactionResult execute(UUID tenantId, UUID journalEntryId) {
        JournalEntry entry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal entry not found: " + journalEntryId));
        // Path tenant must be the entry's tenant or an ancestor (AGGREGATOR → SUB_MERCHANT).
        if (!tenantRepository.findDescendantIds(tenantId).contains(entry.tenantId())) {
            throw new ResourceNotFoundException("Journal entry not found: " + journalEntryId);
        }
        return PostTransactionService.toResult(entry, false);
    }
}
