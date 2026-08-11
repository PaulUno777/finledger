# ADR-019 — External operator CLI + platform init + reproducible baselines

- **Status:** Proposed
- **Date:** 2026-08-11
- **Deciders:** FinLedger maintainers

## Context

The Java shaded `finledger-cli` (ADR-010 / ADR-015) works but is awkward to
install (JDK + Maven reactor). Integrators need a single binary to manage,
inspect, and monitor FinLedger via the **public** Platform API.

Platform init vocabulary is easy to confuse:

| Term             | Meaning today                                                                                                      |
| ---------------- | ------------------------------------------------------------------------------------------------------------------ |
| **bootstrap**    | `POST /api/v1/platform/bootstrap` — one-shot secret → `platform:admin` JWT; second call **410** (FL-158 / ADR-016) |
| **provision**    | `POST /api/v1/platform/provision` — idempotent **root** recipe (`STANDALONE` \| `AGGREGATOR`) when absent (FL-181) |
| **sandbox init** | Eval baseline (`SandboxBootstrap` + scenario packs)                                                                |

## Decision (proposed — not implemented)

1. **CLI:** replace the Maven `finledger-cli` module with an **externally
   installable** Go (or other non-JVM) binary that talks only to `/api/v1` plus
   optional local Compose wrappers. Deprecate the Java CLI after parity
   (`jwt inspect`, `platform provision`, `account list`, ops).
2. **Do not redefine bootstrap** as an idempotent seed. Keep FL-158 as claim
   control-plane credential. Day-0 seed is **provision**.
3. **Sandbox / baselines:** pick **one** story — (A) seed-on-boot ≡ provision
   already applied, or (B) empty sandbox + CLI provision with version-controlled
   reference IDs/dates. Never both. Ticket FL-182 resets the clean sandbox path
   before release; FL-183 asserts sandbox ↔ provision recipe parity.
4. Layering stays **core → Platform API → external CLI**. No mint-in-prod.

## Alternatives considered

| Option                                        | Why not (yet)                                      |
| --------------------------------------------- | -------------------------------------------------- |
| Keep Java CLI forever                         | Install DX remains the integrator tax              |
| Merge bootstrap + provision into one endpoint | Collides with 410 ceremony vs create-if-not-exists |
| Implement Go CLI in the same PR as FL-181     | Blocks integrator-hardening                        |

## Consequences

- Positive: honest vocabulary; installable ops client later
- Follow-ups: FL-182, FL-183, FL-184, FL-185 (see [development.md](../development.md))

## References

- [ADR-010](ADR-010-cli-http-client-module.md), [ADR-015](ADR-015-operational-model.md), [ADR-016](ADR-016-runtime-profiles-jwt-issuer.md)
- [INTEGRATION_GUIDE.md](../INTEGRATION_GUIDE.md) §5 Token Profile
