# Auth integration — who issues the JWT?

FinLedger **never** turns AuthN off. Every `/api/v1/**` call (except mint/JWKS,
health, prometheus, and settlement webhooks) must present a **short-lived JWT**
the ledger can verify. The only question is **who signs it**.

Operator axes (ADR-016):

| Axis | Values |
|------|--------|
| Spring profile | `sandbox` \| `normal` |
| Issuer | `internal` \| `external` |

Design: [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md). End-to-end developer
guide: [INTEGRATION_FOR_CTO.md](INTEGRATION_FOR_CTO.md). Config: [configuration.md](configuration.md).

## Sandbox vs normal

| Environment | Who issues the JWT? | Who refreshes? | What the ledger app developer does |
|-------------|---------------------|----------------|------------------------------------|
| **Sandbox** | FinLedger in-box issuer (ephemeral RSA keys each boot) | Re-mint via `POST /api/v1/auth/token` or `./bin/finledger-cli auth token` | Copy curls from `config/sandbox-ready.txt` — **no IdP** |
| **Normal + external IdP** (default prod) | **Your IdP** (or BFF that obtains tokens from it) | **Your client / BFF / IdP** — **not** FinLedger CLI | Configure issuer/JWKS on FinLedger; put the **claim contract** below in tokens your stack already issues |
| **Normal + internal issuer** (IdP-less CI) | FinLedger in-box issuer with **durable** PKCS#8 key + tenant-bound clients | Client/CLI re-mint with that client's `client_id` / `client_secret` | Configure `FINLEDGER_INTERNAL_SIGNING_KEY_*` + `finledger.security.internal.clients[]` |

### Why mention CLI refresh at all?

Only for **sandbox** (and internal issuer): tokens must expire, so demos need an easy
re-mint. That is **not** production AuthN with an external IdP.

In **normal + external IdP**, set `FINLEDGER_TOKEN` / `--token` from your IdP/BFF.

### “I don’t want to handle ledger security”

You still need a JWT with the right claims — but you do **not** build AuthN inside FinLedger:

1. Point FinLedger at your existing IdP (`issuer-uri` / `jwk-set-uri`).
2. Ensure tokens your BFF/services already use include the **claim contract**.
3. Call `/api/v1/...` with `Authorization: Bearer <that JWT>`.

FinLedger only **verifies** (signature, alg allowlist, `exp`, max TTL, scopes,
`tenant_id` ↔ path). No trust_edge / auth-off / security “modes.”

## Claim contract

| Claim / header | Required | Notes |
|----------------|----------|-------|
| Signature alg | RS256 or ES256 | HS256 / `none` rejected |
| `iss` | Yes | Must match configured issuer |
| `exp` | Yes | Ledger also enforces `finledger.security.max-token-ttl` (default `15m`) |
| `tenant_id` | Yes on tenant-scoped routes | UUID must equal `/api/v1/tenants/{tenantId}/…` |
| Scope / authorities | Yes | `ledger:read`, `ledger:write`, and/or `ledger:admin` (Spring `SCOPE_*`) |

## BFF patterns (pick one)

1. **Pass-through:** BFF forwards the user’s IdP access token unchanged.
2. **Token exchange:** BFF obtains a ledger-audience token with FinLedger claims.
3. **Machine callers:** IdP client-credentials → short-lived JWT with `tenant_id`.

**Wrong:** BFF strips auth and calls FinLedger open — **forbidden** by ADR-016.

## What FinLedger configures vs what you put in tokens

| Side | Responsibility |
|------|----------------|
| **FinLedger** | Profile `normal`; `issuer=external`; `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` or JWKS; `max-token-ttl` |
| **Your IdP / BFF** | Sign JWT (RS256/ES256); set `iss`, `exp`, `tenant_id`, scopes |

Sandbox (`SPRING_PROFILES_ACTIVE=sandbox`): FinLedger sets `issuer=internal`, mints at
`POST /api/v1/auth/token`, publishes JWKS at `GET /api/v1/auth/jwks`. Ephemeral keys —
tokens are **not** valid against a normal deployment. Seed pack is selected with
`finledger.sandbox.scenario` / `FINLEDGER_SANDBOX_SCENARIO` (`simple` \| `aggregator` \|
`remittance`). Optional mint body field `tenant_id` stamps that claim when the tenant
already exists in the DB (unknown → `400 unknown_tenant`). Default claim is
`SandboxIds.TENANT_ID` when omitted.

**Normal + internal:** same mint/JWKS paths, but signing key and clients are durable and
**client-bound** — each `client_id` maps to exactly one `tenant_id` (least privilege). A
leaked secret cannot mint tokens for arbitrary tenants. Passing `tenant_id` in the mint
body is rejected with **`400 tenant_id_not_allowed`** (never silently ignored).

**Platform bootstrap (FL-158):** for empty-DB cold-start, set
`FINLEDGER_PLATFORM_BOOTSTRAP_SECRET`, call `POST /api/v1/platform/bootstrap` (or
`./bin/finledger-cli platform bootstrap`). Returns a short JWT with scope
`platform:admin` and **no** `tenant_id` claim. Use it once to
`POST /api/v1/tenants` with optional `id` matching your client binding. Second bootstrap
→ **410**. Blank secret → **404**. `platform:admin` does not authorize ledger
data-plane routes.

## CLI: when `auth token` applies

```bash
# Sandbox / internal issuer only
./bin/finledger-cli auth token --client-id sandbox --client-secret '<from dump>'
./bin/finledger-cli auth token --client-secret '<from dump>' --tenant-id '<seeded-uuid>'

# IdP-less normal cold-start
./bin/finledger-cli platform bootstrap --secret "$FINLEDGER_PLATFORM_BOOTSTRAP_SECRET"
./bin/finledger-cli tenant create --name Acme --type STANDALONE --id "$TENANT"

# Pick scenario before compose up (writes .env)
./bin/finledger-cli sandbox init --scenario aggregator

# Normal + external IdP
export FINLEDGER_TOKEN='…'   # from your IdP/BFF
```

## Quick sandbox path

```bash
cp finledger.env.example .env
./bin/finledger-cli sandbox init --scenario simple
docker compose --profile sandbox up -d --build
./bin/finledger-cli auth token --client-secret '<from dump>'
```

Sandbox **must not** boot when `FINLEDGER_ENV` is `production` / `prod`
(`RuntimeSecurityPolicy` interlock).
