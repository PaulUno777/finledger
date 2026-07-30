## Summary

Briefly describe what this PR does and why.

## Ticket

`FL-XXX` / roadmap phase:

## Checklist

- [ ] Tests added or updated for the behavior change
- [ ] ArchUnit still green (`domain` remains framework-free; rules under `finledger/src/test/.../architecture/`)
- [ ] No secrets committed; CORS/RLS/audit not weakened for convenience
- [ ] `docs/PLAN_LEDGER_FINTECH.md` and/or ADR updated if this is structural
- [ ] Conventional Commits; one concern per commit
- [ ] Out of scope for this phase left for later tickets

## Test plan

- [ ] `./mvnw -B test` from the reactor root (runs `finledger` + `finledger-cli`)
- [ ] Scoped runs when useful: `./mvnw -pl finledger test` or `./mvnw -pl finledger-cli test`
- [ ] Integration / Testcontainers (if this phase touches persistence or messaging)
- [ ] Boot smoke / manual check (if applicable): `./mvnw -pl finledger spring-boot:run`
