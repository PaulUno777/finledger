# FinLedger — developer integration guide

Self-contained guide to run FinLedger locally and call the API. You only need Docker,
the repo CLI (`./bin/finledger-cli`), and a terminal. **One loop for every path:**

```text
up → get a TOKEN → call the API with that TOKEN
```

The CLI prompts on a TTY for secrets and missing fields. In CI/pipes, pass flags or env.

---

## 1. What you get

FinLedger is a **self-hosted multi-tenant double-entry ledger**. You call its REST API;
it owns journals, idempotency, tenant isolation (Postgres RLS), and a hash-chained audit
trail. Your stack owns UX, KYC, PSP rails, and (in production) JWT **issuance**.

| FinLedger | You |
|-----------|-----|
| Verify every JWT (always on) | Issue JWTs in production (IdP / BFF) |
| Journal + balances + outbox | Consume events (optional adapters) |
| Docker Hub image for production | Configure env / secrets at the edge |

**Hard rule:** never run profile `sandbox` with `FINLEDGER_ENV=production` (or `prod`).
Boot refuses.

---

## 2. Two knobs (same CLI either way)

| Knob | Values | Meaning |
|------|--------|---------|
| Profile | `sandbox` \| `normal` | Eval seed vs empty DB |
| Issuer | `internal` \| `external` | Mint JWT inside FinLedger vs your IdP |

| Goal | Profile | Issuer | How you get `TOKEN` |
|------|---------|--------|---------------------|
| Local demo (recommended first) | `sandbox` | `internal` (forced) | `./bin/finledger-cli auth token` |
| Local without IdP | `normal` | `internal` | `platform bootstrap` once → `tenant create` → `auth token` |
| Staging / production | `normal` | `external` | Export JWT from your IdP/BFF → `FINLEDGER_TOKEN` |

Shared after you have a token:

```bash
export FINLEDGER_TOKEN='…'   # or paste into Swagger Authorize
# then the same CLI / curl style for tenants, accounts, journals
```

---

## 3. Shared loop (CLI-first)

### 3.1 Start the stack

```bash
git clone https://github.com/PaulUno777/finledger.git
cd finledger
cp finledger.env.example .env
```

**Sandbox (seeded demo):**

```bash
./bin/finledger-cli sandbox init          # TTY: pick simple / aggregator / remittance
./bin/finledger-cli up --profile sandbox --build
./bin/finledger-cli doctor
./bin/finledger-cli status
```

**Normal + in-box issuer (no IdP):** set in `.env` (or export), then start Postgres + app:

```bash
# .env (IdP-less local)
SPRING_PROFILES_ACTIVE=normal
FINLEDGER_ENV=local
FINLEDGER_SECURITY_ISSUER=internal
FINLEDGER_INTERNAL_SIGNING_KEY_PATH=./config/internal-signing.pem
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_ID=ci
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_SECRET=   # or leave and use auth token prompt later
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_TENANT_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
FINLEDGER_PLATFORM_BOOTSTRAP_SECRET=                   # set a high-entropy value once

mkdir -p config
test -f config/internal-signing.pem || \
  openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -out config/internal-signing.pem

docker compose up -d
./mvnw -pl finledger spring-boot:run
# or: docker compose --profile with-app up -d --build  (after filling the same vars)
```

**Normal + your IdP:** same as above with `FINLEDGER_SECURITY_ISSUER=external` and
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/...` — skip
§3.2 bootstrap; put an IdP token in `FINLEDGER_TOKEN` and jump to §3.3.

Health:

```bash
curl -s http://localhost:8081/actuator/health
# {"status":"UP",...}
```

### 3.2 Get a TOKEN (interactive)

**Sandbox** — secret is in `config/sandbox-ready.txt` after boot. On a TTY the CLI can
read it or prompt:

```bash
TOKEN=$(./bin/finledger-cli auth token)
# prompts: Client secret (or auto-reads dump); optional Tenant id
export FINLEDGER_TOKEN="$TOKEN"
```

Flags still work for scripts:

```bash
./bin/finledger-cli auth token --client-secret '…' --tenant-id '…'
```

**Normal + internal (cold start, empty DB)** — same CLI shape, one extra step once:

```bash
# 1) One-shot platform:admin JWT (prompts for bootstrap secret; second call → 410)
export FINLEDGER_TOKEN=$(./bin/finledger-cli platform bootstrap)

# 2) Create first tenant — use the SAME uuid as FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_TENANT_ID
./bin/finledger-cli tenant create
# prompts: name, type, optional tenant id

# 3) Mint a merchant token (same command as sandbox)
export FINLEDGER_CLIENT_ID=ci   # if needed
TOKEN=$(./bin/finledger-cli auth token --client-id ci)
# prompts for client secret
export FINLEDGER_TOKEN="$TOKEN"
```

**Normal + external IdP:**

```bash
export FINLEDGER_TOKEN='eyJ…'   # from your IdP / BFF — do not use auth token
```

Unauthenticated calls must fail with `401`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST http://localhost:8080/api/v1/tenants/00000000-0000-0000-0000-000000000001/journal-entries \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: no-auth' -d '{}'
```

### 3.3 Call the API (same for sandbox and normal)

OpenAPI (dev): http://localhost:8080/swagger-ui.html — click **Authorize**, paste Bearer.

**Sandbox `simple` pack** (stable IDs):

| Resource | UUID |
|----------|------|
| Tenant (EcoPay) | `00000000-0000-0000-0000-000000000001` |
| From account | `00000000-0000-0000-0000-000000000010` |
| To account | `00000000-0000-0000-0000-000000000011` |

Other scenarios: copy IDs from `config/sandbox-ready.txt` after boot.

```bash
TENANT=00000000-0000-0000-0000-000000000001
FROM=00000000-0000-0000-0000-000000000010
TO=00000000-0000-0000-0000-000000000011

curl -s -X POST "http://localhost:8080/api/v1/tenants/$TENANT/journal-entries" \
  -H "Authorization: Bearer $FINLEDGER_TOKEN" \
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

Same `Idempotency-Key` + same body → replay. Same key + different body → `409`.

Stop (keeps Postgres data):

```bash
./bin/finledger-cli down
```

---

## 4. Sandbox scenario packs

| Scenario | Labels | Seed |
|----------|--------|------|
| `simple` (default) | EcoPay | Standalone + two USD wallets |
| `aggregator` | EcoPay Network + Send Tunnel | Aggregator + sub-merchant + pool/fee |
| `remittance` | Send Tunnel Remit | USD + EUR wallets |

```bash
./bin/finledger-cli sandbox init --scenario aggregator   # or omit flag and pick on TTY
./bin/finledger-cli up --profile sandbox --build         # restart after changing scenario
```

Optional mint for a non-default seeded tenant:

```bash
./bin/finledger-cli auth token --tenant-id 00000000-0000-0000-0000-0000000000a2
```

On **normal + internal**, do **not** pass `tenant_id` to mint — tenant is bound to the
client record (`400 tenant_id_not_allowed` if you try).

---

## 5. Auth contract (production)

FinLedger only **verifies** JWTs. Your IdP or BFF must **issue**:

| Claim / rule | Detail |
|--------------|--------|
| Algorithms | `RS256` or `ES256` only |
| `iss` | Matches configured issuer |
| `exp` | Required; ledger also caps TTL (default 15m) |
| `tenant_id` | Must equal `/api/v1/tenants/{tenantId}/…` |
| Scopes | `ledger:read`, `ledger:write`, and/or `ledger:admin` |
| Control-plane | `platform:admin` — bootstrap / create tenant only; **no** `tenant_id` claim |

Allowed BFF patterns: pass-through user token, token exchange, or machine
client-credentials. Forbidden: strip auth and call FinLedger open.

Production day-0: `issuer=external`, leave `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET` unset.

---

## 6. Configuration cheat sheet

Last wins: embedded YAML → optional `./config/` → environment.

| Variable | Typical |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `sandbox` or `normal` |
| `FINLEDGER_ENV` | `local` or `production` |
| `FINLEDGER_SECURITY_ISSUER` | `internal` or `external` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | IdP issuer (external) |
| `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET` | IdP-less cold-start only; blank → endpoint 404 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | App role must **not** be superuser (RLS) |
| `MANAGEMENT_SERVER_PORT` | `8081` |

Template: `finledger.env.example` → `.env`.

```bash
./bin/finledger-cli config init --profile normal
./bin/finledger-cli config validate
./bin/finledger-cli restart --service app
```

---

## 7. Production checklist

1. Deploy Hub image `${DOCKERHUB_USERNAME}/finledger:<semver>`.
2. `SPRING_PROFILES_ACTIVE=normal`, `FINLEDGER_ENV=production`, `issuer=external`.
3. Configure OIDC issuer/JWKS; enforce §5.
4. TLS 1.3 at the edge; keep `:8081` private.
5. Provision tenants with `ledger:admin` (or one-shot platform bootstrap only for IdP-less lab).
6. Every mutation sends `Idempotency-Key`.
7. Prometheus / optional OTLP; secrets via env — never bake into the image.
8. Disable Swagger in prod; design for outbox lag and token refresh before `exp`.

```text
Your API / BFF / workers
        │  HTTPS + Idempotency-Key + Bearer JWT
        ▼
FinLedger (image) → Your Postgres (RLS)
```

---

## 8. Failure modes

| Situation | Behavior |
|-----------|----------|
| Same idempotency key + same body | Replay |
| Same key + different body | `409` |
| Bad / missing JWT | `401` |
| `tenant_id` ≠ path | `403` `TENANT_CLAIM_MISMATCH` |
| Sandbox + production env | Boot failure |
| Bootstrap already claimed | `410` |
| Mint body `tenant_id` on persistent issuer | `400` `tenant_id_not_allowed` |

---

## 9. Day-2 CLI

```bash
./bin/finledger-cli                 # REPL
./bin/finledger-cli doctor
./bin/finledger-cli status
./bin/finledger-cli logs -f --service app-sandbox
./bin/finledger-cli auth token      # prompts for secret on TTY
./bin/finledger-cli platform bootstrap
./bin/finledger-cli tenant create
./bin/finledger-cli down            # preserves Postgres data
```

Interactive rules:

- **TTY:** missing secrets/fields → console prompt (`readPassword` for secrets).
- **Non-TTY / CI:** flags or env required (no hanging prompts).

---

*Living document for integrators. Keep the shared loop (up → token → API) as the
primary path for both sandbox and normal.*
