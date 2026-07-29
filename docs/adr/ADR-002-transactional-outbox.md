# ADR-002 — Transactional Outbox

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

After a journal entry is posted, downstream consumers need a reliable
`TransactionPosted` signal. Publishing directly to a broker inside the same
request that writes the journal risks dual-write failure: the DB commit can
succeed while the publish fails (or the reverse), leaving the system
inconsistent.

Two-phase commit (2PC / XA) across the database and a message broker is
operationally fragile and couples the ledger core to a specific broker.

## Decision

Adopt the transactional outbox pattern (plan §9):

1. In the **same DB transaction** as `JournalEntry` persistence, append a row to
   `outbox_event` with `status=PENDING` and a JSON `TransactionPosted` payload.
2. A scheduled in-box poller claims pending rows (`FOR UPDATE SKIP LOCKED`),
   publishes via the broker-agnostic `EventPublisher` port, then marks
   `PUBLISHED`.
3. The default `EventPublisher` is a logging sink (zero extra infra). Concrete
   brokers (Kafka, etc.) plug in later behind the same port.

Replay of an idempotent API request does **not** append a second outbox row.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Direct `EventPublisher.publish` in `PostTransactionService` | Dual-write; can lose or duplicate events relative to the journal |
| 2PC / XA | Operational complexity and fragility (plan §15) |
| CDC / Debezium as the only path | Optional later; poller keeps FL-040 zero-infra |

## Consequences

- Positive: journal + outbox commit atomically; publisher is swappable
- Trade-off: at-least-once delivery until consumers apply §8.2 idempotent handling
- Follow-up: optional Kafka adapter module; consumer-side message dedup table

## References

- `docs/PLAN_LEDGER_FINTECH.md` §9, §15, §19 item 4
- `OutboxWriter`, `OutboxEventRepository`, `OutboxPoller`, `EventPublisher`
