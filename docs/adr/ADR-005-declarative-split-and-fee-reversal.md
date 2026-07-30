# ADR-005 — Declarative split engine + FeeReversalPolicy

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Aggregators need multi-leg fee splits in one atomic journal entry (plan §5).
Formance-style Numscript is powerful but heavy for v1. Putting a full Fee Engine
in the ledger core would violate KISS and non-presumption (plan §5.2).

Refunds need an explicit policy for whether platform/tax/reserve fee legs reverse
with the principal (plan §5.3).

## Decision

1. **Declarative rules only** in-box: `SplitRule` / `SplitRuleSet` with percentages
   and a `remainderTarget`. `SplitPlanEvaluator` uses `HALF_EVEN` and assigns all
   remainder cents to `remainderTarget` so credits equal the total exactly.
2. Port `SplitPlanResolver` + in-box `DeclarativeSplitPlanResolver` (no pricing
   logic — applies stored percentages only).
3. Port-shaped domain `FeeReversalPolicy` with in-box strategies:
   - `NO_REVERSE` (default): reverse principal only; fee/tax/reserve untouched
   - `PRO_RATA`: scale every original posting by `refundAmount / originalDebitTotal`
4. Persist `tenant_split_rule_set` and `tenant_fee_config` (Flyway V7), FORCE RLS.
5. REST: configure rules/fee-config; `POST …/splits` and `POST …/refunds` with
   `Idempotency-Key`. Raw `POST …/journal-entries` remains for explicit posting lists.
6. `JournalEntryType.REFUND` + `JournalEntry.createRefund` linking `reversesEntryId`.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Numscript / DSL runtime in core | Too much engineering for v1 (plan §5) |
| Fee Engine as core bounded context | Violates KISS + non-presumption (§5.2) |
| Always reverse fees on refund | Wrong default for many aggregators; make it configurable |

## Consequences

- Positive: exact-cent splits; refund policy per tenant; pricing stays external
- Trade-off: no conditional/cascading rules in-box yet
- Follow-up: optional external REST fee adapter behind `SplitPlanResolver` (not this phase)

## References

- `docs/PLAN_LEDGER_FINTECH.md` §5, §1.2, §2.3, §19 item 7
- `V7__split_rules_and_fee_config.sql`, `SplitPlanEvaluator`, `NoReverseFeePolicy`, `ProRataFeePolicy`
