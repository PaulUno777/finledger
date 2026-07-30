package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_fraud_config")
public class TenantFraudConfigEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "fail_mode", nullable = false, length = 16)
    private String failMode;

    @Column(name = "max_amount", precision = 38, scale = 18)
    private BigDecimal maxAmount;

    @Column(name = "velocity_max", nullable = false)
    private int velocityMax;

    @Column(name = "velocity_window_seconds", nullable = false)
    private int velocityWindowSeconds;

    @Column(name = "hold_account_id")
    private UUID holdAccountId;

    @Column(name = "denylist_owner_refs", nullable = false)
    private String denylistOwnerRefs;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFailMode() {
        return failMode;
    }

    public void setFailMode(String failMode) {
        this.failMode = failMode;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public int getVelocityMax() {
        return velocityMax;
    }

    public void setVelocityMax(int velocityMax) {
        this.velocityMax = velocityMax;
    }

    public int getVelocityWindowSeconds() {
        return velocityWindowSeconds;
    }

    public void setVelocityWindowSeconds(int velocityWindowSeconds) {
        this.velocityWindowSeconds = velocityWindowSeconds;
    }

    public UUID getHoldAccountId() {
        return holdAccountId;
    }

    public void setHoldAccountId(UUID holdAccountId) {
        this.holdAccountId = holdAccountId;
    }

    public String getDenylistOwnerRefs() {
        return denylistOwnerRefs;
    }

    public void setDenylistOwnerRefs(String denylistOwnerRefs) {
        this.denylistOwnerRefs = denylistOwnerRefs;
    }
}
