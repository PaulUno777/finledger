# Integrating FinLedger — guide for fintech CTOs

> **Status:** Outline living document. Finalize after step-by-step validation of
> FL-151 → FL-170 (see [development.md](development.md)). Until then, treat
> commands and env names as authoritative when they match
> [configuration.md](configuration.md) and ADRs.

This guide is written for a CTO or platform lead evaluating FinLedger as a
**self-hosted double-entry ledger** inside an existing payments / banking stack.

## 1. What you get / what you bring

| FinLedger provides | You provide |
|--------------------|-------------|
| Append-only double-entry journal, multi-tenant RLS, idempotency, outbox | Postgres (+ Redis for current cache/session needs) |
| REST `/api/v1` + OpenAPI | Your OIDC IdP (production `enforced` mode) |
| Optional fraud/rails/FX adapters behind ports | Your payment rails, secrets store, broker (optional) |
| Compose sandbox for eval | Network, TLS termination, observability backend |

**Non-presumption:** we do not hard-wire Kafka, Keycloak, Vault, or a PSP. See plan §2.3.

## 2. Evaluation path (one sitting)

```bash
git clone https://github.com/PaulUno777/finledger.git
cd finledger
cp finledger.env.example .env   # when FL-152 lands; until then use docs/configuration.md
docker compose --profile sandbox up -d --build
# read config/sandbox-ready.txt — post a journal without an IdP
```

Security modes: [ADR-014](adr/ADR-014-security-modes.md). Never run `disabled` /
`static-token` with `prod` / `FINLEDGER_ENV=production`.

## 3. Production integration checklist

1. **Image:** pull `${DOCKERHUB_USERNAME}/finledger:<semver>` or build from tag.
2. **Mode:** `FINLEDGER_SECURITY_MODE=enforced` + issuer/JWKS.
3. **Scopes:** `ledger:read` | `ledger:write` | `ledger:admin`; claim `tenant_id`.
4. **Tenants:** create via CLI or `POST /api/v1/tenants` (admin).
5. **Accounts / postings:** always send `Idempotency-Key` on mutations.
6. **FX / splits / rails:** configure per tenant; freeze rates into journal.
7. **Audit:** hash-chained `audit_log`; verify integrity endpoint/job.
8. **Observability:** scrape `:8081/actuator/prometheus`; optional OTLP.
9. **Secrets:** env / KMS via `SecretsProvider` — never bake into the image.
10. **Swagger:** disable in prod; use CLI + OpenAPI artifact for operators.

## 4. Mapping to your architecture

```text
Your API / BFF / workers
        │  HTTPS + Idempotency-Key + Bearer JWT
        ▼
FinLedger (Docker / K8s)
        │  JDBC
        ▼
Your Postgres (RLS enabled)
```

Optional adapters: FX provider, rail PSP, Vault, Kafka outbox publisher, fraud rules.

## 5. Failure modes you must design for

- Idempotent retries (same key + same body → replay; body change → conflict)
- Outbox lag if your event consumer is down
- Fraud fail-open vs fail-closed per tenant
- Multi-currency only through audited exchange operations

## 6. Security review questions

- Is TLS 1.3 terminated at the edge?
- Are JWT algs restricted to RS256/ES256?
- Is `tenant_id` claim bound to path tenants?
- Are sandbox modes impossible in production (code interlock)?
- Is management port (`8081`) not public on the internet?

## 7. Next steps after eval

- Contract tests / `/sdk-reference/` patterns (FL-160)
- Hardening / load / chaos (FL-170)
- Official multi-lang SDKs only if adoption warrants (FL-180)

## 8. Where to look in-repo

| Topic | Doc |
|-------|-----|
| Product rules | [PLAN_LEDGER_FINTECH.md](PLAN_LEDGER_FINTECH.md) |
| Ops model | [ADR-015](adr/ADR-015-operational-model.md) |
| Config keys | [configuration.md](configuration.md) |
| Security modes | [ADR-014](adr/ADR-014-security-modes.md) |
| Docker | [ADR-012](adr/ADR-012-docker-distribution.md) |
| CLI | [ADR-010](adr/ADR-010-cli-http-client-module.md) |

---

*Final CTO runbook (copy-paste env, K8s snippets, go-live checklist) will be
completed at the end of the step-by-step validation track (FL-190).*
