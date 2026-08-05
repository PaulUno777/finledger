# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- Swagger/OpenAPI UI returns 401 under JWT resource server: permit `/swagger-ui/**` and
  `/v3/api-docs/**`; Bearer scheme in OpenAPI for Authorize (FL-156)
- Audit hash chain: truncate `occurredAt` to microseconds so integrity verify survives Postgres `TIMESTAMPTZ` round-trips (CI on Linux clocks)
- CLI shade plugin: override Boot parent merge so `./mvnw verify` can package the runnable jar

### Added

- Richer sandbox scenarios (FL-157): packs `simple`|`aggregator`|`remittance` via
  `FINLEDGER_SANDBOX_SCENARIO`, EcoPay/Send Tunnel demo labels, CLI `sandbox init`,
  optional mint `tenant_id` for any seeded sandbox tenant; persistent issuer rejects
  body `tenant_id` with 400 ([INTEGRATION_FOR_CTO.md](docs/INTEGRATION_FOR_CTO.md))
- Persistent internal JWT issuer (FL-156): durable PKCS#8 signing key + tenant-bound
  `clients[]` for `normal`+`issuer=internal`; sandbox stays ephemeral; shared
  `InternalJwtIssuer` mint/JWKS surface ([auth-integration.md](docs/auth-integration.md),
  [ADR-016](docs/adr/ADR-016-runtime-profiles-jwt-issuer.md))
- Sandbox ephemeral JWT issuer + auth cleanup (FL-155): in-box RSA mint (`POST /api/v1/auth/token` + JWKS), max TTL, profiles `sandbox`|`normal` only, removed ADR-014 modes (`enforced`/`static-token`/`disabled`) and `local`/`prod`/`test` Spring profiles, CLI `auth token` + claim/BFF docs ([auth-integration.md](docs/auth-integration.md), [ADR-016](docs/adr/ADR-016-runtime-profiles-jwt-issuer.md))
- Developer integration guide rewrite (FL-155): copy-paste sandbox + normal paths, auth contract, ops CLI, production checklist ([INTEGRATION_FOR_CTO.md](docs/INTEGRATION_FOR_CTO.md))
- Roadmap FL-158: platform:admin one-shot bootstrap for IdP-less normal cold-start (plan §14)
- Roadmap FL-157: richer sandbox scenario packs + `sandbox init` launcher (plan §14); keep `SandboxIds` as `simple` pack UUID contract
- Drop unused Redis hard dependency: rate cache stays in-memory; Compose/ITs need Postgres only
- Ops CLI + env template (FL-152): `finledger.env.example`, CLI `doctor`/`status`/`up`/`down`/`restart`/`logs` (Compose wrappers), config restart hints, `./bin/finledger-cli` launcher (REPL-first; auto-build in repo) ([ADR-015](docs/adr/ADR-015-operational-model.md))
- Runtime profiles & JWT issuer model (FL-154 docs): ADR-016 — `sandbox`/`normal`, always-on JWT, issuer `external`|`internal`, no trust_edge; tickets FL-155/156; FL-153 after auth ([ADR-016](docs/adr/ADR-016-runtime-profiles-jwt-issuer.md))
- Operational model (ADR-015): Compose-first eval (Blnk-style), dual-surface CLI (ops + api), CTO integration outline ([INTEGRATION_FOR_CTO.md](docs/INTEGRATION_FOR_CTO.md), finalized FL-190)
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
