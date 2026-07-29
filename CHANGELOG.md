# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Transactional outbox (FL-040): `outbox_event` table, `TransactionPosted` via `OutboxWriter` in the same TX as journal post, scheduled poller + logging `EventPublisher`
- PostTransaction + API idempotency (FL-030): create account / post / get journal entry REST endpoints, `idempotency_record` store, optimistic-lock retry
- Persistence (FL-020): Flyway ledger schema, JPA adapters for accounts/journal/balances
- Domain core (FL-010): Money, LedgerAccount, JournalEntry, Posting, DoubleEntryValidator, BalanceCalculator
- Open-source project scaffolding (LICENSE, README, contributing docs, ADRs)
- GitHub Actions CI workflow for `develop`
- Layered configuration documentation
