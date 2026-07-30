# Contributing to FinLedger

Thanks for helping build a correct, auditable ledger core.

## Mission check

FinLedger is an ultra-secure accounting ledger for aggregator payment systems. It is
**not** a full payment platform, pricing engine, or multi-protocol integration hub.
Prefer hardening double-entry, idempotency, auditability, and tenant isolation over
expanding surface area.

## Branch model

| Branch | Role |
|--------|------|
| `main` | Releases only — no direct feature commits |
| `develop` | Integration base for all new work |
| `feature/FL-XXX-short-slug` | One roadmap ticket / phase |

PRs target `develop`. Release PRs into `main` are human-owned.

## Phase gates

Work proceeds **one phase at a time** (see [docs/development.md](docs/development.md)).
After a phase PR merges to `develop`, wait for confirmation before starting the next.

Before declaring a phase done:

1. Unit / property tests for domain rules touched
2. ArchUnit still green (`domain` stays framework-free)
3. Integration tests when the phase touches persistence or messaging
4. Boot smoke test still passes

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:`, `fix:`, `chore:`, `test:`, `docs:`, `ci:`
- One commit = one atomic concern

## Local setup

The repo is a **multi-module Maven reactor** (`finledger` server + `finledger-cli`).

```bash
docker compose up -d
./mvnw -B test          # all modules from the reactor root
./mvnw -B verify
./mvnw -pl finledger spring-boot:run
```

Java 21 is required. See [docs/development.md](docs/development.md) for module-scoped commands.

## Architecture rules

- Dependencies point inward: adapters → application → domain
- Never put Spring / JPA / Kafka types in `domain`
- Business rules live in `domain` or `application`, not controllers
- Vendor SDKs stay behind ports; optional adapters are preferred for third-party tools

Structural decisions need an ADR under `docs/adr/` and/or an update to
`docs/PLAN_LEDGER_FINTECH.md` §21.

## Code of conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Security

See [SECURITY.md](SECURITY.md). Never commit secrets.
