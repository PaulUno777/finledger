# ADR-008 — OIDC Resource Server with JWT allowlist and tenant claim binding

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Plan §11 requires AuthN/AuthZ via a generic OIDC resource server (no vendor SDK),
a strict JWT algorithm allowlist (RS256/ES256), scopes checked with tenant
affinity, secrets behind a port, and TLS 1.3 at the deployment edge. The API was
temporarily open until this phase (`permitAll` + disabled resource server).

## Decision

1. Spring Security OAuth2 Resource Server with Bearer JWTs. Configure via
   `spring.security.oauth2.resourceserver.jwt.issuer-uri` or `jwk-set-uri`
   (any OIDC IdP — Keycloak, Auth0, Cognito, Okta).
2. Wrap the decoder with `AlgorithmAllowlistingJwtDecoder` so `alg=none`, HS*,
   and other algorithms are rejected before signature verification.
3. Scopes: `ledger:read`, `ledger:write`, `ledger:admin` (Spring `SCOPE_*`).
   `POST /api/v1/tenants` requires admin; GET `/api/**` needs read|write|admin;
   other `/api/**` mutations need write|admin. Only `/actuator/health/**` is public.
4. `TenantClaimAuthorizationFilter` requires JWT claim `tenant_id` to equal the
   path tenant UUID for tenant-scoped routes.
5. `SecretsProvider` + `EnvSecretsProvider` (warns at startup; Vault/KMS later).
6. TLS 1.3 is a **deployment contract** at the reverse proxy / load balancer —
   not embedded Spring SSL in this phase.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Keycloak adapter / vendor SDK | Violates non-presumption (§2.3) |
| Symmetric JWT (HS256) for “simpler local” | Forbidden for public clients (§11) |
| `permitAll` local escape hatch | Weakens the default posture |
| Column encryption / webhook HMAC / rate limit | Separate §11 items; defer (rails/hardening) |

## Consequences

- Positive: API is authenticated; tenant path cannot be spoofed with another
  tenant’s token; algorithm downgrade attacks blocked.
- Trade-off: local runs require a real issuer/JWKS (or test JwtDecoder in tests).
- **Amended by ADR-014:** eval/CI may use `static-token` or `disabled` modes;
  production remains `enforced` only (code interlock).
- Follow-up: sensitive-read audit; Bucket4j; webhook HMAC with rails (FL-110);
  optional Vault secrets adapter.

## References

- `docs/PLAN_LEDGER_FINTECH.md` §11, §19 item 10
- `EnforcedSecurityConfig`, `AlgorithmAllowlistingJwtDecoder`, `TenantClaimAuthorizationFilter`
- [ADR-014](ADR-014-security-modes.md) — runnable security modes
