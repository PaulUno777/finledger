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
docker compose up -d
# Required for FL-100 — point at any OIDC issuer (or JWKS URI):
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://your-idp/realms/finledger
./mvnw -pl finledger spring-boot:run
```

`application-local.yml` / `application-test.yml` point at Compose Postgres/Redis.
Flyway migrates as superuser `finledger`; the app connects as non-superuser
`finledger_app` / `finledger` so Postgres FORCE RLS is enforced (superusers bypass RLS).
Those credentials are **dev-only**.

### AuthN / AuthZ (OIDC)

| Item | Contract |
|------|----------|
| Algorithms | JWT `alg` must be `RS256` or `ES256` |
| Scopes | `ledger:read`, `ledger:write`, `ledger:admin` |
| Tenant binding | Claim `tenant_id` (UUID) must match `/api/v1/tenants/{tenantId}/…` |
| Create tenant | `POST /api/v1/tenants` requires `ledger:admin` |
| Public | `/actuator/health` only |
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

In containers, the conventional mount is `/workspace/config/` (see Docker notes in
the plan §18.1; full image contract arrives in FL-140).

## Common environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | e.g. `test`, `prod` |
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port |
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | Optional extra config locations |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | OIDC issuer (preferred) |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | JWKS URI alternative to issuer |
| `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET` | HMAC secret for inbound rail settlement webhooks |
| `FINLEDGER_BASE_URL` | CLI only — FinLedger API base URL (default `http://localhost:8080`) |
| `FINLEDGER_TOKEN` | CLI only — Bearer JWT for `/api/v1` (needs `ledger:admin` to create tenants) |
| `FINLEDGER_FRAUD_ENABLED` | Enable in-box rule-based risk check (`true` / default `false`) |
| `SERVER_PORT` | HTTP port (default `8080`) |

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
