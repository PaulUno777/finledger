package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TenantSplitRuleSetId implements Serializable {

    private UUID tenantId;
    private String ruleSetKey;

    public TenantSplitRuleSetId() {
    }

    public TenantSplitRuleSetId(UUID tenantId, String ruleSetKey) {
        this.tenantId = tenantId;
        this.ruleSetKey = ruleSetKey;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getRuleSetKey() {
        return ruleSetKey;
    }

    public void setRuleSetKey(String ruleSetKey) {
        this.ruleSetKey = ruleSetKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TenantSplitRuleSetId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(ruleSetKey, that.ruleSetKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, ruleSetKey);
    }
}
