-- Transactional outbox (FL-040 / plan §9). Written in the same DB tx as JournalEntry.

CREATE TABLE outbox_event
(
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ  NULL
);

CREATE INDEX idx_outbox_event_pending ON outbox_event (status, created_at);
