-- Hierarchical tenants + Postgres RLS (FL-050 / plan §3).

CREATE TABLE tenant
(
    id               UUID         PRIMARY KEY,
    tenant_type      VARCHAR(32)  NOT NULL,
    parent_tenant_id UUID         NULL REFERENCES tenant (id),
    name             VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenant_parent CHECK (
        (tenant_type IN ('STANDALONE', 'AGGREGATOR') AND parent_tenant_id IS NULL)
            OR (tenant_type = 'SUB_MERCHANT' AND parent_tenant_id IS NOT NULL)
        )
);

CREATE INDEX idx_tenant_parent ON tenant (parent_tenant_id);

CREATE TABLE tenant_ancestry
(
    ancestor_id   UUID NOT NULL REFERENCES tenant (id),
    descendant_id UUID NOT NULL REFERENCES tenant (id),
    PRIMARY KEY (ancestor_id, descendant_id)
);

CREATE INDEX idx_tenant_ancestry_descendant ON tenant_ancestry (descendant_id);

-- Shared visibility predicate: rows whose tenant is the current tenant or a descendant.
-- Fail closed when GUC is missing/blank. Internal jobs may set app.rls_bypass=on.

CREATE OR REPLACE FUNCTION app_visible_tenant(target_tenant_id UUID)
    RETURNS BOOLEAN
    LANGUAGE sql
    STABLE
AS
$$
SELECT CASE
           WHEN current_setting('app.rls_bypass', true) = 'on' THEN TRUE
           WHEN NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL THEN FALSE
           ELSE EXISTS (SELECT 1
                        FROM tenant_ancestry a
                        WHERE a.ancestor_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
                          AND a.descendant_id = target_tenant_id)
       END;
$$;

-- Runtime role must not be a superuser: Docker POSTGRES_USER bypasses RLS even with FORCE.
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'finledger_app') THEN
            CREATE ROLE finledger_app LOGIN PASSWORD 'finledger'
                NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
        END IF;
    END
$$;

GRANT CONNECT ON DATABASE finledger TO finledger_app;
GRANT USAGE ON SCHEMA public TO finledger_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO finledger_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO finledger_app;
GRANT EXECUTE ON FUNCTION app_visible_tenant(UUID) TO finledger_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO finledger_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO finledger_app;

ALTER TABLE ledger_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_account FORCE ROW LEVEL SECURITY;
CREATE POLICY ledger_account_tenant_isolation ON ledger_account
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE journal_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entry FORCE ROW LEVEL SECURITY;
CREATE POLICY journal_entry_tenant_isolation ON journal_entry
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE account_balance ENABLE ROW LEVEL SECURITY;
ALTER TABLE account_balance FORCE ROW LEVEL SECURITY;
CREATE POLICY account_balance_tenant_isolation ON account_balance
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE idempotency_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_record FORCE ROW LEVEL SECURITY;
CREATE POLICY idempotency_record_tenant_isolation ON idempotency_record
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event FORCE ROW LEVEL SECURITY;
CREATE POLICY outbox_event_tenant_isolation ON outbox_event
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

ALTER TABLE posting ENABLE ROW LEVEL SECURITY;
ALTER TABLE posting FORCE ROW LEVEL SECURITY;
CREATE POLICY posting_tenant_isolation ON posting
    FOR ALL
    USING (
        EXISTS (SELECT 1
                FROM journal_entry je
                WHERE je.id = posting.journal_entry_id
                  AND app_visible_tenant(je.tenant_id))
        )
    WITH CHECK (
        EXISTS (SELECT 1
                FROM journal_entry je
                WHERE je.id = posting.journal_entry_id
                  AND app_visible_tenant(je.tenant_id))
        );
