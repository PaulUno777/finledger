# ADR-018 — Parent aggregator admin may provision direct-child accounts

- **Status:** Accepted
- **Date:** 2026-08-11
- **Deciders:** FinLedger maintainers

## Context

Aggregator day-0 creates a `SUB_MERCHANT` with `platform:admin` (no `tenant_id`),
then must create wallets on `/api/v1/tenants/{child}/accounts`. A
`platform:admin` token cannot call data-plane routes (ADR-016). A tenant-scoped
token with `tenant_id` = parent fails `TENANT_CLAIM_MISMATCH` on the child path.
Requiring a third IdP token scoped to a tenant that just appeared is an
egg-and-chicken problem for machine-to-machine integrators.

Hierarchy is already a first-class server fact (`parentTenantId`,
`INVALID_TENANT_HIERARCHY`).

## Decision

1. A JWT with scope **`ledger:admin`** and `tenant_id` = an **`AGGREGATOR`**
   tenant may call **account routes only** under a **direct** `SUB_MERCHANT`
   child (`parentTenantId` equals the claim):
   - `GET/POST /api/v1/tenants/{child}/accounts`
   - `GET /api/v1/tenants/{child}/accounts/{accountId}`
   - `GET /api/v1/tenants/{child}/accounts/{accountId}/balance`
2. Money paths (rails, settle, refunds, journals, splits) still require
   `tenant_id` = path. Parent admin on those child routes → `403 TENANT_CLAIM_MISMATCH`.
3. **STANDALONE** tokens never get a hierarchy exception (no parent, no children).
4. Grandchildren and non-direct descendants are forbidden.
5. `ledger:write` without `ledger:admin` does **not** get the exception.
6. `platform:admin` stays control-plane only — this is **not** option A
   (global account writes).

## Alternatives considered

| Option                                             | Why not                                                                 |
| -------------------------------------------------- | ----------------------------------------------------------------------- |
| Docs-only two tokens (B)                           | Leaves SUB_MERCHANT wallet provisioning unsolved without a token-broker |
| `platform:admin` may write any tenant accounts (A) | Collapses control-plane into a superuser; contradicts ADR-016           |
| Parent may call money paths on children            | Weakens tenant isolation on the hardest surface                         |

## Consequences

- Positive: aggregator day-0 works with two tokens (platform + parent admin)
- Trade-off: filter consults `TenantRepository` on mismatched account routes
- Follow-ups: Token Profile in [INTEGRATION_GUIDE.md](../INTEGRATION_GUIDE.md) §5;
  tests in `ParentAdminChildAccountIntegrationTest`

## References

- [ADR-016](ADR-016-runtime-profiles-jwt-issuer.md) (control-plane vs data-plane)
- `docs/PLAN_LEDGER_FINTECH.md` §3, §21
- Ticket FL-181
