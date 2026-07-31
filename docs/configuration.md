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
# Fastest eval path (no IdP) — see ADR-014
docker compose --profile sandbox up -d --build
# copy-paste curls from config/sandbox-ready.txt or app logs

# Or OIDC-enforced local run:
docker compose up -d
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/realms/finledger
./mvnw -pl finledger spring-boot:run
```

`application-local.yml` / `application-test.yml` point at Compose Postgres/Redis.
Flyway migrates as superuser `finledger`; the app connects as non-superuser
`finledger_app` / `finledger` so Postgres FORCE RLS is enforced (superusers bypass RLS).
Those credentials are **dev-only**.

### Security modes (FL-151 / ADR-014)

| Mode | Property | Use |
|------|----------|-----|
| `enforced` (default) | `finledger.security.mode=enforced` | Production / OIDC (ADR-008) |
| `static-token` | `static-token` + `FINLEDGER_STATIC_TOKEN` | CI / early integration |
| `disabled` | `disabled` | Local sandbox only |

**Interlock:** boot fails if mode ≠ `enforced` when `FINLEDGER_ENV=production` or
profiles include `prod`.

CLI (local YAML only):

```bash
./mvnw -pl finledger-cli exec:java -- config init --mode disabled
./mvnw -pl finledger-cli exec:java -- config validate
```

### AuthN / AuthZ (OIDC — `enforced`)

| Item | Contract |
|------|----------|
| Algorithms | JWT `alg` must be `RS256` or `ES256` |
| Scopes | `ledger:read`, `ledger:write`, `ledger:admin` |
| Tenant binding | Claim `tenant_id` (UUID) must match `/api/v1/tenants/{tenantId}/…` |
| Create tenant | `POST /api/v1/tenants` requires `ledger:admin` |
| Public | `/actuator/health`, `/actuator/prometheus`; settlement webhooks |
| TLS | Terminate TLS 1.3 at the reverse proxy / load balancer in front of the service |

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
# Postgres + Redis only (default)
docker compose up -d

# Sandbox eval (no OIDC) — dumps curls to ./config/sandbox-ready.txt
docker compose --profile sandbox up -d --build

# Full stack with OIDC enforced (build image + app). Put OIDC issuer in `.env` first.
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
| `SPRING_PROFILES_ACTIVE` | e.g. `test`, `prod` |
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Prod-profile aliases for datasource |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | Flyway credentials (often superuser) |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port |
| `REDIS_HOST` / `REDIS_PORT` | Prod-profile aliases for Redis |
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | Optional extra config locations |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | OIDC issuer (preferred; `enforced` mode) |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | JWKS URI alternative to issuer |
| `FINLEDGER_ENV` | `local` (default) or `production` — production forbids non-enforced modes |
| `FINLEDGER_SECURITY_MODE` | `enforced` \| `static-token` \| `disabled` (ADR-014) |
| `FINLEDGER_STATIC_TOKEN` | Shared Bearer when `mode=static-token` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Optional OTLP traces endpoint (maps to `management.opentelemetry.tracing.export.otlp.endpoint`) |
| `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET` | HMAC secret for inbound rail settlement webhooks |
| `FINLEDGER_BASE_URL` | CLI only — FinLedger API base URL (default `http://localhost:8080`) |
| `FINLEDGER_TOKEN` | CLI only — Bearer JWT for `/api/v1` (needs `ledger:admin` to create tenants) |
| `FINLEDGER_FRAUD_ENABLED` | Enable in-box rule-based risk check (`true` / default `false`) |
| `SERVER_PORT` | HTTP port (default `8080`) |
| `MANAGEMENT_SERVER_PORT` | Actuator port (image default `8081`) |
| `JAVA_OPTS` | Extra JVM flags for the container entrypoint |

Production profile (`application-prod.yml`) expects secrets via env (`DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`) — never commit real values.

## First boot without a tenant

The service must start and become healthy even if no tenant exists yet. When the
DB has zero tenants, the server logs an INFO hint pointing at
`finledger-cli tenant create` or `POST /api/v1/tenants`. Do not add a blocking
setup wizard. See [ADR-010](adr/ADR-010-cli-http-client-module.md).

## Feature / adapter toggles (future)

Plan §18 documents toggles such as tenant mode and optional adapters
(`FINLEDGER_FX_PROVIDER`, `FINLEDGER_MESSAGING_BROKER`, …). They are introduced
when the corresponding ports land — do not invent vendor-specific flags early.
