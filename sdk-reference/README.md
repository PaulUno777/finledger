# FinLedger `/sdk-reference/` (non-official)

**This is not an official SDK.** There is no SemVer guarantee, no Maven Central
publication, and no support SLA. It documents copy-paste client patterns that
match the server’s idempotency, rail webhook HMAC, safe retries, and W3C
`traceparent` conventions.

Official multi-language SDKs are roadmap **FL-180** (separate repos).

Integration contract: [docs/INTEGRATION_GUIDE.md](../docs/INTEGRATION_GUIDE.md).
OpenAPI path inventory (CI drift gate): [docs/contracts/openapi-paths.json](../docs/contracts/openapi-paths.json).

## Module

| Class | Role |
|-------|------|
| `IdempotencyKeys` | UUID key + SHA-256 body fingerprint (“reuse key only with same body”) |
| `WebhookHmac` | `HMAC-SHA256(timestamp + "." + nonce + "." + body)` — parity with server `RailWebhookHmac` |
| `RetryPolicy` | Exponential backoff + jitter; retry `5xx`/connection; optional `408`/`429`; never other `4xx` |
| `Traceparent` | Generate / continue W3C `traceparent` |
| `FinLedgerHttp` | Thin JDK `HttpClient` POST helper (Bearer + Idempotency-Key + traceparent + retry) |

Package: `com.pauluno.finledger.sdkref`. Dependencies: JDK 21 `HttpClient` only
(no Spring; no dependency on the `finledger` server module).

```bash
./mvnw -pl sdk-reference test
```

## Snippets

### Idempotent POST

```java
String body = "{\"transactionReference\":\"tx-1\", ...}";
String key = IdempotencyKeys.newKey();
// store key + IdempotencyKeys.bodyFingerprint(body) if you may retry later
HttpResponse<String> res = client.postJson(
        "/api/v1/tenants/" + tenantId + "/journal-entries",
        body,
        key,
        Traceparent.generate());
```

### Verify inbound rail webhook HMAC

```java
boolean ok = WebhookHmac.matches(
        secret,
        request.header(WebhookHmac.HEADER_TIMESTAMP),
        request.header(WebhookHmac.HEADER_NONCE),
        rawBody,
        request.header(WebhookHmac.HEADER_SIGNATURE));
```

Headers: `X-Finledger-Timestamp`, `X-Finledger-Nonce`, `X-Finledger-Signature`.
