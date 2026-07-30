-- Fraud / risk (FL-130 / plan §17).

CREATE TABLE tenant_fraud_config
(
    tenant_id                 UUID PRIMARY KEY REFERENCES tenant (id),
    enabled                   BOOLEAN        NOT NULL DEFAULT FALSE,
    fail_mode                 VARCHAR(16)    NOT NULL DEFAULT 'OPEN',
    max_amount                NUMERIC(38, 18) NULL,
    velocity_max              INT            NOT NULL DEFAULT 0,
    velocity_window_seconds   INT            NOT NULL DEFAULT 3600,
    hold_account_id           UUID           NULL REFERENCES ledger_account (id),
    denylist_owner_refs       TEXT           NOT NULL DEFAULT '',
    CONSTRAINT chk_fraud_fail_mode CHECK (fail_mode IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_fraud_velocity CHECK (velocity_max >= 0 AND velocity_window_seconds > 0)
);

CREATE TABLE risk_decision
(
    id                     UUID           PRIMARY KEY,
    tenant_id              UUID           NOT NULL REFERENCES tenant (id),
    journal_entry_id       UUID           NULL,
    source_journal_entry_id UUID          NULL,
    transaction_reference  VARCHAR(128)   NOT NULL,
    phase                  VARCHAR(16)    NOT NULL,
    outcome                VARCHAR(16)    NOT NULL,
    reason_code            VARCHAR(64)    NOT NULL,
    score                  INT            NOT NULL,
    rule_ids               TEXT           NOT NULL DEFAULT '',
    hold_journal_entry_id  UUID           NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_risk_phase CHECK (phase IN ('SYNC', 'ASYNC')),
    CONSTRAINT chk_risk_outcome CHECK (outcome IN ('ALLOW', 'DENY', 'REVIEW'))
);

CREATE INDEX idx_risk_decision_tenant_created ON risk_decision (tenant_id, created_at DESC);
CREATE INDEX idx_risk_decision_tenant_ref ON risk_decision (tenant_id, transaction_reference);
CREATE UNIQUE INDEX uq_risk_decision_async_hold
    ON risk_decision (tenant_id, source_journal_entry_id)
    WHERE phase = 'ASYNC' AND hold_journal_entry_id IS NOT NULL;

ALTER TABLE tenant_fraud_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_fraud_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fraud_config_isolation ON tenant_fraud_config
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE risk_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_decision FORCE ROW LEVEL SECURITY;
CREATE POLICY risk_decision_isolation ON risk_decision
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_fraud_config TO finledger_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON risk_decision TO finledger_app;
