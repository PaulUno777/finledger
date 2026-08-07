# ADR-016 — Runtime profiles and JWT issuer model

- **Status:** Accepted
- **Date:** 2026-08-03
- **Deciders:** FinLedger maintainers
- **Amends:** [ADR-008](ADR-008-oidc-resource-server.md), [ADR-014](ADR-014-security-modes.md),
  [ADR-015](ADR-015-operational-model.md)
- **Related delivery:** [ADR-012](ADR-012-docker-distribution.md)

## Context

ADR-014 introduced three peer security modes (`enforced` / `static-token` /
`disabled`) so OSS evaluators could `compose up` without an IdP. That unblocked
DX but left three problems:

1. Operators think in **runtime profiles** (eval sandbox vs real deployment), not
   three AuthN modes of equal weight.
2. `static-token` and sandbox-style shared Bearers are effectively **eternal API
   credentials** — contradicting “a token must never be eternal.”
3. A `trust_edge` / auth-off escape for “gateway already authenticated” reintroduces
   unverified trust (including in staging mistaken for non-prod).

We need a model where **application verification is always cryptographic**,
flexibility lives only in **who issues** the JWT, and delivery liability stays
clear (Hub image vs JAR escape hatch).

## Decision

### 1. Two runtime profiles (operator axis)

| Profile | Purpose |
|---------|---------|
| **`sandbox`** | Eval / local / CI with seeded tenant + accounts; Compose `--profile sandbox` |
| **`normal`** | Non-seeded deployment (local-with-IdP, staging, production) |

`sandbox` **must not boot** when `FINLEDGER_ENV=production` or active profiles
include `prod` (code interlock in `finledger-security-policy`, extended in FL-154+).

### 2. Always-on JWT verification (no exceptions)

Every `/api/v1/**` call (except documented public paths: health, prometheus,
HMAC settlement webhooks) requires a Bearer JWT that FinLedger verifies:

- Algorithms: **RS256 / ES256 only** (ADR-008 allowlist)
- Claims: `tenant_id`, scopes (`ledger:read|write|admin`) as today
- Mandatory `exp` (and standard clock skew)
- **Ledger-enforced max token lifetime** — a configurable ceiling (e.g. 15–60
  minutes) applied even if the issuer set a longer `exp`

**Forbidden permanently:**

- Auth-off / `permitAll` API
- `trust_edge` / “authenticated-looking passthrough”
- Accepting a long-lived shared secret as the API Bearer

### 3. Flexibility = who issues (`external` | `internal`)

| Issuer | When | Secrets |
|--------|------|---------|
| **`external`** | Default for real production — any OIDC IdP (Keycloak, Auth0, Cognito, Okta, …) | Operator JWKS / issuer-uri |
| **`internal`** | Sandbox always; normal/CI when no external IdP | In-box mint: `client_id` + `client_secret` → **short-lived** JWT |

Single verification stack for both issuers. Isolation: sandbox uses **ephemeral
signing keys regenerated each boot** and a sandbox-specific issuer URI so those
JWTs cannot validate against a `normal` deployment.

Long-lived material (`client_secret`, signing keys) is only for **credential
exchange / minting**, never sent as `Authorization: Bearer` for ledger API calls.
Clients (including `finledger-cli`) **refresh before expiry** (STS-style).

### 4. mTLS is additive transport only

mTLS (mesh / sidecar / reverse proxy) may strengthen peer identity. It **never
replaces** JWT verification. Optional later: verify client certificate SAN via a
port (non-presumption — no hard-wired mesh vendor).

### 5. Interservice preference (vendor-agnostic)

1. Short-lived JWT (client credentials / workload identity) on every call
2. mTLS **in addition to** JWT
3. HMAC request signing for async edges (rails webhooks — ADR-009)
4. Network ACL alone / eternal shared Bearer — **rejected**

### 6. Delivery & liability

| Artifact | Role | Liability |
|----------|------|-----------|
| `git clone` + Compose sandbox | Eval | Dev |
| Docker Hub `…/finledger:<semver>` | **Canonical production** | Full (CI, non-root, multi-arch) |
| Server fat JAR on GitHub Release | Escape hatch (already a Docker build intermediate) | **Weaker** — not the same guarantee bar as the image; document in CTO guide |

### 7. Compatibility with ADR-014 (one release)

Public names `enforced` / `static-token` / `disabled` become **deprecated aliases**
mapped during FL-154–FL-156:

| Legacy mode | Maps toward |
|-------------|-------------|
| `enforced` | `normal` + issuer `external` |
| `static-token` | `normal` + issuer `internal` (short-lived mint; **not** raw eternal Bearer) |
| `disabled` | **Removed** — no replacement; use `sandbox` + internal issuer JWTs |

## Alternatives considered

| Option | Why not |
|--------|---------|
| Keep three peer AuthN modes | Confuses operators; eternal static Bearer |
| Raw high-entropy sandbox Bearer (process lifetime) | Structurally eternal; separate verify path |
| `trust_edge` with prod-only interlock | Unverified trust; staging risk |
| mTLS instead of JWT | Substitutes transport for app AuthZ; vendor/mesh coupling |
| Drop Hub image / JAR | Image needed for prod liability; JAR is cheap escape hatch |
| Embed Keycloak | Violates non-presumption |

## Consequences

- Positive: one verify path and test surface; no eternal API Bearers; clear
  sandbox vs normal story; gateway/BFF still works via minted JWTs.
- Trade-off: sandbox and IdP-less CI need the in-box issuer (FL-155 / FL-156);
  CLI must learn token refresh (FL-153 after auth land).
  - **Implementation:** FL-154 (docs) → FL-155 (**sandbox ephemeral issuer + removal of
  ADR-014 modes and `local`/`prod`/`test` Spring profiles** — profile axis is only
  `sandbox`|`normal`) → FL-156 (**persistent internal issuer**: durable PEM +
  tenant-bound clients for normal/CI).
  See [auth-integration.md](../auth-integration.md).

## Explicitly not in the ADR/docs pass

- Embedding Keycloak
- Dropping Hub releases or the JAR escape hatch
- Rewriting the CLI in another language
- Richer sandbox scenario packs (FL-157)
- ~~IdP-less cold-start / `platform:admin` bootstrap (FL-158 — planned)~~ → see FL-158 addendum below

## FL-156 delivery note

Persistent internal issuer for `normal`/CI: durable PKCS#8 signing key +
**client-bound** `tenant_id` list (`finledger.security.internal.clients[]`). Sandbox
remains ephemeral keys + `SandboxIds`.

~~**Known gap (deferred to FL-158):**~~ Closed by FL-158 (see addendum).

## FL-158 addendum — platform bootstrap

IdP-less cold-start for `normal` + `issuer=internal`:

1. **Ceremony:** `POST /api/v1/platform/bootstrap` with body
   `{ "bootstrap_secret": "…" }` matching `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET`.
   Constant-time compare (SHA-256 digests + `MessageDigest.isEqual`). Blank secret →
   **404** (disabled). Wrong secret → **401**. Already claimed → **410 Gone**
   permanently (DB row in `platform_admin_credential`, checked at request time —
   correct across replicas/restarts).
2. **Returned JWT:** short TTL (capped by max token TTL), scope **`platform:admin`
   only**, **no `tenant_id` claim** (control-plane vs data-plane). Subject
   `platform-bootstrap`.
3. **AuthZ:** `platform:admin` may call `POST /api/v1/tenants` (and reserved
   `/api/v1/platform-admins/**`). It does **not** authorize tenant-scoped ledger
   routes. `TenantClaimAuthorizationFilter` skips create-tenant and `/platform/**`
   only.
4. **Create with id:** optional body field `id` (UUID) on `POST /tenants` when
   caller has `platform:admin`. `ledger:admin` passing `id` → **400**
   `id_not_allowed` (never silent ignore). Collision → **409** `TENANT_ID_CONFLICT`.
5. **Cold-start recipe:** set `FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_TENANT_ID` to a
   chosen UUID, claim bootstrap, `POST /tenants` with the same `id` — **no restart**.
6. **CLI:** `./bin/finledger-cli platform bootstrap --secret …`
7. **Production:** remains `issuer=external` + IdP `ledger:admin` for day-0; leave
   bootstrap secret unset.

## References

- `docs/PLAN_LEDGER_FINTECH.md` §11, §18, §19
- [auth-integration.md](../auth-integration.md)
- [ADR-008](ADR-008-oidc-resource-server.md), [ADR-014](ADR-014-security-modes.md) (superseded runtime),
  [ADR-015](ADR-015-operational-model.md), [ADR-012](ADR-012-docker-distribution.md)
- Tickets: FL-154 → FL-158; FL-153 after auth track
