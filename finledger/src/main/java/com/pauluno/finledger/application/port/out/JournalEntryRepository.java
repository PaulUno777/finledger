package com.pauluno.finledger.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;

public interface JournalEntryRepository {

    JournalEntry save(JournalEntry entry);

    Optional<JournalEntry> findById(UUID id);

    Optional<JournalEntry> findByTenantAndIdempotencyKey(UUID tenantId, IdempotencyKey key);
}
