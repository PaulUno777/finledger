package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_break")
public class ReconciliationBreakEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "rail_reference", nullable = false, length = 128)
    private String railReference;

    @Column(name = "expected_amount", precision = 38, scale = 18)
    private BigDecimal expectedAmount;

    @Column(name = "reported_amount", precision = 38, scale = 18)
    private BigDecimal reportedAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "report_batch_id", nullable = false)
    private UUID reportBatchId;

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

    public String getRailReference() {
        return railReference;
    }

    public void setRailReference(String railReference) {
        this.railReference = railReference;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getReportedAmount() {
        return reportedAmount;
    }

    public void setReportedAmount(BigDecimal reportedAmount) {
        this.reportedAmount = reportedAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public UUID getReportBatchId() {
        return reportBatchId;
    }

    public void setReportBatchId(UUID reportBatchId) {
        this.reportBatchId = reportBatchId;
    }
}
