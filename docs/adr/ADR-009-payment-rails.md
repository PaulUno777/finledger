# ADR-009 — Payment rails port + manual clearing + reconciliation

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

External money movement must touch a `RAIL_CLEARING` nostro/vostro account
(plan §7). Adopters bring their own PSP (mobile money, card, SWIFT); the core
must not hard-wire a vendor. Settlement reports drift from ledger reality and
need a separate reconciliation bounded context (same isolation idea as fraud).

## Decision

1. Port `RailAdapter` (`initiate` / `checkStatus`) with in-box
   `ManualRailAdapter` — local references only; settle via API or HMAC webhook.
2. `rail_instruction` persists lifecycle; initiate posts **PENDING** double-entry
   (clearing ↔ counterparty); confirm posts a **new** journal that clears PENDING
   and applies SETTLED legs — never UPDATE historical postings.
3. Reconciliation package matches settlement report lines to instructions by
   `rail_reference` and writes `reconciliation_break` rows (AMOUNT_MISMATCH,
   MISSING_INSTRUCTION, NOT_SETTLED).
4. Inbound settlement webhooks: `HMAC-SHA256(timestamp + "." + nonce + "." + body)`
   using `SecretsProvider` key `FINLEDGER_RAIL_WEBHOOK_HMAC_SECRET`; path is
   `permitAll` at the filter chain (signature replaces JWT for that endpoint).

## Alternatives considered

| Option | Why not |
|--------|---------|
| Stripe/MTN SDK in core | Violates non-presumption (§2.3) |
| UPDATE posting settlementStatus | Breaks append-only ledger |
| Full MT940 / continuous recon product | Deferred; minimal match is enough for FL-110 |
| Outbound signed webhooks | NotificationPort still no-op |

## Consequences

- Positive: real PSPs plug in behind `RailAdapter`; balances stay consistent under PENDING→SETTLED
- Trade-off: manual adapter is not a production rail — adopters must add a vendor module
- Follow-up: optional Maven rail modules; outbound HMAC notifications; richer recon tooling

## References

- `docs/PLAN_LEDGER_FINTECH.md` §7, §19 item 11
- `V9__rail_and_reconciliation.sql`, `ManualRailAdapter`, `RailSettlementReconciler`
