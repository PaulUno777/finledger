package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "journal_entry")
public class JournalEntryEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "transaction_reference", nullable = false)
    private String transactionReference;

    @Column(name = "entry_type", nullable = false)
    private String entryType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "reverses_entry_id")
    private UUID reversesEntryId;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("lineNo ASC")
    private List<PostingEntity> postings = new ArrayList<>();

    public JournalEntryEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public UUID getReversesEntryId() {
        return reversesEntryId;
    }

    public void setReversesEntryId(UUID reversesEntryId) {
        this.reversesEntryId = reversesEntryId;
    }

    public List<PostingEntity> getPostings() {
        return postings;
    }

    public void setPostings(List<PostingEntity> postings) {
        this.postings = postings;
    }

    public void addPosting(PostingEntity posting) {
        postings.add(posting);
        posting.setJournalEntry(this);
    }
}
