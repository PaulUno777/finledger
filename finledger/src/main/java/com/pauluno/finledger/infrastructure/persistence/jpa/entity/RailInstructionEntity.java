package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rail_instruction")
public class RailInstructionEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "rail_code", nullable = false, length = 64)
    private String railCode;

    @Column(name = "rail_reference", nullable = false, length = 128)
    private String railReference;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "clearing_account_id", nullable = false)
    private UUID clearingAccountId;

    @Column(name = "counterparty_account_id", nullable = false)
    private UUID counterpartyAccountId;

    @Column(name = "initiate_journal_entry_id")
    private UUID initiateJournalEntryId;

    @Column(name = "settle_journal_entry_id")
    private UUID settleJournalEntryId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public String getRailCode() {
        return railCode;
    }

    public void setRailCode(String railCode) {
        this.railCode = railCode;
    }

    public String getRailReference() {
        return railReference;
    }

    public void setRailReference(String railReference) {
        this.railReference = railReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getClearingAccountId() {
        return clearingAccountId;
    }

    public void setClearingAccountId(UUID clearingAccountId) {
        this.clearingAccountId = clearingAccountId;
    }

    public UUID getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    public void setCounterpartyAccountId(UUID counterpartyAccountId) {
        this.counterpartyAccountId = counterpartyAccountId;
    }

    public UUID getInitiateJournalEntryId() {
        return initiateJournalEntryId;
    }

    public void setInitiateJournalEntryId(UUID initiateJournalEntryId) {
        this.initiateJournalEntryId = initiateJournalEntryId;
    }

    public UUID getSettleJournalEntryId() {
        return settleJournalEntryId;
    }

    public void setSettleJournalEntryId(UUID settleJournalEntryId) {
        this.settleJournalEntryId = settleJournalEntryId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
