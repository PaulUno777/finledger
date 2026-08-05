# ADR-015 — Operational model (Compose-first, dual-surface CLI)

- **Status:** Accepted
- **Date:** 2026-07-31
- **Deciders:** FinLedger maintainers
- **Amends:** [ADR-010](ADR-010-cli-http-client-module.md), [ADR-012](ADR-012-docker-distribution.md)

## Context

Evaluators and production CTOs need a clear ops story: how to install, configure,
restart without losing data, and administer the ledger over SSH without relying on
Swagger. Industry practice (Blnk, Kafka, Redis, Temporal, Vault, Keycloak) separates
**process lifecycle** (Compose / systemd / K8s) from **admin clients** (CLI talking to
a running server). FL-151 added runnable security modes and sandbox Compose; E2E
review showed gaps (`.env.example`, CLI tenant header for `static-token`, restart
hints, Docker Boot 4 entrypoint).

## Decision

1. **Primary install path (eval):** `git clone` → copy `finledger.env.example` →
   `docker compose --profile sandbox up -d --build` (Blnk-style). No IdP required.
2. **Production path:** official Docker image + Compose `with-app` (or K8s) with
   `finledger.security.mode=enforced` and operator-provided OIDC. JAR remains a
   secondary artifact for non-container hosts.
3. **Configuration:** keep Spring layered resolution (defaults → optional
   `/workspace/config` → env). Ship **`finledger.env.example`** documenting every
   public option. Secrets never in committed files (`SecretsProvider` only).
4. **CLI has two surfaces in one shaded jar** (still no Spring in `finledger-cli`):
   - **ops** (local / SSH next to Compose): `config init|set|validate`, `status`,
     `doctor`, wrappers for `compose up|down|restart|logs`, restart guidance after
     config changes. Lifecycle = Docker Compose / orchestrator, not an embedded
     process supervisor.
   - **api** (HTTP to running server): existing provisioning commands + health/ready;
     Bearer / static-token + `X-FinLedger-Tenant-Id` when required.
5. **Swagger / OpenAPI:** first-class for local/dev and as API documentation;
   disabled or restricted under `prod`. SSH/ops UX is the CLI.
6. **Final deliverable:** a CTO integration guide
   ([INTEGRATION_GUIDE.md](../INTEGRATION_GUIDE.md)) finalized after step-by-step
   validation of remaining roadmap items.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Embed Picocli server start inside the Boot app as primary | Couples ops to JVM; fights K8s/Compose (ADR-010) |
| Custom process supervisor instead of Compose | Reinvents Docker; poor K8s fit |
| Interactive wizard at JVM boot | Blocks health checks (plan §18.1) |
| Replace Swagger entirely with CLI | Hurts DX and contract discovery; restrict in prod only |
| Keycloak-style single binary as server+CLI primary | High packaging cost; optional later if demand appears |

## Consequences

- Positive: one-minute eval; clear prod path; CLI stays lean and secure; volumes
  preserve data across `compose restart`.
- Trade-off: ops commands require Docker (or documented JAR+systemd escape hatch).
- Follow-up tickets: FL-152 (ops CLI + env.example), FL-153 (api CLI tenant header /
  health / UX), FL-190 (finalize CTO guide).
- **Amended by [ADR-016](ADR-016-runtime-profiles-jwt-issuer.md):** sandbox vs
  normal profiles; always-on JWT (external or internal issuer); Hub image remains
  canonical production; JAR escape hatch with weaker liability; FL-153 follows
  FL-155/156 auth land.

## References

- `docs/PLAN_LEDGER_FINTECH.md` §16, §18, §19
- ADR-010, ADR-012, ADR-014, [ADR-016](ADR-016-runtime-profiles-jwt-issuer.md)
