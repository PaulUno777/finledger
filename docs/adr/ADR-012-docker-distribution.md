# ADR-012 — Docker distribution and release-on-tag

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Plan §18 / roadmap FL-140 requires a frictionless distribution path: an official
container image with layered configuration (defaults → optional file mount → env),
CI validation of the image build, and multi-arch publication to Docker Hub on
semver tags. Mutation testing (PIT) is listed in §18.2 for PR CI, but the repo has
no PIT baseline or threshold yet.

## Decision

1. Ship a root multi-stage `Dockerfile` (Temurin JDK 21 build → Temurin JRE 21
   Alpine runtime) using Spring Boot layertools extract, non-root user
   `finledger` (UID/GID 1000), ports `8080` / `8081`, healthcheck on
   `GET /actuator/health` at `8081`, and `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/workspace/config/`.
2. `ci.yml` keeps `./mvnw -B verify` and adds a **Docker build without push** after
   verify succeeds.
3. `release-docker.yml` runs on git tags `v*.*.*`: Buildx multi-arch
   (`linux/amd64`, `linux/arm64`), push
   `${DOCKERHUB_USERNAME}/finledger:<semver>` and `:latest`, then create a GitHub
   Release from Conventional Commit subjects since the previous tag.
4. Compose keeps Postgres on the default profile; the server image is
   under Compose profile `with-app` so local DB-only workflows stay unchanged.
5. **PIT mutation gating is deferred to FL-170 (hardening).** Introducing a
   threshold without a measured domain baseline would either fail CI arbitrarily
   or ship a vacuous gate. This ADR records the deferral explicitly so §18.2 is
   not silently weakened.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Push image on every `main` merge | Noisy; tags are the release contract |
| Separate CLI image in this phase | CLI is a shaded jar (FL-120); not required for §18 server distribution |
| Enable PIT with threshold 0 / report-only | Ceremony without a fail gate; better done once baseline exists in FL-170 |
| Embed Keycloak in Compose | Violates non-presumption; operators bring their own OIDC issuer |

## Consequences

- Positive: evaluators can `docker compose --profile with-app up --build` (with
  OIDC env) or pull Hub images after a tag; CI catches broken Dockerfiles early
- Trade-off: app container still requires issuer/JWKS at boot (same as local);
  health is public only after the process starts
- Follow-up: FL-170 adds PIT on `domain` with a real threshold; FL-150 observability
  builds on actuator already exposed on `8081` in the image
- **FL-170 delivered:** PIT on `com.pauluno.finledger.domain.**` with
  `mutationThreshold` 48 (measured baseline ~50%; fail gate on `mvn verify`).
  Re-measure and raise the floor when domain tests kill more mutants.
