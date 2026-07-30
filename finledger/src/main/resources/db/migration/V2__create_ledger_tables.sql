-- Ledger core tables (FL-020). Append-only journal; balances are a rebuildable projection.

CREATE TABLE ledger_account
(
    id               UUID         PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    owner_ref        VARCHAR(255) NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    account_type     VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    allows_overdraft BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_ledger_account_tenant ON ledger_account (tenant_id);

CREATE TABLE journal_entry
(
    id                    UUID         PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    idempotency_key       VARCHAR(255) NOT NULL,
    transaction_reference VARCHAR(255) NOT NULL,
    entry_type            VARCHAR(32)  NOT NULL,
    occurred_at           TIMESTAMPTZ  NOT NULL,
    reverses_entry_id     UUID         NULL REFERENCES journal_entry (id),
    CONSTRAINT uq_journal_entry_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_journal_entry_tenant ON journal_entry (tenant_id);

CREATE TABLE posting
(
    id                UUID            PRIMARY KEY,
    journal_entry_id  UUID            NOT NULL REFERENCES journal_entry (id),
    account_id        UUID            NOT NULL REFERENCES ledger_account (id),
    amount            NUMERIC(38, 18) NOT NULL,
    currency          VARCHAR(3)      NOT NULL,
    settlement_status VARCHAR(32)     NOT NULL,
    line_no           INT             NOT NULL,
    CONSTRAINT uq_posting_entry_line UNIQUE (journal_entry_id, line_no)
);

CREATE INDEX idx_posting_account ON posting (account_id);
CREATE INDEX idx_posting_journal ON posting (journal_entry_id);

CREATE TABLE account_balance
(
    account_id UUID            PRIMARY KEY REFERENCES ledger_account (id),
    tenant_id  UUID            NOT NULL,
    currency   VARCHAR(3)      NOT NULL,
    available  NUMERIC(38, 18) NOT NULL,
    pending    NUMERIC(38, 18) NOT NULL,
    held       NUMERIC(38, 18) NOT NULL,
    version    BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_account_balance_tenant ON account_balance (tenant_id);
