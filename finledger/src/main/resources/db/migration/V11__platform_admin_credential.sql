-- Platform bootstrap claim (FL-158): one-shot fail-closed marker for IdP-less cold-start.

CREATE TABLE platform_admin_credential
(
    id                       SMALLINT    PRIMARY KEY DEFAULT 1,
    claimed_at               TIMESTAMPTZ NOT NULL,
    jti                      UUID        NOT NULL,
    bootstrap_secret_sha256  BYTEA       NULL,
    CONSTRAINT chk_platform_admin_credential_singleton CHECK (id = 1)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON platform_admin_credential TO finledger_app;
