-- Split rules + fee reversal config (FL-070 / plan §5).

CREATE TABLE tenant_split_rule_set
(
    tenant_id        UUID         NOT NULL REFERENCES tenant (id),
    rule_set_key     VARCHAR(128) NOT NULL,
    rules_json       TEXT         NOT NULL,
    remainder_target VARCHAR(64)  NOT NULL,
    PRIMARY KEY (tenant_id, rule_set_key)
);

CREATE TABLE tenant_fee_config
(
    tenant_id            UUID        PRIMARY KEY REFERENCES tenant (id),
    fee_reversal_policy  VARCHAR(32) NOT NULL DEFAULT 'NO_REVERSE',
    CONSTRAINT chk_fee_reversal_policy CHECK (fee_reversal_policy IN ('NO_REVERSE', 'PRO_RATA'))
);

ALTER TABLE tenant_split_rule_set ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_split_rule_set FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_split_rule_set_isolation ON tenant_split_rule_set
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE tenant_fee_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_fee_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fee_config_isolation ON tenant_fee_config
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_split_rule_set TO finledger_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_fee_config TO finledger_app;
