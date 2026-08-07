# ADR-010 — CLI as a separate Maven module (HTTP client)

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Plan §16 / §19 (FL-120) calls for a provisioning CLI so operators can create
tenants, accounts, FX config, and split rules without a web admin UI. An earlier
sketch mentioned `/admin/*` routes; the public API already exposes the needed
surfaces under `/api/v1/...`. Embedding Picocli inside the Spring Boot process
would couple operator tooling to the server classpath and complicate packaging.

## Decision

1. Convert the repo to a **multi-module Maven reactor**: parent
   `finledger-parent`, server module `finledger` (same artifact coordinates as
   before), CLI module `finledger-cli`.
2. `finledger-cli` is a **plain JDK HTTP client** of `/api/v1` (Picocli + JLine 3
   + Jackson). No Spring dependency; auth via Bearer JWT
   (`--token` / `FINLEDGER_TOKEN`).
3. Do **not** add new `/admin` endpoints in this phase — map plan §16 sketches to
   existing REST routes.
4. Server logs an INFO hint at boot when zero tenants exist; never blocks health.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Embed Picocli in the Boot app | Couples CLI lifecycle to the server; harder to distribute a lean jar |
| New `/admin/*` surface | Duplicates `/api/v1`; deferred until product needs a distinct admin API |
| Official SDKs | Roadmap FL-180 |

## Consequences

- Positive: operators get a runnable shaded CLI jar; server stays focused on the API
- Trade-off: CLI must track REST contracts; breaking API changes need CLI updates
- Follow-up: document `FINLEDGER_BASE_URL` / `FINLEDGER_TOKEN` in configuration docs
- The CLI’s private `ApiClient` is intentional until FL-160 ships an in-repo
  `/sdk-reference/` client (and FL-180 official multi-lang SDKs in **separate
  repos**); do not extract a shared SDK module in this phase.
- **Amended by [ADR-015](ADR-015-operational-model.md):** CLI gains a local **ops**
  surface (Compose wrappers, doctor, config validate) while keeping HTTP **api**
  commands as the remote admin path; no embedded server lifecycle.
- **FL-160 delivered:** in-repo non-official [`/sdk-reference/`](../../sdk-reference/)
  (idempotency, webhook HMAC, retry, `traceparent`). The CLI remains a separate
  module and does not depend on `sdk-reference`; official SDKs stay FL-180.
