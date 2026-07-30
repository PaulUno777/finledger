# ADR-013 — Observability with Micrometer + OpenTelemetry

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** FinLedger maintainers

## Context

Plan §2.3 / roadmap FL-150 call for Micrometer + OpenTelemetry as the in-box
observability stack, with structured logs and local Prometheus, without binding
the core to Datadog, New Relic, or a mandatory Tempo deployment. Audit rows already
store `traceId` / `spanId` from a manual `traceparent` parse (FL-090); FL-150 must
correlate those columns with real spans.

## Decision

1. Add `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` to the
   `finledger` server module (not a separate Maven module).
2. OTLP export is **opt-in** via `OTEL_EXPORTER_OTLP_ENDPOINT` /
   `management.opentelemetry.tracing.export.otlp.endpoint`. No vendor APM SDK in core.
3. Sampling probability `1.0` by default; `0.1` under the `prod` profile.
4. `AuditableAspect` prefers Micrometer `Tracer.currentSpan()` ids, falling back to
   the existing `TraceContext` / `TraceparentFilter` path.
5. Expose `/actuator/prometheus` as `permitAll` (same posture as health). Deployers
   must network-restrict the management port (`8081` in the Docker image).
6. Business meters via port `LedgerMetrics` → `MicrometerLedgerMetrics`
   (`finledger.journal.posted`, `finledger.risk.denied`).
7. Compose profile `observability` ships Prometheus + Grafana with a provisioned
   overview dashboard; it does not start by default.

## Alternatives considered

| Option | Why not |
|--------|---------|
| Mandatory Tempo/Jaeger in Compose | Violates non-presumption; OTLP endpoint is enough for adopters |
| Datadog/New Relic agent in core | Vendor lock-in; optional later as an adapter if needed |
| Keep only manual `traceparent` parse | Insufficient for spans/metrics; deferred OTEL was always FL-150 |

## Consequences

- Positive: traces, Prometheus scrape, JSON logs, and audit correlation without
  forcing a particular APM vendor
- Trade-off: empty/misconfigured OTLP endpoint must not break boot — operators set
  the endpoint only when a collector is available
- Follow-up: FL-170 may add PIT and load/chaos; deeper SLOs/alerts stay with adopters
