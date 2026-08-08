# Security Policy

## Supported versions

FinLedger is pre-1.0 (`0.x`). The current supported release line is **`0.1.x`**
(first public tag: `v0.1.0`). Security fixes land on the active `develop` line and
are released through `main` as versions are tagged.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Report privately by emailing the maintainer associated with
[https://github.com/PaulUno777](https://github.com/PaulUno777) or by using
GitHub's private vulnerability reporting on this repository if enabled.

Include:

- A description of the issue and its impact
- Steps to reproduce or a proof of concept
- Affected versions / commit SHAs if known

You should receive an acknowledgement within a few days. We will coordinate a fix
and disclosure timeline with you.

## Hard rules for contributors

- Never commit secrets, credentials, or production connection strings
- Never log secrets (tokens, password hashes, private keys)
- Never weaken CORS, TLS, RLS, or audit-trail requirements “to make it work”
- Prefer the `SecretsProvider` port over inlining values, even for local demos
- JWT algorithms for the public API are allowlisted to **RS256** and **ES256** only
- Terminate **TLS 1.3** (minimum) at the reverse proxy / load balancer in front of FinLedger
- Tenant-scoped API calls must present a JWT whose `tenant_id` claim matches the path tenant
