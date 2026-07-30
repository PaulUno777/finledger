package com.pauluno.finledger.cli;

import picocli.CommandLine.Option;

/**
 * Shared connection options for all CLI commands.
 */
public class GlobalOptions {

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

    public ApiClient apiClient() {
        return new ApiClient(baseUrl, token, idempotencyKey);
    }
}
