# FinLedger — developer integration guide

Self-contained guide for the **engineering team** integrating FinLedger: local eval,
CI/IdP-less normal, staging with your IdP, and production go-live. Same loop everywhere.
You need Docker (Kubernetes only when you deploy), the repo CLI (`./bin/finledger-cli`),
and a terminal.

**One loop for every environment:**

```text
up → get a TOKEN → call the API with that TOKEN
```

| Who / when                   | Start here                     |
| ---------------------------- | ------------------------------ |
| Local demo / onboarding      | §3 sandbox                     |
| Backend without an IdP yet   | §3 normal + internal           |
| Staging / prod with your IdP | §3 normal + external → §5 → §7 |
| Day-2 ops (any env)          | §8–§10                         |

The CLI prompts on a TTY for secrets and missing fields. In CI/pipes, pass flags or env.

Related: [configuration.md](configuration.md) · [auth-integration.md](auth-integration.md) ·
[ADR-015](adr/ADR-015-operational-model.md) · [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md) ·
[ADR-018](adr/ADR-018-parent-admin-child-accounts.md)

---

## 1. What you get

FinLedger is a **self-hosted multi-tenant double-entry ledger**. You call its REST API;
it owns journals, idempotency, tenant isolation (Postgres RLS), and a hash-chained audit
trail. Your stack owns UX, KYC, PSP rails, and (in production) JWT **issuance**.

| FinLedger                       | You                                  |
| ------------------------------- | ------------------------------------ |
| Verify every JWT (always on)    | Issue JWTs in production (IdP / BFF) |
| Journal + balances + outbox     | Consume events (optional adapters)   |
| Docker Hub image for production | Configure env / secrets at the edge  |

**Distribution** (when you leave local Compose):

| Artifact                               | Role                               | Liability                                                                        |
| -------------------------------------- | ---------------------------------- | -------------------------------------------------------------------------------- |
| Hub image `unoteck/finledger:<semver>` | Canonical deployable               | Full CI + multi-arch release bar ([ADR-012](adr/ADR-012-docker-distribution.md)) |
| Server fat JAR                         | Escape hatch (non-container hosts) | Weaker guarantee bar ([ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md))     |

**Hard rule:** never run profile `sandbox` with `FINLEDGER_ENV=production` (or `prod`).
Boot refuses.

---

## 2. Two knobs (same CLI either way)

| Knob    | Values                   | Meaning                               |
| ------- | ------------------------ | ------------------------------------- |
| Profile | `sandbox` \| `normal`    | Eval seed vs empty DB                 |
| Issuer  | `internal` \| `external` | Mint JWT inside FinLedger vs your IdP |

| Goal                           | Profile   | Issuer              | How you get `TOKEN`                                        |
| ------------------------------ | --------- | ------------------- | ---------------------------------------------------------- |
| Local demo (recommended first) | `sandbox` | `internal` (forced) | `./bin/finledger-cli auth token`                           |
| Local without IdP              | `normal`  | `internal`          | `platform bootstrap` once → `tenant create` → `auth token` |
| Staging / production           | `normal`  | `external`          | Export JWT from your IdP/BFF → `FINLEDGER_TOKEN`           |

Shared after you have a token:

```bash
export FINLEDGER_TOKEN='…'   # or paste into Swagger Authorize (dev only)
# then the same CLI / curl style for tenants, accounts, journals
```

---

## 3. Eval path (CLI-first)

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

**Normal + in-box issuer (no IdP):** generate a signing key once, then pick **one** start
path (host path ≠ container path for the PEM):

```bash
mkdir -p config
test -f config/internal-signing.pem || \
  openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -out config/internal-signing.pem
```

**Path A — host JVM** (Postgres via Compose; app on the host):

```bash
# .env (IdP-less local — host paths)
SPRING_PROFILES_ACTIVE=normal
FINLEDGER_ENV=local
FINLEDGER_SECURITY_ISSUER=internal
FINLEDGER_INTERNAL_SIGNING_KEY_PATH=./config/internal-signing.pem
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_ID=ci
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_SECRET=   # or leave and use auth token prompt later
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_TENANT_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
FINLEDGER_PLATFORM_BOOTSTRAP_SECRET=                   # set a high-entropy value once

docker compose up -d                                   # Postgres only
./mvnw -pl finledger spring-boot:run
```

**Path B — Compose `with-app`** (app in Docker; PEM via mounted `/workspace/config`):

```bash
# .env (IdP-less local — container paths)
SPRING_PROFILES_ACTIVE=normal
FINLEDGER_ENV=local
FINLEDGER_SECURITY_ISSUER=internal
FINLEDGER_INTERNAL_SIGNING_KEY_PATH=/workspace/config/internal-signing.pem
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_ID=ci
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_SECRET=
FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_TENANT_ID=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
FINLEDGER_PLATFORM_BOOTSTRAP_SECRET=

docker compose --profile with-app up -d --build
```

Do **not** reuse a host-relative PEM path (`./config/...`) inside `with-app` — the
container will not resolve it. Compose defaults remain `issuer=external` /
`FINLEDGER_ENV=production` when those keys are unset (OIDC path below).

**Normal + your IdP (prod-like):** `FINLEDGER_SECURITY_ISSUER=external` and
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` pointing at **Zitadel or
Keycloak** (or any OIDC IdP). Skip §3.2 bootstrap — mint in the IdP, export
`FINLEDGER_TOKEN`, jump to §3.3. Same Token Profile as §5.1: one
`platform:admin` client (**no** `tenant_id`) and a separate tenant-worker client
(`ledger:*` + `tenant_id`). Zitadel action stub: [auth-integration.md](auth-integration.md).

Local Keycloak eval (optional — **not** part of product Compose, ADR-012):

```bash
docker compose -f deploy/idp/docker-compose.keycloak.yml up -d
# Admin UI http://localhost:8180  (admin / admin) — realm `finledger` imported

# FinLedger (host JVM; Kind often owns :8080/:8081 — use other ports)
SPRING_PROFILES_ACTIVE=normal \
FINLEDGER_ENV=local \
FINLEDGER_SECURITY_ISSUER=external \
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8180/realms/finledger \
FINLEDGER_SECURITY_CLAIM_SCOPES=ledger_scope \
SERVER_PORT=18080 MANAGEMENT_SERVER_PORT=18081 \
# … datasource env …
java -jar finledger/target/finledger-0.1.0.jar

# Platform token (no tenant_id) then tenant worker
curl -s http://localhost:8180/realms/finledger/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=finledger-platform -d client_secret=platform-dev-secret
# Eval secrets in deploy/idp/keycloak/ — never reuse in production
```

`docker compose --profile with-app up -d --build` is the usual path once
`ISSUER_URI` is your real IdP.

Health (management port **8081** only — `:8080/actuator/health` is not mapped → **404**):

```bash
curl -s http://localhost:8081/actuator/health
# {"status":"UP",...}
# or: ./bin/finledger-cli health
```

On **sandbox**, `POST /api/v1/platform/bootstrap` is not registered → **404** (normal+internal only).

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

OpenAPI (dev): <http://localhost:8080/swagger-ui.html> — click **Authorize**, paste Bearer.

**Sandbox `simple` pack** (stable IDs):

| Resource        | UUID                                   |
| --------------- | -------------------------------------- |
| Tenant (EcoPay) | `00000000-0000-0000-0000-000000000001` |
| From account    | `00000000-0000-0000-0000-000000000010` |
| To account      | `00000000-0000-0000-0000-000000000011` |

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

### Client patterns (`/sdk-reference/`)

In-repo **non-official** Java helpers under [`sdk-reference/`](../sdk-reference/)
(JDK `HttpClient`; no Spring; **no SemVer / Maven Central** — official SDKs are FL-180):

- Idempotency-Key generation + “reuse only with same body hash”
- Rail webhook HMAC verify (`HMAC-SHA256(timestamp + "." + nonce + "." + body)`)
- Safe retries (no generic `4xx`; optional `408`/`429`)
- W3C `traceparent` generate/propagate on outbound calls

OpenAPI **path + operationId** inventory is gated in CI:
[`docs/contracts/openapi-paths.json`](contracts/openapi-paths.json). After intentional
API changes, regenerate with
`./mvnw -pl finledger -Dtest=ApiContractIntegrationTest -Dfinledger.contracts.write=true test`.

Stop (keeps Postgres data):

```bash
./bin/finledger-cli down
```

---

## 4. Sandbox scenario packs

Sandbox packs are **eval seeds**, not extra tenant types. They map 1:1 onto the
enums in §5.2:

| Scenario           | Labels                       | TenantType(s)                         | Seed                                 |
| ------------------ | ---------------------------- | ------------------------------------- | ------------------------------------ |
| `simple` (default) | EcoPay                       | `STANDALONE`                          | Standalone + two USD wallets         |
| `aggregator`       | EcoPay Network + Send Tunnel | `AGGREGATOR` + `SUB_MERCHANT`         | Aggregator + sub-merchant + pool/fee |
| `remittance`       | Send Tunnel Remit            | `STANDALONE` (multi-currency wallets) | USD + EUR wallets                    |

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

FinLedger only **verifies** JWTs. Your IdP or BFF must **issue**. Full BFF patterns:
[auth-integration.md](auth-integration.md).

### 5.1 Token Profile (do not mix clients)

Two (or three) IdP clients. Never use one JWT for both control-plane and money.

| Client               | Scopes                                | `tenant_id`             | Allowed                                                                                                                                                                                                                                                       |
| -------------------- | ------------------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Platform**         | `platform:admin`                      | **absent**              | `POST /api/v1/tenants`; `POST /api/v1/platform/bootstrap` (IdP-less only); `POST /api/v1/platform/provision` (root recipes). Optional body `id` on create-tenant.                                                                                             |
| **Tenant worker**    | `ledger:write` (and/or `ledger:read`) | **= path tenant**       | Accounts, rails, settle, refunds, journals, splits, fee/fx config on **that** tenant.                                                                                                                                                                         |
| **Parent admin (C)** | `ledger:admin`                        | **= AGGREGATOR parent** | Same as tenant worker on the parent **plus** `GET/POST …/tenants/{child}/accounts` (and get/balance) for a **direct** `SUB_MERCHANT` child only ([ADR-018](adr/ADR-018-parent-admin-child-accounts.md)). Money on the child still needs a child-scoped token. |

`POST /api/v1/tenants` also accepts `ledger:admin` **without** a `tenant_id` (same
control-plane skip as `platform:admin`). Prefer `platform:admin` for topology;
`ledger:admin` without `tenant_id` is for IdP principals that already own that scope.
A `tenant_id` claim on a control-plane token is **ignored** on `POST /tenants` — do
not treat that as “this token may write any tenant.”

`platform:admin` never authorizes accounts, rails, journals, or other data-plane
routes.

### 5.2 Enums and hierarchy

**`TenantType`:** `STANDALONE` \| `AGGREGATOR` \| `SUB_MERCHANT`.

- `STANDALONE` and `AGGREGATOR` are **roots** — no `parentTenantId` (`422 INVALID_TENANT_HIERARCHY` if set).
- `SUB_MERCHANT` **requires** `parentTenantId` pointing at an existing `AGGREGATOR`.

**`AccountType`:** `MERCHANT_WALLET`, `AGGREGATOR_POOL`, `RAIL_CLEARING`,
`SUSPENSE_HOLD`, `FEE_PLATFORM_REVENUE`, `FEE_INTERCHANGE_COST`,
`FEE_AGGREGATOR_MARKUP`, `RESERVE_HOLD`, `TAX_VAT`.

### 5.3 Rails account rules

`POST /api/v1/tenants/{tenantId}/rails/payments`:

- `clearingAccountId` **must** be type `RAIL_CLEARING` (`422 INVALID_CLEARING_ACCOUNT` otherwise).
- `counterpartyAccountId` must exist in the same tenant (typically `MERCHANT_WALLET` or a fee/pool account).
- Empty / missing JSON body → `400 INVALID_ARGUMENT` (not 500).
- Settle: `POST …/rails/payments/{railReference}/settle` (empty body is allowed when the path identifies the instruction).

`GET /api/v1/tenants/{tenantId}/accounts` lists accounts for that tenant (recover IDs after create).

### 5.4 Claim / TTL rules

| Claim / rule | Detail                                                                                                                                                      |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Algorithms   | `RS256` or `ES256` only                                                                                                                                     |
| `iss`        | Must equal configured issuer (`ISSUER_URI`). If discovery `issuer` ≠ JWT `iss`, set `jwk-set-uri` independently and/or `finledger.security.issuer-aliases`. |
| `exp`        | Required; ledger also caps TTL (default **15m** for user/pass-through)                                                                                      |
| Machine TTL  | Tokens with `token_use=machine` or `azp` on `finledger.security.machine-azp-allowlist` may use `finledger.security.max-token-ttl-machine` (default **1h**)  |
| `tenant_id`  | Must equal `/api/v1/tenants/{tenantId}/…` except create-tenant, `/platform/**`, and ADR-018 parent-admin **account** routes                                 |
| Scopes       | `ledger:read`, `ledger:write`, `ledger:admin`; control-plane `platform:admin`                                                                               |

**Machine tokens:** refresh **before each saga / Temporal activity**. Do **not** inject
a single bearer at pod start — a 15m (or even 1h) token will expire mid-park and later
calls return `401`. CLI silent remint is **in-box issuer only**.

Allowed BFF patterns: **token exchange** or **machine** client-credentials for day-0
and workers. **Pass-through** (forward the user’s IdP access token unchanged) is
**runtime / interactive only** — it is **not** the day-0 path for STANDALONE or
AGGREGATOR provisioning (one user JWT cannot be both “no `tenant_id`” and
“`tenant_id` = child”). Forbidden: strip auth and call FinLedger open.

Production day-0: `issuer=external`, leave `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET` unset.

---

## 6. Configuration cheat sheet

Last wins: embedded YAML → optional `./config/` → environment. Full reference:
[configuration.md](configuration.md). Template: `finledger.env.example` → `.env`.

| Variable                                               | Typical                                        |
| ------------------------------------------------------ | ---------------------------------------------- |
| `SPRING_PROFILES_ACTIVE`                               | `sandbox` or `normal`                          |
| `FINLEDGER_ENV`                                        | `local` or `production`                        |
| `FINLEDGER_SECURITY_ISSUER`                            | `internal` or `external`                       |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | IdP issuer (external)                          |
| `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET`                  | IdP-less cold-start only; blank → endpoint 404 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`               | App role must **not** be superuser (RLS)       |
| `MANAGEMENT_SERVER_PORT`                               | `8081`                                         |

```bash
./bin/finledger-cli config init --profile normal
./bin/finledger-cli config validate
./bin/finledger-cli restart --service app
```

---

## 7. Production go-live (when you ship)

For staging/prod — not required for local eval (§3). Canonical path: **Hub image** +
profile `normal` + `issuer=external` + your Postgres.

```text
Your API / BFF / workers
        │  HTTPS + Idempotency-Key + Bearer JWT
        ▼
FinLedger (Hub image) → Your Postgres (FORCE RLS)
```

### 7.1 Checklist

1. Pull `unoteck/finledger:<semver>` (current: `0.1.0`; also `:latest` after each release tag).

   ```bash
   docker pull unoteck/finledger:0.1.0
   ```

2. `SPRING_PROFILES_ACTIVE=normal`, `FINLEDGER_ENV=production`, `FINLEDGER_SECURITY_ISSUER=external`.
3. Point OIDC issuer/JWKS at your IdP; enforce §5.
4. Terminate **TLS 1.3** at the edge; keep management port **`:8081` private** (ClusterIP / no public LB).
5. Postgres: Flyway may use a privileged migrator; the **app role must not be superuser**
   (FORCE RLS). Dev Compose uses `finledger` (Flyway) + `finledger_app` (app) — mirror that split.
6. Secrets via env / mounted config / secret store — **never** bake into the image.
7. Disable Springdoc in prod (`SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`).
8. Provision first **root** tenants with a **`platform:admin`** JWT from your IdP
   (`POST /tenants` or `POST /platform/provision`) — **no** `tenant_id` claim. Do **not**
   use `POST /platform/bootstrap` in production (secret unset).
9. Every mutating call sends `Idempotency-Key`. Refresh machine JWTs **per activity**
   before `exp` (default max TTL 15m; optional machine cap 1h — §5.4).
10. Wire Prometheus scrape on `:8081/actuator/prometheus`; optional OTLP via
    `OTEL_EXPORTER_OTLP_ENDPOINT`.

### 7.2 Required production environment

| Variable                                                      | Required            | Notes                       |
| ------------------------------------------------------------- | ------------------- | --------------------------- |
| `SPRING_PROFILES_ACTIVE`                                      | yes                 | `normal`                    |
| `FINLEDGER_ENV`                                               | yes                 | `production`                |
| `FINLEDGER_SECURITY_ISSUER`                                   | yes                 | `external`                  |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`        | yes\*               | or `…_JWK_SET_URI`          |
| `DB_URL`                                                      | yes                 | JDBC URL                    |
| `DB_USERNAME` / `DB_PASSWORD`                                 | yes                 | Non-superuser app role      |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`               | typical             | Migrator (often elevated)   |
| `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET`                         | leave unset         | Prod uses IdP admins        |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | recommended `false` |                             |
| `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET`                          | if rails            | HMAC for inbound settlement |
| `FINLEDGER_RATE_LIMIT_*`                                      | optional            | See §8                      |
| `OTEL_EXPORTER_OTLP_ENDPOINT`                                 | optional            | Traces                      |

Compose eval of the same shape: `docker compose --profile with-app up -d` after filling
`.env` for external OIDC (see §3.1).

### 7.3 Kubernetes skeleton (illustrative)

Not a Helm chart — copy and adapt. Probes hit **8081** only.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: finledger
spec:
  replicas: 2
  selector:
    matchLabels:
      app: finledger
  template:
    metadata:
      labels:
        app: finledger
    spec:
      containers:
        - name: finledger
          image: unoteck/finledger:0.1.0 # pin semver; avoid :latest in prod
          ports:
            - name: http
              containerPort: 8080
            - name: management
              containerPort: 8081
          envFrom:
            - configMapRef:
                name: finledger-config
            - secretRef:
                name: finledger-secrets
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: management
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: management
            initialDelaySeconds: 60
            periodSeconds: 20
          resources:
            requests:
              cpu: "250m"
              memory: 512Mi
            limits:
              memory: 1Gi
---
apiVersion: v1
kind: Service
metadata:
  name: finledger
spec:
  selector:
    app: finledger
  ports:
    - name: http
      port: 8080
      targetPort: http
    # Do not expose management on a public LoadBalancer — ClusterIP only or omit
    - name: management
      port: 8081
      targetPort: management
```

`finledger-config` / `finledger-secrets` should carry the §7.2 keys. Optional config
file mount: image honors `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/workspace/config/`.

### 7.4 JAR escape hatch

Prefer the Hub image. A fat JAR + systemd is supported for non-container hosts but
carries **weaker liability** than the released multi-arch image (ADR-016). Same env
contract as §7.2; health still on `MANAGEMENT_SERVER_PORT` (default 8081).

---

## 8. Day-0 and day-2 operations

### Day-0

1. Deploy image / K8s with §7.2 env and healthy probes. Point `ISSUER_URI` at the
   JWT `iss` (or set `jwk-set-uri` if discovery issuer diverges).
2. Create **two** IdP clients (Token Profile §5.1). Do not reuse one token for both.

**STANDALONE (two tokens — no hierarchy / no ADR-018):**

1. **Token A** — `platform:admin`, no `tenant_id` → `POST /api/v1/tenants` with
   `type=STANDALONE` (no `parentTenantId`), **or** `POST /api/v1/platform/provision`
   `{ "recipe": "STANDALONE", … }`.
2. **Token B** — `ledger:write` + `tenant_id` = that tenant → `RAIL_CLEARING`,
   wallets, fee-config, then rails / journals / refunds.

**AGGREGATOR + SUB_MERCHANT (Token B provisions child wallets — ADR-018):**

1. **Token A** — `platform:admin`, no `tenant_id` → create `AGGREGATOR`, then
   `SUB_MERCHANT` with `parentTenantId` = aggregator (or provision recipe
   `AGGREGATOR` for the root only — children stay `POST /tenants`).
2. **Token B** — `ledger:admin` + `tenant_id` = aggregator → parent
   `RAIL_CLEARING` / pool / fee accounts **and** child `MERCHANT_WALLET`s under
   `/tenants/{subMerchantId}/accounts`.
3. **Token C** — `ledger:write` + `tenant_id` = sub-merchant → rails, settle,
   refunds, journals on the child. Parent Token B **cannot** call money paths on
   the child.

CLI: `./bin/finledger-cli jwt inspect --token "$FINLEDGER_TOKEN"` prints which
profile a token matches.

### Day-2

| Concern          | What to do                                                                                                                                                                                                                            |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Token refresh    | Refresh IdP tokens **before each long-running saga activity**, not once at pod start. CLI silent remint is **in-box issuer only**.                                                                                                    |
| Outbox lag       | Consumers must tolerate delay/duplication; publish is transactional outbox                                                                                                                                                            |
| Rate limit       | In-memory Bucket4j on `/api/v1/**` — `FINLEDGER_RATE_LIMIT_ENABLED` (default `true`), `…_CAPACITY` / `…_REFILL_PER_SECOND` (defaults `120` / `60`). **Per pod, not cluster-wide** — each replica has its own bucket (Redis deferred). |
| Rail webhooks    | `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET`; anti-replay skew `FINLEDGER_RAIL_WEBHOOK_MAX_SKEW_SECONDS` (default `300`)                                                                                                                      |
| Observability    | Prometheus `:8081/actuator/prometheus`; optional OTLP; Compose profile `observability` for local Grafana                                                                                                                              |
| Fraud (optional) | `FINLEDGER_FRAUD_ENABLED=true` — separate bounded context; fail-closed/open per tenant config                                                                                                                                         |
| Restarts         | Named Postgres volume / PVC — `compose restart` / rolling update must not wipe data                                                                                                                                                   |
| CLI              | `doctor` / `health` / `ready` / `status` / `logs` / `jwt inspect` against running stack (§9)                                                                                                                                          |

---

## 9. Failure modes

| Situation                                                            | Behavior                         |
| -------------------------------------------------------------------- | -------------------------------- |
| Same idempotency key + same body                                     | Replay                           |
| Same key + different body                                            | `409`                            |
| Bad / missing JWT                                                    | `401`                            |
| `tenant_id` ≠ path (and not ADR-018 parent-admin account route)      | `403` `TENANT_CLAIM_MISMATCH`    |
| Wallet used as `clearingAccountId`                                   | `422` `INVALID_CLEARING_ACCOUNT` |
| Unknown `TenantType` / `AccountType`                                 | `400` `INVALID_ARGUMENT`         |
| `SUB_MERCHANT` without parent / parent not `AGGREGATOR`              | `422` `INVALID_TENANT_HIERARCHY` |
| Rate limit exceeded                                                  | `429` `RATE_LIMITED`             |
| Sandbox + production env                                             | Boot failure                     |
| Bootstrap already claimed                                            | `410`                            |
| Mint body `tenant_id` on persistent issuer                           | `400` `tenant_id_not_allowed`    |
| Unmapped / disabled route (e.g. sandbox bootstrap, `:8080/actuator`) | `404` `NOT_FOUND`                |

---

## 10. Day-2 CLI

```bash
./bin/finledger-cli                 # REPL (prints session banner; silent remint in-box only)
./bin/finledger-cli health          # GET …/actuator/health
./bin/finledger-cli ready           # readiness, else health UP
./bin/finledger-cli doctor          # fails if actuator unhealthy
./bin/finledger-cli status
./bin/finledger-cli logs -f --service app-sandbox
./bin/finledger-cli auth token      # prompts for secret on TTY; stores session for remint
./bin/finledger-cli jwt inspect --token "$FINLEDGER_TOKEN"
./bin/finledger-cli platform bootstrap
./bin/finledger-cli tenant create --dry-run   # print request; no HTTP
./bin/finledger-cli down            # preserves Postgres data
```

Interactive rules:

- **TTY:** missing secrets/fields → console prompt (`readPassword` for secrets).
- **Non-TTY / CI:** flags or env required (no hanging prompts).
- **Silent remint:** after `auth token` in the same process/shell, near-expiry or HTTP
  `401` remints via stored client credentials (in-box issuer only). External IdP:
  export `FINLEDGER_TOKEN` yourself — CLI does not refresh IdP tokens.
- **`--dry-run`:** global on mutating API calls (`tenant` / `account` / `fx` / `split-rules`);
  not applied to `auth token` / `platform bootstrap` (those _are_ the mint).
- **Tenant binding:** JWT `tenant_id` claim only — no `X-FinLedger-Tenant-Id` header.

---

_Developer integration guide (FL-190). Local, staging, and production share the same
loop: up → token → API._
