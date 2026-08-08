# Configuration

FinLedger uses Spring Boot's native configuration resolution in **layers**. There is
no interactive wizard at boot (wizards block health checks and orchestrated deploys).

## Resolution order (highest priority last wins)

1. **Embedded defaults** — `finledger/src/main/resources/application.yaml` (and profile files)
2. **Optional external file** — mount or place config under a directory listed in
   `spring.config.additional-location` (prefixed with `optional:` so absence is fine)
3. **Environment variables** — relaxed binding (`SPRING_DATASOURCE_URL`, etc.)
4. **Secrets** — never in YAML committed to git; use env / secret store via the
   `SecretsProvider` port (default: `EnvSecretsProvider` — environment and system
   properties, with a startup warning that this is for non-prod)

## Local development

```bash
# Fastest eval path (Blnk-style — ADR-015 / FL-152)
git clone <repo> && cd finledger
cp finledger.env.example .env
docker compose --profile sandbox up -d --build
# Or: ./bin/finledger-cli up --profile sandbox --build
# copy-paste curls from config/sandbox-ready.txt or app logs

# Or normal profile + external IdP:
docker compose up -d
export SPRING_PROFILES_ACTIVE=normal
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/realms/finledger
./mvnw -pl finledger spring-boot:run
```

Developer integration guide (eval through production; Hub / OIDC / K8s in §7–§8):
[INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md). Ops model: [ADR-015](adr/ADR-015-operational-model.md).
Auth model: [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md).
**Who issues tokens / claim contract / BFF:** [auth-integration.md](auth-integration.md).

Profile files: `application-sandbox.yml` / `application-normal.yml` (localhost defaults
for IDE). Flyway migrates as superuser `finledger`; the app connects as non-superuser
`finledger_app` / `finledger` so Postgres FORCE RLS is enforced (superusers bypass RLS).
Those credentials are **dev-only**.

### Runtime profiles & JWT (ADR-016)

| Profile | Purpose | Issuer |
|---------|---------|--------|
| `sandbox` | Seeded eval | In-box issuer; **ephemeral signing keys per boot** |
| `normal` | Real deployments (default) | External OIDC (default) **or** in-box issuer (FL-156 durable secrets) |

**Invariants:** JWT verification always on (RS256/ES256, `exp`, ledger max TTL, `tenant_id` +
scopes). No auth-off / `trust_edge` / `finledger.security.mode`. Long-lived `client_secret`
is for **minting only**, never the API Bearer.

| Property | Env | Notes |
|----------|-----|-------|
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `sandbox` \| `normal` only |
| `finledger.security.issuer` | `FINLEDGER_SECURITY_ISSUER` | `external` (default) \| `internal` |
| `finledger.security.max-token-ttl` | `FINLEDGER_SECURITY_MAX_TOKEN_TTL` | Default `15m` |
| `finledger.sandbox.client-id` / `client-secret` | `FINLEDGER_SANDBOX_CLIENT_ID` / `_SECRET` | Sandbox mint only; blank secret → generated at boot |
| `finledger.sandbox.scenario` | `FINLEDGER_SANDBOX_SCENARIO` | `simple` (default) \| `aggregator` \| `remittance`; CLI `sandbox init` |
| `finledger.security.internal.issuer-uri` | `FINLEDGER_INTERNAL_ISSUER_URI` | Distinct URI for normal+internal (default `http://localhost:8080/internal`) |
| `finledger.security.internal.signing-key-pem` / `signing-key-path` | `FINLEDGER_INTERNAL_SIGNING_KEY_PEM` / `_PATH` | PKCS#8 PEM; **required** for normal+internal (never auto-generated) |
| `finledger.security.internal.clients[]` | `FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_*` | Tenant-bound machine clients (`client-id`, `client-secret`, `tenant-id`, optional `scopes`) |
| `finledger.platform.bootstrap-secret` | `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET` | One-shot cold-start (FL-158); blank → bootstrap endpoint 404 |
| `finledger.platform.bootstrap-token-ttl` | `FINLEDGER_PLATFORM_BOOTSTRAP_TOKEN_TTL` | Cap by max-token-ttl; default `15m` |

Sandbox Compose: `SPRING_PROFILES_ACTIVE=sandbox` → `issuer=internal` (ephemeral keys). Mint:
`POST /api/v1/auth/token` or `./bin/finledger-cli auth token` (optional `--tenant-id` for any
seeded tenant). Scenario packs: `./bin/finledger-cli sandbox init --scenario …` then `up`.
On normal+internal, mint body `tenant_id` → HTTP 400 `tenant_id_not_allowed`.

**Normal + internal (IdP-less CI):** durable RSA + at least one client bound to a
`tenant_id`. Boot fails if the signing key or clients are missing. Cold-start from an
empty DB: set `FINLEDGER_PLATFORM_BOOTSTRAP_SECRET`, run
`./bin/finledger-cli platform bootstrap`, then `tenant create --id <bound-uuid>`
(ADR-016 FL-158). Leave the bootstrap secret unset in production (`issuer=external`).

Generate a local key:

```bash
openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -out config/internal-signing.pem
```

**Interlocks:** boot fails if profile `sandbox` with `FINLEDGER_ENV=production|prod`,
or if `sandbox` and `normal` are both active.

CLI (local YAML only — restart app after changes):

```bash
./bin/finledger-cli config init --profile sandbox
./bin/finledger-cli config set security.issuer external
./bin/finledger-cli config validate
./bin/finledger-cli restart
```

### Ops CLI (FL-152 / ADR-015)

Local Compose helpers (no Spring; shells out to `docker compose`). Run from the repo root
or pass `--project-dir`. Template: `finledger.env.example` → `.env` (gitignored).

Launcher: `./bin/finledger-cli` (POSIX) / `bin\finledger-cli.cmd` (Windows). No args opens the
interactive REPL. Prod: colocate `finledger-cli.jar` with the script, or set `FINLEDGER_CLI_JAR`.

| Command | Purpose |
|---------|---------|
| `doctor` | Docker / compose / `.env` / profile interlock; **fails** if actuator unhealthy |
| `status` | `compose ps` + `GET …/actuator/health` |
| `health` / `ready` | Thin actuator probes (`FINLEDGER_MANAGEMENT_URL`; ready falls back to health UP) |
| `up [--profile sandbox\|with-app] [--build]` | `docker compose up -d` |
| `down` | `docker compose down` (no `-v` — preserves Postgres data) |
| `sandbox init [--scenario …]` | Write `FINLEDGER_SANDBOX_SCENARIO` into `.env` (no Compose start) |
| `platform bootstrap` | One-shot `platform:admin` JWT (FL-158; normal+internal) |
| `restart [--service app-sandbox\|app]` | Restart app container |
| `logs [-f] [--service …]` | Compose logs |
| `auth token [--tenant-id]` | Mint sandbox/internal JWT; session remint in process/shell |
| `--dry-run` (global) | Print mutating API request without sending HTTP |

### AuthN / AuthZ (JWT — always)

| Item | Contract |
|------|----------|
| Algorithms | JWT `alg` must be `RS256` or `ES256` |
| Lifetime | `exp` required; ledger-enforced max TTL (ADR-016 / FL-154+) |
| Scopes | `ledger:read`, `ledger:write`, `ledger:admin`; control-plane `platform:admin` (FL-158) |
| Tenant binding | Claim `tenant_id` (UUID) must match `/api/v1/tenants/{tenantId}/…` (skipped for create + `/platform/**`) |
| Create tenant | `POST /api/v1/tenants` requires `ledger:admin` or `platform:admin`; optional `id` only with `platform:admin` |
| Public | `/actuator/health`, `/actuator/prometheus`; settlement webhooks |
| TLS | Terminate TLS 1.3 at the reverse proxy / load balancer in front of the service |
| mTLS | Additive at the edge/mesh — does not replace JWT |

If Flyway reports a checksum mismatch after a migration file was edited, recreate
the local volume: `docker compose down -v && docker compose up -d`.

### External config file (optional)

```bash
mkdir -p ./config
# edit ./config/application.yml
export SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./config/
./mvnw -pl finledger spring-boot:run
```

In containers, mount overrides at `/workspace/config/` (image default
`SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/workspace/config/`). See
[ADR-012](adr/ADR-012-docker-distribution.md).

## Docker image contract (FL-140)

| Item | Value |
|------|--------|
| Image | `${DOCKERHUB_USERNAME}/finledger:<semver>` and `:latest` (published on tag `v*.*.*`) |
| Ports | `8080` (HTTP API), `8081` (management / actuator) |
| Health | `GET http://localhost:8081/actuator/health` |
| User | non-root `finledger` (UID 1000) |
| Config volume | `/workspace/config` |
| Extra JVM flags | `JAVA_OPTS` (optional) |

Local Compose:

```bash
# Postgres only (default)
docker compose up -d

# Sandbox eval (no OIDC) — dumps curls to ./config/sandbox-ready.txt
docker compose --profile sandbox up -d --build

# Full stack (build image + app). Defaults: issuer=external, FINLEDGER_ENV=production.
# Override in `.env` for IdP (issuer-uri) or IdP-less (issuer=internal + container PEM path).
docker compose --profile with-app up -d --build

# Optional Prometheus + Grafana (see deploy/observability/)
docker compose --profile with-app --profile observability up -d --build
```

### Observability (FL-150)

| Item | Contract |
|------|----------|
| Traces | Micrometer Tracing + OpenTelemetry (W3C `traceparent`) |
| OTLP | Set `OTEL_EXPORTER_OTLP_ENDPOINT` / `management.opentelemetry.tracing.export.otlp.endpoint` to export; unset = no export |
| Metrics | `GET /actuator/prometheus` (public; restrict management port in prod) |
| Logs | Pattern + MDC locally; JSON under `prod` / `json-logs` |
| Sampling | `1.0` default; `0.1` in `prod` |
| Compose | Profile `observability` → Prometheus `:9090`, Grafana `:3000` (admin/admin) |

See [ADR-013](adr/ADR-013-observability.md).

## Common environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `sandbox` \| `normal` |
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Datasource aliases (Compose / prod) |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | Flyway credentials (often superuser) |
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | Optional extra config locations |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | OIDC issuer (`issuer=external`) |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | JWKS URI alternative to issuer |
| `FINLEDGER_ENV` | `local` (default) or `production` — production forbids profile `sandbox` |
| `FINLEDGER_SECURITY_ISSUER` | `external` \| `internal` |
| `FINLEDGER_SECURITY_MAX_TOKEN_TTL` | Ledger max JWT lifetime (default `15m`) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Optional OTLP traces endpoint |
| `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET` | HMAC secret for inbound rail settlement webhooks |
| `FINLEDGER_RAIL_WEBHOOK_MAX_SKEW_SECONDS` | Anti-replay timestamp skew window (default `300`) |
| `FINLEDGER_RATE_LIMIT_ENABLED` | In-memory Bucket4j on `/api/v1/**` (default `true`) |
| `FINLEDGER_RATE_LIMIT_CAPACITY` / `_REFILL_PER_SECOND` | Token bucket size / refill (defaults `120` / `60`) |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | Hikari pool (default `20`; keep well under Postgres `max_connections`) |
| `spring.threads.virtual.enabled` | Virtual threads (default `true` in embedded `application.yaml`) |
| `FINLEDGER_BASE_URL` | CLI only — FinLedger API base URL (default `http://localhost:8080`) |
| `FINLEDGER_TOKEN` | CLI only — Bearer JWT for `/api/v1` |
| `FINLEDGER_MANAGEMENT_URL` | CLI only — actuator base (default `http://localhost:8081`) |
| `FINLEDGER_CLI_JAR` | CLI launcher — absolute path to shaded jar (prod) |
| `FINLEDGER_FRAUD_ENABLED` | Enable in-box rule-based risk check (`true` / default `false`) |
| `SERVER_PORT` | HTTP port (default `8080`) |
| `MANAGEMENT_SERVER_PORT` | Actuator port (image default `8081`) |
| `JAVA_OPTS` | Extra JVM flags for the container entrypoint |

Production (`FINLEDGER_ENV=production`, profile `normal`) expects secrets via env
(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, OIDC URI) — never commit real values.

## First boot without a tenant

The service must start and become healthy even if no tenant exists yet. When the
DB has zero tenants, the server logs an INFO hint pointing at
`finledger-cli tenant create` or `POST /api/v1/tenants`. Do not add a blocking
setup wizard. See [ADR-010](adr/ADR-010-cli-http-client-module.md).

## Feature / adapter toggles (future)

Plan §18 documents toggles such as tenant mode and optional adapters
(`FINLEDGER_FX_PROVIDER`, `FINLEDGER_MESSAGING_BROKER`, …). They are introduced
when the corresponding ports land — do not invent vendor-specific flags early.
