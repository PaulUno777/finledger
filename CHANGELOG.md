# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Document published Hub image as `unoteck/finledger:0.1.0` / `:latest` in README and
  integration guide

## [0.1.0] - 2026-08-08

First public release of the completed v1 roadmap track (FL-010 … FL-190).

### Added

- Developer integration guide finalized (FL-190): local eval through production —
  Hub image + external IdP go-live, required env table, illustrative Kubernetes
  Deployment/Service + probes on `:8081`, day-0/day-2 ops (rate limit, webhook
  anti-replay, observability) ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md))
- Hardening (FL-170): virtual threads + Hikari pool defaults; concurrent posting IT;
  PIT mutation gate on `domain` (threshold 48); Bucket4j in-memory rate limit;
  rail webhook timestamp/nonce anti-replay; security headers; Dependabot;
  [ADR-017](docs/adr/ADR-017-hardening-fl-170.md)
- Contract tests + `/sdk-reference/` (FL-160): OpenAPI path/`operationId` snapshot
  at `docs/contracts/openapi-paths.json` with `@Tag("contract")` IT (any drift fails);
  behavioral contracts (401/409/403/bad HMAC); non-official Java reference client
  (idempotency, webhook HMAC, retry, `traceparent`) — not published, no SemVer
  ([sdk-reference/](sdk-reference/), [INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md))
- API CLI UX (FL-153): process-memory session after `auth token`, silent remint on
  near-expiry/401 (in-box issuer only), global `--dry-run`, `health`/`ready` commands,
  `doctor` fails on unhealthy actuator; ADR-015 drops obsolete tenant header wording
  ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md) §9)
- Platform bootstrap (FL-158): one-shot `POST /api/v1/platform/bootstrap` +
  `platform:admin` JWT (no `tenant_id`); optional client-supplied tenant `id` on
  create; CLI `platform bootstrap`; ADR-016 addendum
  ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md) §3)
- Richer sandbox scenarios (FL-157): packs `simple`|`aggregator`|`remittance` via
  `FINLEDGER_SANDBOX_SCENARIO`, EcoPay/Send Tunnel demo labels, CLI `sandbox init`,
  optional mint `tenant_id` for any seeded sandbox tenant; persistent issuer rejects
  body `tenant_id` with 400 ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md))
- Persistent internal JWT issuer (FL-156): durable PKCS#8 signing key + tenant-bound
  `clients[]` for `normal`+`issuer=internal`; sandbox stays ephemeral; shared
  `InternalJwtIssuer` mint/JWKS surface ([auth-integration.md](docs/auth-integration.md),
  [ADR-016](docs/adr/ADR-016-runtime-profiles-jwt-issuer.md))
- Sandbox ephemeral JWT issuer + auth cleanup (FL-155): in-box RSA mint (`POST /api/v1/auth/token` + JWKS), max TTL, profiles `sandbox`|`normal` only, removed ADR-014 modes (`enforced`/`static-token`/`disabled`) and `local`/`prod`/`test` Spring profiles, CLI `auth token` + claim/BFF docs ([auth-integration.md](docs/auth-integration.md), [ADR-016](docs/adr/ADR-016-runtime-profiles-jwt-issuer.md))
- Developer integration guide rewrite (FL-155): copy-paste sandbox + normal paths, auth contract, ops CLI, production checklist ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md))
- Roadmap FL-158: platform:admin one-shot bootstrap for IdP-less normal cold-start (plan §14)
- Roadmap FL-157: richer sandbox scenario packs + `sandbox init` launcher (plan §14); keep `SandboxIds` as `simple` pack UUID contract
- Drop unused Redis hard dependency: rate cache stays in-memory; Compose/ITs need Postgres only
- Ops CLI + env template (FL-152): `finledger.env.example`, CLI `doctor`/`status`/`up`/`down`/`restart`/`logs` (Compose wrappers), config restart hints, `./bin/finledger-cli` launcher (REPL-first; auto-build in repo) ([ADR-015](docs/adr/ADR-015-operational-model.md))
- Runtime profiles & JWT issuer model (FL-154 docs): ADR-016 — `sandbox`/`normal`, always-on JWT, issuer `external`|`internal`, no trust_edge; tickets FL-155/156; FL-153 after auth ([ADR-016](docs/adr/ADR-016-runtime-profiles-jwt-issuer.md))
- Operational model (ADR-015): Compose-first eval (Blnk-style), dual-surface CLI (ops + api), developer integration outline ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md), finalized FL-190)
- Runnable security modes (FL-151): `enforced` / `static-token` / `disabled`, production interlock, sandbox Compose profile + credential dump, CLI `config init|set|validate`, shared `finledger-security-policy` module ([ADR-014](docs/adr/ADR-014-security-modes.md))
- Observability (FL-150): Micrometer Tracing + OpenTelemetry, optional OTLP export, JSON logs with trace MDC, `LedgerMetrics`, Compose `observability` profile + Grafana dashboard ([ADR-013](docs/adr/ADR-013-observability.md))
- CI/CD + Docker Hub (FL-140): multi-stage `Dockerfile`, Compose `with-app` profile, CI Docker build (no push), tag-triggered multi-arch Hub release + GitHub Release ([ADR-012](docs/adr/ADR-012-docker-distribution.md))
- Fraud module (FL-130): `TransactionRiskCheckPort`, in-box rules behind `FINLEDGER_FRAUD_ENABLED`, async HOLD on `TransactionPosted`, `/api/v1/tenants/{id}/fraud` config/decisions
- CLI provisioning module (FL-120): multi-module Maven (`finledger` + `finledger-cli`), Picocli/JLine HTTP client of `/api/v1`, no-tenant boot INFO hint
- Payment rails (FL-110): `RailAdapter` + manual clearing, PENDING→SETTLED journals via `RAIL_CLEARING`, reconciliation breaks, inbound HMAC settlement webhook
- Security (FL-100): OIDC Resource Server (RS256/ES256 allowlist), scopes `ledger:read|write|admin`, JWT `tenant_id` claim binding, `SecretsProvider` env default, TLS 1.3 edge contract
- Audit trail (FL-090): hash-chained `audit_log`, `@Auditable` AOP on mutating use cases, `traceparent` correlation, integrity verify API/job
- Balance types API (FL-080): expose `available` / `pending` / `held` via GET account and GET balance; create-account returns zeroed views
- Split engine + fees (FL-070): declarative `SplitRuleSet` / `SplitPlanEvaluator`, `FeeReversalPolicy` (`NO_REVERSE` / `PRO_RATA`), `POST …/splits` + `POST …/refunds`, V7 `tenant_split_rule_set` / `tenant_fee_config`
- FX providers (FL-060): `ExchangeRateProvider` chain (override → external+CB → cache), `tenant_fx_config` / `fx_rate_override`, rate freeze on journal, FX REST under `/fx`
- Multi-tenant hierarchy + Postgres RLS (FL-050): `tenant` / `tenant_ancestry`, FORCE RLS on ledger tables, `TenantContext` + `SET LOCAL`, `POST /api/v1/tenants`
- Transactional outbox (FL-040): `outbox_event` table, `TransactionPosted` via `OutboxWriter` in the same TX as journal post, scheduled poller + logging `EventPublisher`
- PostTransaction + API idempotency (FL-030): create account / post / get journal entry REST endpoints, `idempotency_record` store, optimistic-lock retry
- Persistence (FL-020): Flyway ledger schema, JPA adapters for accounts/journal/balances
- Domain core (FL-010): Money, LedgerAccount, JournalEntry, Posting, DoubleEntryValidator, BalanceCalculator
- Open-source project scaffolding (LICENSE, README, contributing docs, ADRs)
- GitHub Actions CI workflow for `develop`
- Layered configuration documentation

### Fixed

- RateLimitingFilter owns its Jackson `ObjectMapper` (Boot 4 does not expose a
  Jackson 2 bean) so the container image boots past filter wiring (FL-190)
- Optimistic-lock retries use a **new** DB transaction per attempt with backoff
  outside the TX; balance re-read re-validates overdraft before apply (FL-170 / §8.3)
- Outbox poller no longer runs fraud HOLD synchronously inside the claim TX —
  `AsyncFraudHandler` uses a virtual-thread executor (FL-170 / ADR-011)
- Compose `with-app` honors `.env` for issuer/env/signing-key path (no longer hardcodes
  `external`/`production`, which blocked IdP-less normal+internal boot)
- Unmapped HTTP paths return **404** `NOT_FOUND` instead of **500** (e.g. sandbox
  `/api/v1/platform/bootstrap`, `:8080/actuator/health`)
- Swagger/OpenAPI UI returns 401 under JWT resource server: permit `/swagger-ui/**` and
  `/v3/api-docs/**`; Bearer scheme in OpenAPI for Authorize (FL-156)
- Audit hash chain: truncate `occurredAt` to microseconds so integrity verify survives Postgres `TIMESTAMPTZ` round-trips (CI on Linux clocks)
- CLI shade plugin: override Boot parent merge so `./mvnw verify` can package the runnable jar

### Changed

- Integration guide: split host JVM vs Compose `with-app` paths for normal+internal
  (host `./config/...` vs container `/workspace/config/...`); health on `:8081`
  ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md))
- Integration guide rewritten as a standalone CLI-first loop (sandbox and normal share
  up → token → API); CLI prompts on TTY for secrets and missing tenant fields
  ([INTEGRATION_GUIDE.md](docs/INTEGRATION_GUIDE.md))
