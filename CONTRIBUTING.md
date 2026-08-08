# Contributing to FinLedger

Thanks for helping build a correct, auditable ledger core.

## Mission check

FinLedger is an ultra-secure accounting ledger for aggregator payment systems. It is
**not** a full payment platform, pricing engine, or multi-protocol integration hub.
Prefer hardening double-entry, idempotency, auditability, and tenant isolation over
expanding surface area.

## How to contribute (fork → PR)

**Do not ask for write access** to this repository. Only the maintainer merges into
`develop` and `main`.

1. **Fork** the repo and clone your fork.
2. Branch from up-to-date `develop`: `feature/FL-XXX-short-slug` or a descriptive
   `fix/…` / `docs/…` name.
3. Open a **pull request targeting `develop`** (never open feature PRs into `main`).
4. Wait for CI (`Maven verify`, `Docker build (no push)`). Fix failures on your branch.
5. The maintainer reviews and merges. You will not be able to merge into `develop` or
   `main` yourself.

Release flow (maintainer only): PR `develop` → `main`, then tag `v*.*.*` to publish
the Docker image and GitHub Release.

### Branch protection (enforced on GitHub)

| Branch | Protection | Required |
|--------|------------|----------|
| `develop` | No force-push / deletion; PRs only | CI green (`Maven verify`, `Docker build`) |
| `main` | No force-push / deletion; PRs only | CI green (`Maven verify`, `Docker build`) |

**Access model:** this is a personal repository — GitHub does not allow
“push restricted to user X” on personal repos. Effective control is:

1. **Do not grant Write/Admin** to anyone else (Settings → Collaborators).
2. External work lands via **fork + PR**; only accounts with Write can merge, and
   that should remain the maintainer alone.
3. Branch rules still block force-push and require a PR + CI before merge.

Tags (`v*.*.*`) and Hub publishes are maintainer-owned.

## Branch model

| Branch | Role |
|--------|------|
| `main` | Releases only — no direct feature commits |
| `develop` | Integration base for all new work |
| `feature/FL-XXX-short-slug` | One roadmap ticket / phase |

PRs target `develop`. Release PRs into `main` are human-owned (maintainer).

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
