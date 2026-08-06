package com.pauluno.finledger.cli;

import java.time.Duration;
import java.time.Instant;

import picocli.CommandLine.Option;

/**
 * Shared connection options for all CLI commands (FL-153 session + dry-run).
 */
public class GlobalOptions {

    /** Remint if expiry is within this window. */
    public static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    @Option(
            names = {"--base-url"},
            description = "FinLedger base URL (env: FINLEDGER_BASE_URL; default: http://localhost:8080)",
            defaultValue = "${env:FINLEDGER_BASE_URL:-http://localhost:8080}"
    )
    public String baseUrl;

    @Option(
            names = {"--token"},
            description = "Bearer JWT (env: FINLEDGER_TOKEN)",
            defaultValue = "${env:FINLEDGER_TOKEN:-}"
    )
    public String token;

    @Option(
            names = {"--idempotency-key"},
            description = "Idempotency-Key header; UUID generated when omitted on mutating calls"
    )
    public String idempotencyKey;

    @Option(
            names = {"--dry-run"},
            description = "Print mutating request (method/URL/body) without sending HTTP"
    )
    public boolean dryRun;

    /** In-memory only — never logged or written to disk. */
    public String sessionClientId;
    public String sessionClientSecret;
    public String sessionMintTenantId;
    public Instant tokenExpiresAt;

    public ApiClient apiClient() {
        return new ApiClient(baseUrl, token, idempotencyKey, dryRun);
    }

    public void acceptMint(String accessToken, long expiresInSeconds) {
        this.token = accessToken;
        if (expiresInSeconds > 0) {
            this.tokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds);
        } else {
            this.tokenExpiresAt = null;
        }
    }

    public void rememberClientCredentials(String clientId, String clientSecret, String mintTenantId) {
        this.sessionClientId = clientId;
        this.sessionClientSecret = clientSecret;
        this.sessionMintTenantId = mintTenantId;
    }

    public boolean canSilentRemint() {
        return sessionClientId != null && !sessionClientId.isBlank()
                && sessionClientSecret != null && !sessionClientSecret.isBlank();
    }

    public boolean tokenExpiringSoon() {
        if (tokenExpiresAt == null) {
            return false;
        }
        return Instant.now().plus(EXPIRY_SKEW).isAfter(tokenExpiresAt);
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public String contextBanner() {
        String tokenState = hasToken() ? "set" : "missing";
        String expiry = "";
        if (tokenExpiresAt != null) {
            long secs = Duration.between(Instant.now(), tokenExpiresAt).getSeconds();
            expiry = " expiresIn≈" + Math.max(0, secs) + "s";
        }
        return "[cli] baseUrl=" + baseUrl
                + " token=" + tokenState
                + expiry
                + " dry-run=" + dryRun
                + (canSilentRemint() ? " remint=ready" : "");
    }
}
