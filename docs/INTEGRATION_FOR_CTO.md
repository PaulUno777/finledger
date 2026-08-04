# FinLedger — developer integration guide

Practical guide for engineering teams integrating FinLedger into a payments or
banking stack. Commands and env names match [configuration.md](configuration.md)
and [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md).

| Audience | Start here |
|----------|------------|
| Backend / platform engineer | §§1–5 (quickstart → first API call → auth) |
| DevOps / SRE | §§6–7 (config, deploy, ops) |
| Security review | §5 + [auth-integration.md](auth-integration.md) |
| Product / architecture | §8 + [PLAN_LEDGER_FINTECH.md](PLAN_LEDGER_FINTECH.md) |

---

## 1. What you are integrating

FinLedger is a **self-hosted multi-tenant double-entry ledger**. You call its REST
API; it owns journal integrity, idempotency, tenant isolation (Postgres RLS), and
an append-only audit trail.

| FinLedger owns | Your stack owns |
|----------------|-----------------|
| Journal, accounts, balances (projections) | Customer UX, KYC, card/PSP rails |
| Idempotency + transactional outbox | Event consumers / brokers (optional adapters) |
| JWT **verification** (always on) | JWT **issuance** in production (your IdP / BFF) |
| Hash-chained `audit_log` | Log aggregation / SIEM |
| Optional FX / splits / rails / fraud behind ports | Concrete vendors (non-presumption — plan §2.3) |

**Canonical production artifact:** Docker Hub image `${DOCKERHUB_USERNAME}/finledger:<semver>`.
The server fat JAR on GitHub Releases is an escape hatch with weaker liability.

---

## 2. Mental model (two knobs only)

```text
SPRING_PROFILES_ACTIVE = sandbox | normal
finledger.security.issuer = internal | external
```

| Profile | When to use | Issuer default | Seeded data |
|---------|-------------|----------------|-------------|
| **`sandbox`** | Local eval, demos, onboarding | `internal` (ephemeral keys each boot) | Yes — `simple` pack: fixed tenant + two USD wallets (`SandboxIds`) |
| **`normal`** | Staging / production / IDE against real IdP | `external` (your OIDC JWKS) | No — you create tenants |

Richer demo packs (aggregator hierarchy, remittance / multi-currency, branded labels)
are planned as **FL-157** — see plan §14 / [development.md](development.md). Until then,
seed stays the minimal `simple` pack so JWT `tenant_id` and dump curls stay stable.

JWT verification is **always on**. There is no `disabled` / `trust_edge` / auth-off mode.

**Hard rule:** never set profile `sandbox` when `FINLEDGER_ENV=production` (or `prod`).
Boot fails on purpose.

Deep dive on claims and BFF patterns: [auth-integration.md](auth-integration.md).

---

## 3. Quickstart — sandbox (one sitting)

Requires Docker. From the repo root:

```bash
git clone https://github.com/PaulUno777/finledger.git
cd finledger
cp finledger.env.example .env

# Build + start Postgres + sandbox app
./bin/finledger-cli up --profile sandbox --build
# equivalent: docker compose --profile sandbox up -d --build

./bin/finledger-cli doctor
./bin/finledger-cli status
```

Wait until health is up:

```bash
curl -s http://localhost:8081/actuator/health
# {"status":"UP",...}
```

### 3.1 Mint a short-lived JWT

Credentials are printed at boot and written to `config/sandbox-ready.txt` (gitignored):

```bash
# Prefer the dump — secret is generated if you left FINLEDGER_SANDBOX_CLIENT_SECRET blank
CLIENT_SECRET=$(awk -F= '/^clientSecret=/{print $2}' config/sandbox-ready.txt)

TOKEN=$(./bin/finledger-cli auth token \
  --client-id sandbox \
  --client-secret "$CLIENT_SECRET")
echo "$TOKEN" | cut -c1-40   # should look like eyJ...
```

Or raw HTTP:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d "{\"grant_type\":\"client_credentials\",\"client_id\":\"sandbox\",\"client_secret\":\"$CLIENT_SECRET\"}"
```

Unauthenticated API calls must fail:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST http://localhost:8080/api/v1/tenants/00000000-0000-0000-0000-000000000001/journal-entries \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: no-auth' \
  -d '{}'
# expect 401
```

### 3.2 Post a journal entry

Seeded IDs (stable across boots):

| Resource | UUID |
|----------|------|
| Tenant | `00000000-0000-0000-0000-000000000001` |
| From account | `00000000-0000-0000-0000-000000000010` |
| To account | `00000000-0000-0000-0000-000000000011` |

```bash
TENANT=00000000-0000-0000-0000-000000000001
FROM=00000000-0000-0000-0000-000000000010
TO=00000000-0000-0000-0000-000000000011

curl -s -X POST "http://localhost:8080/api/v1/tenants/$TENANT/journal-entries" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: demo-$(uuidgen)" \
  -d "{
    \"transactionReference\": \"demo-tx-1\",
    \"postings\": [
      {\"accountId\":\"$FROM\",\"amount\":\"-10.00\",\"currencyCode\":\"USD\",\"settlementStatus\":\"SETTLED\"},
      {\"accountId\":\"$TO\",\"amount\":\"10.00\",\"currencyCode\":\"USD\",\"settlementStatus\":\"SETTLED\"}
    ]
  }"
# expect 201 + journalEntryId
```

Replay the **same** `Idempotency-Key` + same body → same response. Same key + different body → `409`.

OpenAPI UI (dev): http://localhost:8080/swagger-ui.html

Stop:

```bash
./bin/finledger-cli down   # keeps Postgres volume; never use down -v unless wiping
```

---

## 4. Quickstart — normal (developer / staging)

`normal` is the real deployment profile: **no seed**, JWT from your IdP by default.

### 4.1 Local Postgres + app (external IdP)

```bash
docker compose up -d          # Postgres only
export SPRING_PROFILES_ACTIVE=normal
export FINLEDGER_ENV=local
export FINLEDGER_SECURITY_ISSUER=external
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/realms/finledger
# or: SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=https://.../certs

./mvnw -pl finledger spring-boot:run
```

Compose image path (`with-app`) expects the same OIDC vars in `.env`:

```bash
# .env
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/realms/finledger

docker compose --profile with-app up -d --build
```

### 4.2 IdP-less local `normal` (in-box issuer)

Useful for CI / local without Keycloak. **No sandbox seed** — create tenants yourself.
Each mint client is **bound to one `tenant_id`** (FL-156).

```bash
# PKCS#8 signing key (once)
mkdir -p config
openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -out config/internal-signing.pem

docker compose up -d
export SPRING_PROFILES_ACTIVE=normal
export FINLEDGER_ENV=local
export FINLEDGER_SECURITY_ISSUER=internal
export FINLEDGER_INTERNAL_SIGNING_KEY_PATH=./config/internal-signing.pem
export FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_ID=ci
export FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_SECRET=dev-only-secret
# Must match a tenant you create (or seed in tests):
export FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_TENANT_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa

./mvnw -pl finledger spring-boot:run
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"grant_type":"client_credentials","client_id":"ci","client_secret":"dev-only-secret"}' \
  | jq -r .access_token)

# JWT tenant_id claim == bound client tenant — call that tenant's APIs
curl -s http://localhost:8080/api/v1/tenants/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/audit/integrity \
  -H "Authorization: Bearer $TOKEN"
```

For production, prefer **`issuer=external`** and your company IdP — see §5.
---

## 5. Auth contract (what your team must implement)

FinLedger only **verifies** JWTs. Your IdP or BFF must **issue** them with:

| Requirement | Detail |
|-------------|--------|
| Algorithms | `RS256` or `ES256` only (`none` / `HS256` rejected) |
| `iss` | Matches configured issuer |
| `exp` | Required; ledger also caps lifetime (`finledger.security.max-token-ttl`, default `15m`) |
| `tenant_id` | UUID must equal path `/api/v1/tenants/{tenantId}/…` |
| Scopes | `ledger:read`, `ledger:write`, and/or `ledger:admin` |

**BFF patterns (allowed):**

1. **Pass-through** — forward the user’s IdP access token (already has claims).
2. **Token exchange** — BFF obtains a ledger-audience token, then calls FinLedger.
3. **Machine clients** — client-credentials → short-lived JWT with `tenant_id`.

**Forbidden:** BFF strips auth and calls FinLedger open.

CLI `./bin/finledger-cli auth token` is **sandbox / internal issuer only**. For
`normal` + external IdP, export `FINLEDGER_TOKEN` from your IdP/BFF.

Full matrix and examples: [auth-integration.md](auth-integration.md).

---

## 6. Configuration cheat sheet

Layers (last wins): embedded YAML → optional `./config/` file → environment variables.

| Variable | Typical value |
|----------|----------------|
| `SPRING_PROFILES_ACTIVE` | `sandbox` or `normal` |
| `FINLEDGER_ENV` | `local` or `production` |
| `FINLEDGER_SECURITY_ISSUER` | `internal` or `external` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | IdP issuer (external) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Postgres (app role must **not** be superuser — RLS) |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | Often DB superuser for migrations only |
| `MANAGEMENT_SERVER_PORT` | `8081` (health / prometheus) |

Full table: [configuration.md](configuration.md). Template: [`finledger.env.example`](../finledger.env.example).

```bash
./bin/finledger-cli config init --profile normal
./bin/finledger-cli config validate
./bin/finledger-cli restart --service app   # after YAML changes under Compose
```

---

## 7. Production checklist

1. Deploy Hub image `${DOCKERHUB_USERNAME}/finledger:<semver>` (not an ad-hoc JAR).
2. `SPRING_PROFILES_ACTIVE=normal`, `FINLEDGER_ENV=production`, `issuer=external`.
3. Configure OIDC issuer/JWKS; enforce claim contract (§5).
4. Terminate TLS 1.3 at the edge; keep `:8081` off the public internet.
5. Provision tenants (`POST /api/v1/tenants` or CLI) with `ledger:admin`.
6. Every mutation sends `Idempotency-Key`.
7. Scrape Prometheus; optional OTLP (`OTEL_EXPORTER_OTLP_ENDPOINT`).
8. Secrets via env / `SecretsProvider` — never bake into the image.
9. Disable Swagger in prod (`springdoc.*.enabled=false`).
10. Design for outbox lag, idempotent retries, and token refresh before `exp`.

Architecture sketch:

```text
Your API / BFF / workers
        │  HTTPS + Idempotency-Key + Bearer JWT
        ▼
FinLedger (image)
        │  JDBC (RLS)
        ▼
Your Postgres
```

---

## 8. Failure modes to design for

| Situation | Expected behavior |
|-----------|-------------------|
| Same idempotency key + same body | Replay stored response |
| Same key + different body | `409 Conflict` |
| JWT missing / bad signature / wrong alg | `401` |
| `tenant_id` ≠ path tenant | `403` `TENANT_CLAIM_MISMATCH` |
| Token past `exp` or over max TTL | `401` |
| Sandbox + `FINLEDGER_ENV=production` | **Boot failure** |
| Outbox consumer down | Events stay in `outbox_event` until drained |
| Cross-currency without FX op | Rejected by domain rules |

---

## 9. Day-2 ops CLI

```bash
./bin/finledger-cli                 # REPL
./bin/finledger-cli doctor
./bin/finledger-cli status
./bin/finledger-cli logs -f --service app-sandbox
./bin/finledger-cli restart --service app-sandbox
./bin/finledger-cli down            # preserves Postgres data
```

---

## 10. Doc map

| Topic | Doc |
|-------|-----|
| Auth claims / BFF | [auth-integration.md](auth-integration.md) |
| Env & profiles | [configuration.md](configuration.md) |
| Runtime decision | [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md) |
| Ops model | [ADR-015](adr/ADR-015-operational-model.md) |
| Docker image | [ADR-012](adr/ADR-012-docker-distribution.md) |
| Product rules | [PLAN_LEDGER_FINTECH.md](PLAN_LEDGER_FINTECH.md) |
| Roadmap tickets | [development.md](development.md) |

---

*Living document — keep commands aligned with `configuration.md`. Copy-paste K8s
snippets and go-live runbook are completed in FL-190.*
