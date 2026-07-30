# ADR-003 — Materialized tenant ancestry + Postgres FORCE RLS

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

FinLedger is multi-tenant with three tenant types (`STANDALONE`, `AGGREGATOR`,
`SUB_MERCHANT`). Aggregators must read descendant sub-merchant ledger data;
siblings and unrelated tenants must not. Enforcing isolation only in application
queries is easy to bypass (ad-hoc SQL, forgotten `WHERE`, reporting paths).

Recursive CTEs in every RLS policy are correct but costly and harder to reason
about under load. OIDC claim ↔ tenant binding lands later (FL-100); this phase
needs DB-enforced isolation from the path tenant id.

## Decision

1. Persist a materialized closure table `tenant_ancestry(ancestor_id, descendant_id)`,
   always including the self-row `(id, id)`, rebuilt on tenant create.
2. Enable **FORCE ROW LEVEL SECURITY** on money / idempotency / outbox tables
   (`ledger_account`, `journal_entry`, `posting`, `account_balance`,
   `idempotency_record`, `outbox_event`). Provisioning tables `tenant` /
   `tenant_ancestry` stay without RLS this phase.
3. Visibility via GUC `app.current_tenant_id` (fail closed when blank) and optional
   `app.rls_bypass=on` for internal jobs (outbox poller). Session values are set with
   `SET LOCAL` / `set_config(..., is_local=true)` from a request-scoped
   `TenantContext` immediately after the JPA transaction begins
   (`TenantAwareJpaTransactionManager`).
4. Path `/api/v1/tenants/{tenantId}/…` populates `TenantContext`; `POST /api/v1/tenants`
   provisions without a path tenant.
5. Runtime DB role is non-superuser (`finledger_app`): Docker `POSTGRES_USER` is a
   superuser and would bypass RLS even with `FORCE ROW LEVEL SECURITY`.

## Alternatives considered

| Option | Why not |
|--------|---------|
| App-only `WHERE tenant_id = ?` | Easy to miss; not defense-in-depth for a ledger |
| Live recursive CTE in every RLS check | Correct but heavier; ancestry is stable at create time |
| RLS on `tenant` tables too | Blocks simple provisioning until admin auth (FL-100) |

## Consequences

- Positive: DB enforces hierarchy-aware isolation even if a query forgets a filter
- Trade-off: every ledger TX must set the GUC (filter + aspect); jobs need explicit bypass
- Follow-up: bind OIDC claims to tenant (FL-100); optionally RLS on tenant admin APIs

## References

- `docs/PLAN_LEDGER_FINTECH.md` §3, §19 item 5
- Flyway `V5__tenant_hierarchy_and_rls.sql`, `TenantContext`, `TenantAwareJpaTransactionManager`
