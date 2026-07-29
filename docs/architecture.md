# Architecture overview

FinLedger uses **hexagonal (ports & adapters)** architecture so the ledger core stays
framework-free and vendor-agnostic.

```text
┌─────────────────────────────────────────────────────────┐
│  Adapters in: REST · CLI · event consumers              │
└───────────────────────────┬─────────────────────────────┘
                            │ commands / queries
┌───────────────────────────▼─────────────────────────────┐
│  Application: use cases + ports (in / out)              │
└───────────────────────────┬─────────────────────────────┘
                            │ domain types only
┌───────────────────────────▼─────────────────────────────┐
│  Domain: Money, JournalEntry, Posting, validators       │
│  (no Spring / JPA / Kafka)                              │
└─────────────────────────────────────────────────────────┘
        ▲
        │ implement ports
┌───────┴─────────────────────────────────────────────────┐
│  Infrastructure: persistence, outbox, FX, security, …   │
└─────────────────────────────────────────────────────────┘
```

## Non-negotiables

- Append-only journal; corrections via linked reverse entries
- Double-entry sum-zero per pivot currency
- Idempotency for mutating API and message consumers
- Transactional outbox for events that must follow a DB write
- Tenant isolation (`tenant_id` + RLS when hierarchy lands)

## Where to read next

| Topic | Document |
|-------|----------|
| Full product plan | [PLAN_LEDGER_FINTECH.md](PLAN_LEDGER_FINTECH.md) |
| Structural decisions | [PLAN_LEDGER_FINTECH.md §21](PLAN_LEDGER_FINTECH.md) and [adr/](adr/) |
| Extension ports | Plan §2.3 |
| Roadmap / tickets | [development.md](development.md) |
| Configuration layers | [configuration.md](configuration.md) |

Decision record for this layout: [ADR-001](adr/ADR-001-clean-architecture.md).
