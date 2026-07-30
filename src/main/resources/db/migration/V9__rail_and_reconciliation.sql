-- Rail instructions + reconciliation breaks (FL-110 / plan §7).

CREATE TABLE rail_instruction
(
    id                         UUID           PRIMARY KEY,
    tenant_id                  UUID           NOT NULL REFERENCES tenant (id),
    rail_code                  VARCHAR(64)    NOT NULL,
    rail_reference             VARCHAR(128)   NOT NULL,
    amount                     NUMERIC(38, 18) NOT NULL,
    currency_code              VARCHAR(3)     NOT NULL,
    status                     VARCHAR(32)    NOT NULL,
    clearing_account_id        UUID           NOT NULL,
    counterparty_account_id    UUID           NOT NULL,
    initiate_journal_entry_id  UUID           NULL,
    settle_journal_entry_id    UUID           NULL,
    idempotency_key            VARCHAR(128)   NOT NULL,
    created_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rail_instruction_status CHECK (status IN ('INITIATED', 'SETTLED', 'FAILED')),
    CONSTRAINT uq_rail_instruction_ref UNIQUE (tenant_id, rail_reference),
    CONSTRAINT uq_rail_instruction_idem UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_rail_instruction_tenant_status ON rail_instruction (tenant_id, status);

CREATE TABLE reconciliation_break
(
    id               UUID            PRIMARY KEY,
    tenant_id        UUID            NOT NULL REFERENCES tenant (id),
    rail_reference   VARCHAR(128)    NOT NULL,
    expected_amount  NUMERIC(38, 18) NULL,
    reported_amount  NUMERIC(38, 18) NULL,
    currency_code    VARCHAR(3)      NOT NULL,
    reason           VARCHAR(64)     NOT NULL,
    detected_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    report_batch_id  UUID            NOT NULL
);

CREATE INDEX idx_reconciliation_break_tenant ON reconciliation_break (tenant_id, detected_at DESC);

ALTER TABLE rail_instruction ENABLE ROW LEVEL SECURITY;
ALTER TABLE rail_instruction FORCE ROW LEVEL SECURITY;
CREATE POLICY rail_instruction_isolation ON rail_instruction
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE reconciliation_break ENABLE ROW LEVEL SECURITY;
ALTER TABLE reconciliation_break FORCE ROW LEVEL SECURITY;
CREATE POLICY reconciliation_break_isolation ON reconciliation_break
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON rail_instruction TO finledger_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON reconciliation_break TO finledger_app;
