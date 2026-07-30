package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_fx_config")
public class TenantFxConfigEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "pivot_currency", nullable = false, length = 3)
    private String pivotCurrency;

    @Column(name = "spread_bps", nullable = false)
    private int spreadBps;

    @Column(name = "supported_currencies", nullable = false)
    private String supportedCurrencies;

    public TenantFxConfigEntity() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPivotCurrency() {
        return pivotCurrency;
    }

    public void setPivotCurrency(String pivotCurrency) {
        this.pivotCurrency = pivotCurrency;
    }

    public int getSpreadBps() {
        return spreadBps;
    }

    public void setSpreadBps(int spreadBps) {
        this.spreadBps = spreadBps;
    }

    public String getSupportedCurrencies() {
        return supportedCurrencies;
    }

    public void setSupportedCurrencies(String supportedCurrencies) {
        this.supportedCurrencies = supportedCurrencies;
    }
}
