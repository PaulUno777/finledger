package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "risk_decision")
public class RiskDecisionEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "source_journal_entry_id")
    private UUID sourceJournalEntryId;

    @Column(name = "transaction_reference", nullable = false, length = 128)
    private String transactionReference;

    @Column(nullable = false, length = 16)
    private String phase;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(nullable = false)
    private int score;

    @Column(name = "rule_ids", nullable = false)
    private String ruleIds;

    @Column(name = "hold_journal_entry_id")
    private UUID holdJournalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public void setJournalEntryId(UUID journalEntryId) {
        this.journalEntryId = journalEntryId;
    }

    public UUID getSourceJournalEntryId() {
        return sourceJournalEntryId;
    }

    public void setSourceJournalEntryId(UUID sourceJournalEntryId) {
        this.sourceJournalEntryId = sourceJournalEntryId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getRuleIds() {
        return ruleIds;
    }

    public void setRuleIds(String ruleIds) {
        this.ruleIds = ruleIds;
    }

    public UUID getHoldJournalEntryId() {
        return holdJournalEntryId;
    }

    public void setHoldJournalEntryId(UUID holdJournalEntryId) {
        this.holdJournalEntryId = holdJournalEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
