package com.pauluno.finledger.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.pauluno.finledger.domain.service.DoubleEntryValidator;

/**
 * Immutable accounting transaction (append-only). Corrections happen only via
 * {@link #reverse(IdempotencyKey, Instant)}, which creates a new linked opposite entry.
 */
public final class JournalEntry {

    private final UUID id;
    private final UUID tenantId;
    private final IdempotencyKey idempotencyKey;
    private final TransactionReference transactionReference;
    private final JournalEntryType type;
    private final List<Posting> postings;
    private final Instant occurredAt;
    private final UUID reversesEntryId;

    private JournalEntry(
            UUID id,
            UUID tenantId,
            IdempotencyKey idempotencyKey,
            TransactionReference transactionReference,
            JournalEntryType type,
            List<Posting> postings,
            Instant occurredAt,
            UUID reversesEntryId
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.transactionReference = Objects.requireNonNull(transactionReference, "transactionReference");
        this.type = Objects.requireNonNull(type, "type");
        this.postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.reversesEntryId = reversesEntryId;
    }

    public static JournalEntry create(
            UUID tenantId,
            IdempotencyKey idempotencyKey,
            TransactionReference transactionReference,
            List<Posting> postings,
            Map<UUID, LedgerAccount> accounts,
            Map<UUID, AccountBalance> balancesBefore,
            Instant occurredAt
    ) {
        DoubleEntryValidator.validate(postings, accounts, balancesBefore);
        return new JournalEntry(
                UUID.randomUUID(),
                tenantId,
                idempotencyKey,
                transactionReference,
                JournalEntryType.POSTING,
                postings,
                occurredAt,
                null
        );
    }

    /**
     * Hydrates an entry already persisted and validated. Does not re-run
     * {@link DoubleEntryValidator} — the database is the source of truth for reload.
     */
    public static JournalEntry reconstitute(
            UUID id,
            UUID tenantId,
            IdempotencyKey idempotencyKey,
            TransactionReference transactionReference,
            JournalEntryType type,
            List<Posting> postings,
            Instant occurredAt,
            UUID reversesEntryId
    ) {
        return new JournalEntry(
                id,
                tenantId,
                idempotencyKey,
                transactionReference,
                type,
                postings,
                occurredAt,
                reversesEntryId
        );
    }

    /**
     * Creates a new REVERSAL entry with opposite signed amounts, linked to this entry.
     * Does not mutate this instance.
     */
    public JournalEntry reverse(
            IdempotencyKey reversalKey,
            Instant reversalTime,
            Map<UUID, LedgerAccount> accounts,
            Map<UUID, AccountBalance> balancesBefore
    ) {
        List<Posting> opposite = new ArrayList<>(postings.size());
        for (Posting posting : postings) {
            opposite.add(posting.reversed());
        }
        DoubleEntryValidator.validate(opposite, accounts, balancesBefore);
        return new JournalEntry(
                UUID.randomUUID(),
                tenantId,
                reversalKey,
                transactionReference,
                JournalEntryType.REVERSAL,
                opposite,
                reversalTime,
                id
        );
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public TransactionReference transactionReference() {
        return transactionReference;
    }

    public JournalEntryType type() {
        return type;
    }

    public List<Posting> postings() {
        return Collections.unmodifiableList(postings);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Optional<UUID> reversesEntryId() {
        return Optional.ofNullable(reversesEntryId);
    }
}
