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

Postgres + Redis:

```bash
docker compose up -d
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/realms/finledger
./mvnw -pl finledger spring-boot:run
```

Or run the server image (Compose profile `with-app`) — put the OIDC issuer in `.env` first:

```bash
docker compose --profile with-app up -d --build
```

Health: `GET http://localhost:8081/actuator/health` (container) or
`GET http://localhost:8080/actuator/health` (local `spring-boot:run`).

Published images (after a `v*.*.*` tag): `${DOCKERHUB_USERNAME}/finledger:<semver>`.

Provisioning CLI (separate module — see [ADR-010](docs/adr/ADR-010-cli-http-client-module.md)):

```bash
export FINLEDGER_TOKEN=<jwt>
./mvnw -pl finledger-cli exec:java -- tenant create --name Acme --type STANDALONE
```

## Configuration

Configuration is layered (Spring Boot conventions — no boot wizard):

1. Defaults in the application image / `application.yaml`
2. Optional external file (`SPRING_CONFIG_ADDITIONAL_LOCATION`)
3. Environment variables (orchestrators)
4. Secrets only via a `SecretsProvider` port (never hard-coded)

Details: [docs/configuration.md](docs/configuration.md)

## API

OpenAPI UI is available when the app is running (springdoc).

Posting a journal entry requires an `Idempotency-Key` header. Same key + same body
replays the stored response; same key + different body returns `409`.

```bash
# Create two accounts, then post a same-currency transfer
TENANT=00000000-0000-0000-0000-000000000001

curl -s -X POST "http://localhost:8080/api/v1/tenants/$TENANT/accounts" \
  -H 'Content-Type: application/json' \
  -d '{"ownerRef":"merchant-a","currencyCode":"USD","type":"MERCHANT_WALLET","allowsOverdraft":true}'

curl -s -X POST "http://localhost:8080/api/v1/tenants/$TENANT/journal-entries" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-transfer-1' \
  -d '{
    "transactionReference": "tx-1",
    "postings": [
      {"accountId":"<FROM_ID>","amount":"-10.00","currencyCode":"USD","settlementStatus":"SETTLED"},
      {"accountId":"<TO_ID>","amount":"10.00","currencyCode":"USD","settlementStatus":"SETTLED"}
    ]
  }'
```

FL-030 note: posting currency must match each account’s currency unless an FX
path is configured (FL-060). API routes require OIDC Bearer JWTs (FL-100).

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

**v0.x — early foundation.** Roadmap tickets and status live in
[docs/development.md](docs/development.md). Docker image contract:
[ADR-012](docs/adr/ADR-012-docker-distribution.md).

## License

Licensed under the [Apache License 2.0](LICENSE).
