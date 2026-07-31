# ADR-014 — Runnable security modes (eval / CI / prod)

- **Status:** Accepted
- **Date:** 2026-07-31
- **Deciders:** FinLedger maintainers
- **Amends:** [ADR-008](ADR-008-oidc-resource-server.md)

## Context

ADR-008 correctly rejected a silent `permitAll` escape hatch for production.
That left OSS evaluators unable to `compose up` and post a journal without an
IdP — health might start only if a `JwtDecoder` is present; the API was unusable
for a one-minute solo eval.

## Decision

1. Introduce `finledger.security.mode` with three values:
   - **`enforced`** (default) — OIDC/JWT resource server as in ADR-008
   - **`static-token`** — single shared Bearer (`FINLEDGER_STATIC_TOKEN`); full
     ledger scopes; tenant from header `X-FinLedger-Tenant-Id`
   - **`disabled`** — no AuthN; sandbox principal; path tenant must equal the
     well-known sandbox tenant UUID
2. **Production interlock (code, not docs):** boot fails if `mode != enforced`
   when `FINLEDGER_ENV=production` **or** active Spring profiles contain `prod`.
   Shared pure-JDK module `finledger-security-policy` implements the rule for
   the server guard and CLI `config validate`.
3. Compose profile **`sandbox`**: Postgres + Redis + app with
   `SPRING_PROFILES_ACTIVE=sandbox`, `mode=disabled`, never `prod`. Boot seeds
   a fixed sandbox tenant + two USD overdraft wallets and dumps copy-paste
   curls to the console and `config/sandbox-ready.txt` (gitignored).
4. CLI adds **local-only** `config init|set|validate` (YAML on disk). HTTP
   commands remain ADR-010.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Embedded mock IdP | Extra moving parts; still JWT ceremony for eval |
| README-only “don’t use permitAll in prod” | Insufficient — accidental misconfig |
| Keep ADR-008 as-is | Blocks OSS compose→curl DX |

## Consequences

- Positive: one-minute sandbox eval; CI can use `static-token`; production
  remains enforced-only with an automated gate.
- Trade-off: operators must never set `sandbox`/`disabled` alongside `prod`
  (the interlock aborts boot if they do).
- Follow-up: FL-160 contracts; optional Vault for static token storage.

## References

- `docs/PLAN_LEDGER_FINTECH.md` §11
- `SecurityModePolicy`, `SecurityModeGuard`, `EnforcedSecurityConfig`,
  `StaticTokenSecurityConfig`, `DisabledSecurityConfig`, `SandboxBootstrap`
