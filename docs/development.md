# Development guide

## Sources of truth

- Product / architecture plan: [PLAN_LEDGER_FINTECH.md](PLAN_LEDGER_FINTECH.md) (roadmap §19)
- ADRs: [adr/](adr/)
- Engineering rules: `.cursor/rules/architecture.mdc`
- Workflow gates: `.cursor/rules/project-workflow.mdc`

## Branch model

- `main` — releases only
- `develop` — integration base
- `feature/FL-XXX-short-slug` — one ticket / phase from `develop`

PRs are human-owned (agents do not open them unless asked).

## Ticket / branch map

### Track A — setup (early)

| Ticket | Branch | Scope |
|--------|--------|-------|
| FL-000 | `feature/FL-000-oss-bootstrap` | OSS docs, LICENSE, cleanup |
| FL-001 | (same Track A branch / commit) | CI skeleton |
| FL-002 | (same Track A branch / commit) | Config layers |

### Track B — product roadmap (plan §19)

| Ticket | Phase | Branch slug | Status |
|--------|-------|-------------|--------|
| FL-010 | Domain core | `feature/FL-010-domain-core` | done |
| FL-020 | Persistence + Flyway | `feature/FL-020-persistence` | done |
| FL-030 | PostTransaction + API idempotency | `feature/FL-030-post-transaction` | done |
| FL-040 | Transactional outbox | `feature/FL-040-outbox` | done |
| FL-050 | Multi-tenant + RLS | `feature/FL-050-multi-tenant-rls` | done |
| FL-060 | FX providers | `feature/FL-060-fx-providers` | done |
| FL-070 | Split engine + fees | `feature/FL-070-split-engine` | done |
| FL-080 | Balance types | `feature/FL-080-balance-types` | done |
| FL-090 | Audit trail | `feature/FL-090-audit-trail` | done |
| FL-100 | Security (OIDC, TLS contract) | `feature/FL-100-security` | done |
| FL-110 | Payment rails | `feature/FL-110-payment-rails` | done |
| FL-120 | CLI module | `feature/FL-120-cli` | done |
| FL-130 | Fraud module (optional) | `feature/FL-130-fraud-module` | pending |
| FL-140 | CI/CD + Docker Hub | `feature/FL-140-cicd-docker` | pending |
| FL-150 | Observability | `feature/FL-150-observability` | pending |
| FL-160 | Contract tests + in-repo `/sdk-reference/` (non-official) | `feature/FL-160-contracts-sdk-ref` | pending |
| FL-170 | Hardening | `feature/FL-170-hardening` | pending |
| FL-180 | Post-v1 official multi-lang SDKs (separate repos) | `feature/FL-180-official-sdks` | pending |

## Phase gate checklist

1. Relevant tests green (`./mvnw test`, integration profile when applicable)
2. ArchUnit green
3. Atomic Conventional Commit(s) on the feature branch
4. Human merges PR into `develop`
5. Only then start the next ticket

## Maven commands

The repo is a multi-module reactor (`finledger` server + `finledger-cli`).

```bash
./mvnw test                         # all modules: unit + ArchUnit + smoke
./mvnw -pl finledger test           # server only
./mvnw -pl finledger-cli test       # CLI only
./mvnw -pl finledger spring-boot:run
./mvnw -pl finledger-cli package    # shaded runnable CLI jar
./mvnw verify                       # broader verification as profiles grow
```

### CLI (FL-120)

```bash
export FINLEDGER_BASE_URL=http://localhost:8080   # optional; this is the default
export FINLEDGER_TOKEN=<jwt-with-ledger:admin-or-write>

./mvnw -pl finledger-cli exec:java -- \
  tenant create --name Acme --type STANDALONE

# or after package:
java -jar finledger-cli/target/finledger-cli-0.0.1-SNAPSHOT.jar shell
```

Commands: `tenant create`, `account create`, `fx config`, `split-rules put`, `shell`.

Test tags (target convention):

- `@Tag("unit")` — fast, no external deps
- `@Tag("integration")` — Testcontainers / real Postgres
- `@Tag("architecture")` — ArchUnit
- `@Tag("e2e")` — release pipeline only
