# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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
