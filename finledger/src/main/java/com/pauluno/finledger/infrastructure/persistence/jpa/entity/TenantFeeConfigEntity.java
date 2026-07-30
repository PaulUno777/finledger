package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_fee_config")
public class TenantFeeConfigEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "fee_reversal_policy", nullable = false, length = 32)
    private String feeReversalPolicy;

    public TenantFeeConfigEntity() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getFeeReversalPolicy() {
        return feeReversalPolicy;
    }

    public void setFeeReversalPolicy(String feeReversalPolicy) {
        this.feeReversalPolicy = feeReversalPolicy;
    }
}
