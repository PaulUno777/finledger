# ADR-011 — Fraud as optional bounded context behind TransactionRiskCheckPort

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Plan §17 requires fraud/risk as a separate, activable bounded context so scoring
rules and HOLD routing do not live inside double-entry invariants. The ledger
must keep posting correct when fraud is off (non-presumption).

## Decision

1. Sync gate: `TransactionRiskCheckPort` called from `PostTransaction` /
   `PostSplit` **before** `JournalEntry.create`. Outcomes `ALLOW` / `DENY` /
   `REVIEW`. `DENY` → `BusinessRuleException(RISK_DENIED)`.
2. Default bean: `NoOpTransactionRiskCheck` (`finledger.fraud.enabled=false`,
   matchIfMissing). In-box `RuleBasedTransactionRiskCheck` when enabled
   (amount threshold, velocity, owner denylist).
3. Fail mode per tenant: `OPEN` (default on port errors) / `CLOSED`.
4. Async: decorate `EventPublisher` to run `AsyncFraudHandler` on
   `TransactionPosted`; optional HOLD journal into configured
   `SUSPENSE_HOLD`/`RESERVE_HOLD` via `HoldFundsForReviewUseCase` (skips risk
   re-entry).
5. Persist `risk_decision` + `tenant_fraud_config` with RLS. REST under
   `/api/v1/tenants/{id}/fraud/...`.
6. Packaging stays in the `finledger` server module for the in-box engine;
   vendor/ML adapters may later be optional Maven modules.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Rules inside `DoubleEntryValidator` | Couples risk policy to accounting invariants |
| Separate `finledger-fraud` Maven module in v1 | Extra reactor complexity for a small in-box engine |
| Always-on DENY fail-closed | Blocks adopters who have not configured fraud |

## Consequences

- Positive: core posts with fraud off; adopters enable rules without domain changes
- Trade-off: CLI does not yet expose fraud config (use REST)
- Follow-up: external fraud engines behind the same port (FL-180-era adapters)
