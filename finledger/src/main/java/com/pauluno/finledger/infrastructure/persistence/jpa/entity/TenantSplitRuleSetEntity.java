package com.pauluno.finledger.infrastructure.persistence.jpa.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_split_rule_set")
@IdClass(TenantSplitRuleSetId.class)
public class TenantSplitRuleSetEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "rule_set_key", length = 128)
    private String ruleSetKey;

    @Column(name = "rules_json", nullable = false, columnDefinition = "TEXT")
    private String rulesJson;

    @Column(name = "remainder_target", nullable = false, length = 64)
    private String remainderTarget;

    public TenantSplitRuleSetEntity() {
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

    public String getRulesJson() {
        return rulesJson;
    }

    public void setRulesJson(String rulesJson) {
        this.rulesJson = rulesJson;
    }

    public String getRemainderTarget() {
        return remainderTarget;
    }

    public void setRemainderTarget(String remainderTarget) {
        this.remainderTarget = remainderTarget;
    }
}
