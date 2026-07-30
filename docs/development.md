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
| FL-070 | Split engine + fees | `feature/FL-070-split-engine` | done (pending merge) |
| FL-080 | Balance types | `feature/FL-080-balance-types` | pending |
| FL-090 | Audit trail | `feature/FL-090-audit-trail` | pending |
| FL-100 | Security (OIDC, TLS contract) | `feature/FL-100-security` | pending |
| FL-110 | Payment rails | `feature/FL-110-payment-rails` | pending |
| FL-120 | CLI module | `feature/FL-120-cli` | pending |
| FL-130 | Fraud module (optional) | `feature/FL-130-fraud-module` | pending |
| FL-140 | CI/CD + Docker Hub | `feature/FL-140-cicd-docker` | pending |
| FL-150 | Observability | `feature/FL-150-observability` | pending |
| FL-160 | Contract tests + sdk-reference | `feature/FL-160-contracts-sdk-ref` | pending |
| FL-170 | Hardening | `feature/FL-170-hardening` | pending |
| FL-180 | Post-v1 official SDKs | `feature/FL-180-official-sdks` | pending |

## Phase gate checklist

1. Relevant tests green (`./mvnw test`, integration profile when applicable)
2. ArchUnit green
3. Atomic Conventional Commit(s) on the feature branch
4. Human merges PR into `develop`
5. Only then start the next ticket

## Maven commands

```bash
./mvnw test          # unit + ArchUnit + smoke
./mvnw verify        # includes broader verification as profiles grow
```

Test tags (target convention):

- `@Tag("unit")` — fast, no external deps
- `@Tag("integration")` — Testcontainers / real Postgres
- `@Tag("architecture")` — ArchUnit
- `@Tag("e2e")` — release pipeline only
