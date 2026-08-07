package com.pauluno.finledger.sdkref;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thin JDK {@link HttpClient} wrapper that applies Authorization, Idempotency-Key,
 * and W3C {@code traceparent} on mutating POSTs, with {@link RetryPolicy}.
 */
public final class FinLedgerHttp {

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Supplier<String> bearerToken;
    private final RetryPolicy retryPolicy;

    public FinLedgerHttp(URI baseUri, Supplier<String> bearerToken) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                baseUri, bearerToken, RetryPolicy.defaults());
    }

    public FinLedgerHttp(
            HttpClient httpClient,
            URI baseUri,
            Supplier<String> bearerToken,
            RetryPolicy retryPolicy
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.bearerToken = Objects.requireNonNull(bearerToken, "bearerToken");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    /**
     * POST JSON under {@code baseUri} + path (e.g. {@code /api/v1/tenants/...}).
     *
     * @param path absolute path starting with {@code /}
     * @param jsonBody request body
     * @param idempotencyKey required for FinLedger mutations
     * @param traceparent optional; generated when null/blank
     */
    public HttpResponse<String> postJson(
            String path,
            String jsonBody,
            String idempotencyKey,
            String traceparent
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(jsonBody, "jsonBody");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        String tp = Traceparent.requireValidOrGenerate(traceparent);
        String absolutePath = path.startsWith("/") ? path : "/" + path;
        URI uri = URI.create(trimTrailingSlash(baseUri.toString()) + absolutePath);

        IOException lastIo = null;
        HttpResponse<String> last = null;
        for (int attempt = 0; attempt < retryPolicy.maxAttempts(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + bearerToken.get())
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", idempotencyKey)
                        .header(Traceparent.HEADER, tp)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();
                last = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (!retryPolicy.shouldRetry(attempt, last.statusCode(), false)) {
                    return last;
                }
                retryPolicy.backoff(attempt);
            } catch (IOException ex) {
                lastIo = ex;
                if (!retryPolicy.shouldRetry(attempt, 0, true)) {
                    throw ex;
                }
                retryPolicy.backoff(attempt);
            }
        }
        if (last != null) {
            return last;
        }
        throw lastIo != null ? lastIo : new IOException("retry exhausted without response");
    }

    private static String trimTrailingSlash(String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}
