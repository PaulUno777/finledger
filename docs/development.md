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
| FL-130 | Fraud module (optional) | `feature/FL-130-fraud-module` | done |
| FL-140 | CI/CD + Docker Hub | `feature/FL-140-cicd-docker` | done |
| FL-150 | Observability | `feature/FL-150-observability` | done |
| FL-151 | Runnable security modes (eval / CI / prod) | `feature/FL-151-security-modes` | done |
| FL-152 | Ops CLI + `finledger.env.example` (Compose wrappers, doctor, restart hints) | `feature/FL-152-ops-cli-env-example` | in progress |
| FL-154 | ADR-016 + docs/policy aliases (`sandbox`/`normal`, issuer model) | `feature/FL-154-adr-016-runtime-profiles` | in progress (docs) |
| FL-155 | Sandbox ephemeral JWT issuer + richer seed + dump UX | `feature/FL-155-sandbox-jwt-issuer` | pending |
| FL-156 | Internal issuer for normal/CI; remove eternal static-token Bearer | `feature/FL-156-internal-jwt-issuer` | pending |
| FL-153 | API CLI UX (tenant header, health/ready, dry-run, silent refresh) | `feature/FL-153-api-cli-ux` | pending (after FL-156) |
| FL-160 | Contract tests + in-repo `/sdk-reference/` (non-official) | `feature/FL-160-contracts-sdk-ref` | pending |
| FL-170 | Hardening | `feature/FL-170-hardening` | pending |
| FL-180 | Post-v1 official multi-lang SDKs (separate repos) | `feature/FL-180-official-sdks` | pending |
| FL-190 | Finalize CTO integration runbook | `feature/FL-190-cto-integration-guide` | pending |

Work proceeds **one ticket at a time** with human PR gates. After each merge, continue
manual/E2E validation for that ticket. **FL-190** (end of track) delivers the
copy-paste [INTEGRATION_FOR_CTO.md](INTEGRATION_FOR_CTO.md) instructions as if a
fintech CTO were integrating FinLedger into their stack. Ops model: [ADR-015](adr/ADR-015-operational-model.md).
Auth target: [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md).

**Gate note:** FL-154 docs may land while FL-152 is still open (docs-only waive). Do **not**
start FL-155/156 code until FL-152 is merged (or waived) and FL-154 docs are accepted.
FL-153 waits for the new auth model (FL-156).

## Phase gate checklist

1. Relevant tests green (`./mvnw test`, integration profile when applicable)
2. ArchUnit green
3. Atomic Conventional Commit(s) on the feature branch
4. Human merges PR into `develop`
5. Only then start the next ticket

## Maven commands

The repo is a multi-module reactor (`finledger` server + `finledger-cli` + `finledger-security-policy`).

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
