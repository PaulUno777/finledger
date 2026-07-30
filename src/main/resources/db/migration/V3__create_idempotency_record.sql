-- API-level idempotency store (plan §8.1). Orthogonal to journal_entry.idempotency_key.

CREATE TABLE idempotency_record
(
    tenant_id         UUID         NOT NULL,
    idempotency_key   VARCHAR(255) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    response_snapshot TEXT,
    status            VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_record_expires ON idempotency_record (expires_at);
