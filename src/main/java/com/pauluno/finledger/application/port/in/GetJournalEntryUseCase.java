package com.pauluno.finledger.application.port.in;

import java.util.UUID;

import com.pauluno.finledger.application.dto.PostTransactionResult;

public interface GetJournalEntryUseCase {

    PostTransactionResult execute(UUID tenantId, UUID journalEntryId);
}
