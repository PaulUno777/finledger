# ADR-007 — Hash-chained audit trail via AOP

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Financial mutations must be reconstructible and tamper-evident (plan §10).
Application logs are not enough — a hash-chained `audit_log` detects
retroactive alteration. Correlation with distributed traces requires capturing
W3C `traceparent` ids on each row.

Full OpenTelemetry and WORM/S3 Object Lock are valuable but heavier than this
phase needs (observability is FL-150; WORM is optional compliance).

## Decision

1. Append-only `audit_log` (Flyway V8) with FORCE RLS; app role gets SELECT/INSERT
   only; trigger rejects UPDATE/DELETE.
2. Per-tenant hash chain:
   `current_hash = SHA256(prev_hash + payload_hash + timestamp + actor)`
   with genesis `prev_hash = 64×'0'`. Latest row locked with `FOR UPDATE` on append.
3. Capture via `@Auditable` on mutating use cases + infrastructure
   `AuditableAspect` (requires `spring-boot-starter-aspectj` on Boot 4). Writes
   participate in the same DB transaction as the business operation.
4. Manual `traceparent` parse into `TraceContext` (no OTEL dependency yet).
5. Actor from `SecurityContext`, else `"anonymous"` until FL-100 OIDC.
6. Integrity verification use case + scheduled job + `GET …/audit/integrity`.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Application logging only | Not tamper-evident |
| OpenTelemetry SDK now | Deferred to FL-150; header parse is enough for correlation columns |
| WORM/S3 Object Lock in core | Optional adapter later; violates non-presumption as a hard default |
| Audit every GET | Deferred (§11 sensitive-read); focus mutations this phase |

## Consequences

- Positive: mutable operations leave a verifiable chain tied to tenant + trace ids
- Trade-off: CreateTenant / early TX without path tenant uses SET LOCAL inside the writer
- Follow-up: OTEL instrumentation (FL-150); optional WORM sink; sensitive-read auditing

## References

- `docs/PLAN_LEDGER_FINTECH.md` §10, §19 item 9
- `V8__create_audit_log.sql`, `AuditHashChain`, `AuditableAspect`
