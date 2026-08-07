# ADR-017 — Hardening FL-170 (correctness then scale)

- **Status:** Accepted
- **Date:** 2026-08-07
- **Deciders:** FinLedger maintainers

## Context

Roadmap FL-170 (“Durcissement”) and plan §8.3 / §9 / §11 / §12.1 left unfinished
concurrency and hardening work. A correctness review showed optimistic-lock
retries, outbox↔fraud coupling, virtual threads, Hikari sizing, and race tests
were incomplete relative to what earlier phases claimed.

## Decision

1. **Finish claimed concurrency first:** new-TX optimistic retries with
   overdraft re-validation on the re-read balance snapshot; short outbox poll TX
   with real async fraud; enable virtual threads; explicit Hikari pool; concurrent
   posting IT.
2. **Then planned hardening:** PIT mutation gate on `domain`, Bucket4j in-memory
   rate limit, webhook timestamp/nonce anti-replay, security headers, Dependabot.
3. **Explicitly defer** (not broken features — scale/topology):
   - Account partitioning (§8.3 advanced)
   - Redis-backed rate limit / multi-instance FX cache
   - CDC/Debezium outbox and Kafka modules
   - Async/WORM audit scale-out (sync hash chain remains correct for v1)
   - Column encryption; sensitive-read audit

## Consequences

- Ledger money paths under contention match §8.3 semantics before throughput
  ornaments.
- Single-node in-memory nonce store and Bucket4j are documented limits for
  multi-instance deployments.
