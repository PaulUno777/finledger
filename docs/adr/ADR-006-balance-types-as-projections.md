# ADR-006 — Balance types as posting projections

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Aggregators need to distinguish spendable funds from in-flight settlement and
blocked (hold) amounts. Blnk-style `available` / `pending` / `held` views are
useful without inventing a second ledger (plan §6).

Domain already materializes these on `AccountBalance` via `BalanceCalculator`
and `Posting.settlementStatus`. The gap for FL-080 was API aggregation.

## Decision

1. **AVAILABLE** = sum of postings with `settlementStatus = SETTLED`.
2. **PENDING** = sum of postings with `settlementStatus = PENDING`.
3. **HELD** = on `SUSPENSE_HOLD` / `RESERVE_HOLD` accounts, `available + pending`;
   otherwise `0`. Not a separate posting stream.
4. Expose the three amounts via:
   - `GET /api/v1/tenants/{tenantId}/accounts/{accountId}/balance`
   - `GET /api/v1/tenants/{tenantId}/accounts/{accountId}` (nested fields)
   - create-account response (zeroed)
5. Overdraft / insufficient-funds checks continue to use **available** only
   (`DoubleEntryValidator`).
6. No mutate-pending-to-settled API in this phase — that belongs with payment
   rails (FL-110).

## Alternatives considered

| Option | Why not |
|--------|---------|
| Separate HELD posting ledger | Duplicates truth; hold is account-type projection |
| Single opaque balance in API | Loses Blnk-style DX required by §6 |
| Settle-pending endpoint now | Couples FL-080 to rails; deferred to FL-110 |

## Consequences

- Positive: clients see withdrawable vs in-flight vs held without new tables
- Trade-off: `account_balance` remains a rebuildable cache; truth stays postings
- Follow-up: settle PENDING → SETTLED when rail adapters land

## References

- `docs/PLAN_LEDGER_FINTECH.md` §6, §19 item 8
- `BalanceCalculator`, `AccountBalance`, `GetAccountBalanceService`
