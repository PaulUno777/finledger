# FinLedger

[![CI](https://github.com/PaulUno777/finledger/actions/workflows/ci.yml/badge.svg)](https://github.com/PaulUno777/finledger/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green.svg)](https://spring.io/projects/spring-boot)

> Double-entry, multi-currency, multi-tenant ledger for payment aggregators.
> Self-hosted. No forced broker, IAM, secrets manager, or payment rail.

## Why FinLedger

Fintech startups need a correct accounting core more than another all-in-one payments
platform. FinLedger focuses on the hard part: append-only double-entry posting,
idempotency, multi-currency FX that freezes rates into the journal, hierarchical
tenancy, and an auditable hash-chained trail — as an open-source component you host
yourself.

## What this project does NOT assume

FinLedger follows a **non-presumption** principle. The core never hard-wires:

- a message broker (Kafka, RabbitMQ, …)
- an IAM / OIDC vendor
- a secrets manager
- a payment rail or PSP
- a fraud engine or fee/pricing DSL

Everything crosses a **port**; optional adapters plug in later. See the extension table in
[docs/PLAN_LEDGER_FINTECH.md](docs/PLAN_LEDGER_FINTECH.md) §2.3.

## Architecture

Hexagonal / Clean Architecture with dependencies pointing inward:

```text
adapter/in (REST, CLI, events)
        → application (use cases + ports)
                → domain (Money, JournalEntry, Posting — zero frameworks)
infrastructure implements ports (JPA, outbox, FX, security, …)
```

- [Architecture overview](docs/architecture.md)
- [ADRs](docs/adr/)
- [Full product plan](docs/PLAN_LEDGER_FINTECH.md)

## Quick start

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

Health: `GET http://localhost:8080/actuator/health`

## Configuration

Configuration is layered (Spring Boot conventions — no boot wizard):

1. Defaults in the application image / `application.yaml`
2. Optional external file (`SPRING_CONFIG_ADDITIONAL_LOCATION`)
3. Environment variables (orchestrators)
4. Secrets only via a `SecretsProvider` port (never hard-coded)

Details: [docs/configuration.md](docs/configuration.md)

## API

OpenAPI UI is available when the app is running (springdoc). Mutating endpoints will
require an `Idempotency-Key` header once the PostTransaction use case lands (roadmap
phase FL-030).

## Guarantees (target)

| Guarantee | Mechanism |
|-----------|-----------|
| Append-only ledger | Corrections via linked `reverse()` entries only |
| Double-entry | Postings sum to zero per pivot currency |
| Idempotency | API key + body hash; consumers dedupe in the same DB tx |
| Consistency | Transactional outbox — never publish outside the write tx |
| Tenancy | `tenant_id` (+ ancestry) with Postgres RLS |

## Extension points

New FX sources, brokers, rails, or fraud engines = new adapter behind an existing port.
Do not modify domain validators to “support” a vendor.

## Documentation

| Doc | Link |
|-----|------|
| Product & architecture plan | [docs/PLAN_LEDGER_FINTECH.md](docs/PLAN_LEDGER_FINTECH.md) |
| Architecture (short) | [docs/architecture.md](docs/architecture.md) |
| Configuration | [docs/configuration.md](docs/configuration.md) |
| Development & roadmap tickets | [docs/development.md](docs/development.md) |
| ADRs | [docs/adr/](docs/adr/) |
| Contributing | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Security policy | [SECURITY.md](SECURITY.md) |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |

## Status

**v0.x — early foundation.** Track A (docs/OSS/CI/config) is in progress. Domain core and
posting APIs follow the roadmap in [docs/development.md](docs/development.md).

Docker Hub image badges and GitHub Release badges will appear after CI/CD phase FL-140.

## License

Licensed under the [Apache License 2.0](LICENSE).
