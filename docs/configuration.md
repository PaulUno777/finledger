# Configuration

FinLedger uses Spring Boot's native configuration resolution in **layers**. There is
no interactive wizard at boot (wizards block health checks and orchestrated deploys).

## Resolution order (highest priority last wins)

1. **Embedded defaults** — `src/main/resources/application.yaml` (and profile files)
2. **Optional external file** — mount or place config under a directory listed in
   `spring.config.additional-location` (prefixed with `optional:` so absence is fine)
3. **Environment variables** — relaxed binding (`SPRING_DATASOURCE_URL`, etc.)
4. **Secrets** — never in YAML committed to git; use env / secret store via a
   `SecretsProvider` port when that phase lands

## Local development

```bash
docker compose up -d
./mvnw spring-boot:run
```

`application-local.yml` / `application-test.yml` point at Compose Postgres/Redis
(`finledger` / `finledger` on localhost). Those credentials are **dev-only**.

If Flyway reports a checksum mismatch after a migration file was edited, recreate
the local volume: `docker compose down -v && docker compose up -d`.

### External config file (optional)

```bash
mkdir -p ./config
# edit ./config/application.yml
export SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./config/
./mvnw spring-boot:run
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
| `SERVER_PORT` | HTTP port (default `8080`) |

Production profile (`application-prod.yml`) expects secrets via env (`DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`) — never commit real values.

## First boot without a tenant

The service must start and become healthy even if no tenant exists yet. Tenant
provisioning will use the admin API / CLI (roadmap FL-120). Do not add a blocking
setup wizard.

## Feature / adapter toggles (future)

Plan §18 documents toggles such as tenant mode and optional adapters
(`FINLEDGER_FX_PROVIDER`, `FINLEDGER_MESSAGING_BROKER`, …). They are introduced
when the corresponding ports land — do not invent vendor-specific flags early.
