package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ledger_account")
public class LedgerAccountEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_ref", nullable = false)
    private String ownerRef;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(nullable = false)
    private String status;

    @Column(name = "allows_overdraft", nullable = false)
    private boolean allowsOverdraft;

    @Version
    private long version;

    public LedgerAccountEntity() {
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

    public String getOwnerRef() {
        return ownerRef;
    }

    public void setOwnerRef(String ownerRef) {
        this.ownerRef = ownerRef;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAllowsOverdraft() {
        return allowsOverdraft;
    }

    public void setAllowsOverdraft(boolean allowsOverdraft) {
        this.allowsOverdraft = allowsOverdraft;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
