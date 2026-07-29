package com.pauluno.finledger.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.GetJournalEntryUseCase;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.domain.model.JournalEntry;

@Service
public class GetJournalEntryService implements GetJournalEntryUseCase {

    private final JournalEntryRepository journalEntryRepository;

    public GetJournalEntryService(JournalEntryRepository journalEntryRepository) {
        this.journalEntryRepository = journalEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PostTransactionResult execute(UUID tenantId, UUID journalEntryId) {
        JournalEntry entry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journal entry not found: " + journalEntryId));
        if (!entry.tenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Journal entry not found: " + journalEntryId);
        }
        return PostTransactionService.toResult(entry, false);
    }
}
