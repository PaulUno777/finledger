# ADR-004 — Exchange rate provider chain

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Multi-currency posting needs a reliable rate at posting time. The rate must be
frozen on the journal entry (never recalculated later). Tenants need overrides,
an optional external feed, and a last-known fallback when externals fail.

Hard-wiring ECB/OpenExchangeRates into the core would violate the non-presumption
principle (plan §2.3).

## Decision

1. Port `ExchangeRateProvider.getRate(tenantId, pair, asOf)` (tenant id added vs
   the sketch in §4.1 so resolution works outside HTTP path context).
2. In-box composite chain in infrastructure:
   - DB **override** (`fx_rate_override` + `tenant_fx_config` with spread bps)
   - **External** via `ExternalRateClient` (default no-op; circuit breaker with
     Resilience4j around the call)
   - **Fallback** in-memory `RateCache` (stale flag set)
3. Persist optional `rate_used` / `rate_source` / `rate_timestamp` on
   `journal_entry` when the post request includes an `exchange` hint.
4. Domain `ExchangeOperation` is the only legal cross-currency `Money` conversion.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Recalculate historical rates on read | Breaks auditability (plan §4.1) |
| Hard-code OpenExchangeRates in core | Violates non-presumption |
| Live recursive vendor SDK in application | Keep Resilience4j in infrastructure only |

## Consequences

- Positive: overrides work with zero external infra; vendors plug in later
- Trade-off: default external client is no-op — tenants must configure overrides
  (or enable a future external adapter) before FX-stamped posts succeed
- Follow-up: optional HTTP external adapter module; Redis `RateCache`

## References

- `docs/PLAN_LEDGER_FINTECH.md` §4, §2.3, §19 item 6
- `CompositeExchangeRateProvider`, `V6__tenant_fx_and_rate_overrides.sql`
