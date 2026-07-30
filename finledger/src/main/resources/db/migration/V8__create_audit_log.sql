-- Hash-chained append-only audit log (FL-090 / plan §10).

CREATE TABLE audit_log
(
    id             UUID         PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor          VARCHAR(256) NOT NULL,
    action         VARCHAR(64)  NOT NULL,
    resource_type  VARCHAR(64)  NOT NULL,
    resource_id    UUID         NULL,
    payload        TEXT         NOT NULL,
    payload_hash   VARCHAR(64)  NOT NULL,
    prev_hash      VARCHAR(64)  NOT NULL,
    current_hash   VARCHAR(64)  NOT NULL,
    trace_id       VARCHAR(32)  NULL,
    span_id        VARCHAR(16)  NULL
);

CREATE INDEX idx_audit_log_tenant_occurred ON audit_log (tenant_id, occurred_at, id);
CREATE INDEX idx_audit_log_tenant_id ON audit_log (tenant_id, id);

-- Append-only at the grant layer (REVOKE overrides default ALL privileges).
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_log_isolation ON audit_log
    FOR ALL
    USING (app_visible_tenant(tenant_id))
    WITH CHECK (app_visible_tenant(tenant_id));

-- SELECT+INSERT for writes; UPDATE privilege retained so SELECT … FOR UPDATE works.
-- Trigger below still rejects actual UPDATE/DELETE (append-only).
GRANT SELECT, INSERT, UPDATE ON audit_log TO finledger_app;
REVOKE DELETE ON audit_log FROM finledger_app;

CREATE OR REPLACE FUNCTION audit_log_reject_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;

CREATE TRIGGER audit_log_no_update
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
EXECUTE PROCEDURE audit_log_reject_mutation();
