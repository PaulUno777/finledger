# FinLedger — developer integration guide

Self-contained guide for the **engineering team** integrating FinLedger: local eval,
CI/IdP-less normal, staging with your IdP, and production go-live. Same loop everywhere.
You need Docker (Kubernetes only when you deploy), the repo CLI (`./bin/finledger-cli`),
and a terminal.

**One loop for every environment:**

```text
up → get a TOKEN → call the API with that TOKEN
```

| Who / when | Start here |
| ---------- | ---------- |
| Local demo / onboarding | §3 sandbox |
| Backend without an IdP yet | §3 normal + internal |
| Staging / prod with your IdP | §3 normal + external → §5 → §7 |
| Day-2 ops (any env) | §8–§10 |

The CLI prompts on a TTY for secrets and missing fields. In CI/pipes, pass flags or env.

Related: [configuration.md](configuration.md) · [auth-integration.md](auth-integration.md) ·
[ADR-015](adr/ADR-015-operational-model.md) · [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md)

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

| Artifact | Role | Liability |
| -------- | ---- | --------- |
| Hub image `${DOCKERHUB_USERNAME}/finledger:<semver>` | Canonical deployable | Full CI + multi-arch release bar ([ADR-012](adr/ADR-012-docker-distribution.md)) |
| Server fat JAR | Escape hatch (non-container hosts) | Weaker guarantee bar ([ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md)) |

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

**Normal + your IdP:** set `FINLEDGER_SECURITY_ISSUER=external` and
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/...` — skip
§3.2 bootstrap; put an IdP token in `FINLEDGER_TOKEN` and jump to §3.3.
`docker compose --profile with-app up -d --build` is the usual path.

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

OpenAPI (dev): http://localhost:8080/swagger-ui.html — click **Authorize**, paste Bearer.

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

| Scenario           | Labels                       | Seed                                 |
| ------------------ | ---------------------------- | ------------------------------------ |
| `simple` (default) | EcoPay                       | Standalone + two USD wallets         |
| `aggregator`       | EcoPay Network + Send Tunnel | Aggregator + sub-merchant + pool/fee |
| `remittance`       | Send Tunnel Remit            | USD + EUR wallets                    |

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

| Claim / rule  | Detail                                                                      |
| ------------- | --------------------------------------------------------------------------- |
| Algorithms    | `RS256` or `ES256` only                                                     |
| `iss`         | Matches configured issuer                                                   |
| `exp`         | Required; ledger also caps TTL (default 15m)                                |
| `tenant_id`   | Must equal `/api/v1/tenants/{tenantId}/…`                                   |
| Scopes        | `ledger:read`, `ledger:write`, and/or `ledger:admin`                        |
| Control-plane | `platform:admin` — bootstrap / create tenant only; **no** `tenant_id` claim |

Allowed BFF patterns: pass-through user token, token exchange, or machine
client-credentials. Forbidden: strip auth and call FinLedger open.

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

1. Pull `${DOCKERHUB_USERNAME}/finledger:<semver>` (also `:latest` after a release tag).
2. `SPRING_PROFILES_ACTIVE=normal`, `FINLEDGER_ENV=production`, `FINLEDGER_SECURITY_ISSUER=external`.
3. Point OIDC issuer/JWKS at your IdP; enforce §5.
4. Terminate **TLS 1.3** at the edge; keep management port **`:8081` private** (ClusterIP / no public LB).
5. Postgres: Flyway may use a privileged migrator; the **app role must not be superuser**
   (FORCE RLS). Dev Compose uses `finledger` (Flyway) + `finledger_app` (app) — mirror that split.
6. Secrets via env / mounted config / secret store — **never** bake into the image.
7. Disable Springdoc in prod (`SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`).
8. Provision first tenants with a `ledger:admin` JWT from your IdP (not platform bootstrap).
9. Every mutating call sends `Idempotency-Key`; design clients for outbox lag and token
   refresh **before** `exp` (default max TTL 15m).
10. Wire Prometheus scrape on `:8081/actuator/prometheus`; optional OTLP via
    `OTEL_EXPORTER_OTLP_ENDPOINT`.

### 7.2 Required production environment

| Variable | Required | Notes |
| -------- | -------- | ----- |
| `SPRING_PROFILES_ACTIVE` | yes | `normal` |
| `FINLEDGER_ENV` | yes | `production` |
| `FINLEDGER_SECURITY_ISSUER` | yes | `external` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | yes* | or `…_JWK_SET_URI` |
| `DB_URL` | yes | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | yes | Non-superuser app role |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | typical | Migrator (often elevated) |
| `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET` | leave unset | Prod uses IdP admins |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | recommended `false` | |
| `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET` | if rails | HMAC for inbound settlement |
| `FINLEDGER_RATE_LIMIT_*` | optional | See §8 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | optional | Traces |

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
          image: YOUR_DOCKERHUB_USER/finledger:0.1.0   # pin semver
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

1. Deploy image / K8s with §7.2 env and healthy probes.
2. Create a `ledger:admin` principal in your IdP (scopes + no mistaken `tenant_id` on
   control-plane-only tokens if you use platform-style roles).
3. `POST /api/v1/tenants` (or `./bin/finledger-cli tenant create`) with that admin JWT.
4. Issue tenant-scoped machine/user tokens (`tenant_id` + `ledger:write`) for BFF/workers.
5. Post a dry-run journal with `Idempotency-Key`; confirm `201` and audit/outbox paths.

### Day-2

| Concern | What to do |
| ------- | ---------- |
| Token refresh | Refresh IdP tokens before `exp`; CLI silent remint is **in-box issuer only** |
| Outbox lag | Consumers must tolerate delay/duplication; publish is transactional outbox |
| Rate limit | In-memory Bucket4j on `/api/v1/**` — `FINLEDGER_RATE_LIMIT_ENABLED` (default `true`), `…_CAPACITY` / `…_REFILL_PER_SECOND` (defaults `120` / `60`). Multi-replica = per-pod; Redis deferred |
| Rail webhooks | `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET`; anti-replay skew `FINLEDGER_RAIL_WEBHOOK_MAX_SKEW_SECONDS` (default `300`) |
| Observability | Prometheus `:8081/actuator/prometheus`; optional OTLP; Compose profile `observability` for local Grafana |
| Fraud (optional) | `FINLEDGER_FRAUD_ENABLED=true` — separate bounded context; fail-closed/open per tenant config |
| Restarts | Named Postgres volume / PVC — `compose restart` / rolling update must not wipe data |
| CLI | `doctor` / `health` / `ready` / `status` / `logs` against running stack (§9) |

---

## 9. Failure modes

| Situation                                  | Behavior                      |
| ------------------------------------------ | ----------------------------- |
| Same idempotency key + same body           | Replay                        |
| Same key + different body                  | `409`                         |
| Bad / missing JWT                          | `401`                         |
| `tenant_id` ≠ path                         | `403` `TENANT_CLAIM_MISMATCH` |
| Rate limit exceeded                        | `429` `RATE_LIMITED`          |
| Sandbox + production env                   | Boot failure                  |
| Bootstrap already claimed                  | `410`                         |
| Mint body `tenant_id` on persistent issuer | `400` `tenant_id_not_allowed` |
| Unmapped / disabled route (e.g. sandbox bootstrap, `:8080/actuator`) | `404` `NOT_FOUND` |

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
