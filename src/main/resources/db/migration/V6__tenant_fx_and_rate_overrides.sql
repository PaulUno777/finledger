-- Tenant FX config + rate overrides (FL-060 / plan §4).

CREATE TABLE tenant_fx_config
(
    tenant_id             UUID PRIMARY KEY REFERENCES tenant (id),
    pivot_currency        VARCHAR(3)  NOT NULL,
    spread_bps            INT         NOT NULL DEFAULT 0,
    supported_currencies  VARCHAR(255) NOT NULL,
    CONSTRAINT chk_fx_spread_nonneg CHECK (spread_bps >= 0)
);

CREATE TABLE fx_rate_override
(
    id            UUID PRIMARY KEY,
    tenant_id     UUID           NOT NULL REFERENCES tenant (id),
    base_currency VARCHAR(3)     NOT NULL,
    quote_currency VARCHAR(3)    NOT NULL,
    rate          NUMERIC(38, 18) NOT NULL,
    valid_from    TIMESTAMPTZ    NOT NULL,
    valid_to      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_fx_rate_positive CHECK (rate > 0),
    CONSTRAINT chk_fx_rate_window CHECK (valid_to > valid_from),
    CONSTRAINT chk_fx_pair_distinct CHECK (base_currency <> quote_currency)
);

CREATE INDEX idx_fx_rate_override_lookup
    ON fx_rate_override (tenant_id, base_currency, quote_currency, valid_from, valid_to);

ALTER TABLE journal_entry
    ADD COLUMN rate_used      NUMERIC(38, 18) NULL,
    ADD COLUMN rate_source    VARCHAR(32)     NULL,
    ADD COLUMN rate_timestamp TIMESTAMPTZ     NULL;

-- Tenant-scoped FX config/overrides follow the same RLS posture as ledger data.
ALTER TABLE tenant_fx_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_fx_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_fx_config_isolation ON tenant_fx_config
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE fx_rate_override ENABLE ROW LEVEL SECURITY;
ALTER TABLE fx_rate_override FORCE ROW LEVEL SECURITY;
CREATE POLICY fx_rate_override_isolation ON fx_rate_override
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_fx_config TO finledger_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON fx_rate_override TO finledger_app;
