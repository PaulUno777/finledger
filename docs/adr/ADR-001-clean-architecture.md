# ADR-001 — Clean / Hexagonal Architecture

- **Status:** Accepted
- **Date:** 2026-07-29
- **Deciders:** FinLedger maintainers

## Context

FinLedger is a multi-tenant, double-entry ledger. Business invariants (sum-zero
postings, append-only corrections, idempotency, tenant isolation) must remain
testable without Spring, JPA, Kafka, or HTTP. Third-party tools (brokers, IAM,
secrets, rails, fraud engines) must not be hard-wired into the core.

## Decision

Adopt Clean / Hexagonal Architecture with packages:

| Package | Responsibility |
|---------|----------------|
| `domain` | Pure model and invariants — **zero framework dependencies** |
| `application` | Use cases + inbound/outbound ports (no vendor SDK types in signatures) |
| `infrastructure` | Adapters implementing ports (JPA, outbox, FX, security, messaging) |
| `presentation` / `adapter/in` | REST, CLI, event consumers — transport only |

Dependencies point **inward only**. ArchUnit tests enforce that `domain` does not
depend on Spring, JPA, Kafka, or Vault packages.

Optional vendor adapters are preferred as conditional modules behind ports
(`docs/PLAN_LEDGER_FINTECH.md` §2.3), not imports from `application` or `domain`.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Classic layered Spring “service everywhere” | Domain rules leak into controllers and JPA entities; hard to property-test |
| Full CQRS / event sourcing from day one | Over-engineering for v1; plan deliberately keeps a simpler write model |

## Consequences

- High testability of money movement without containers for unit tests
- More explicit ports/mappers (accepted boilerplate for a ledger)
- New features must follow: domain → ports → use case → adapter → config → tests

## References

- `docs/PLAN_LEDGER_FINTECH.md` §2, §21
- `docs/architecture.md`
- ArchUnit rules under `src/test/java/.../architecture/`
