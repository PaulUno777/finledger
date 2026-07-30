package com.pauluno.finledger.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Thin HTTP client for FinLedger {@code /api/v1} — JDK {@link HttpClient} + Jackson only.
 */
public final class ApiClient {

    @FunctionalInterface
    public interface HttpExecutor {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }

    private final String baseUrl;
    private final String token;
    private final String idempotencyKey;
    private final ObjectMapper mapper;
    private final HttpExecutor http;

    public ApiClient(String baseUrl, String token, String idempotencyKey) {
        this(baseUrl, token, idempotencyKey, defaultExecutor());
    }

    ApiClient(String baseUrl, String token, String idempotencyKey, HttpExecutor http) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.token = token;
        this.idempotencyKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String post(String path, Object body) throws Exception {
        return exchange("POST", path, body);
    }

    public String put(String path, Object body) throws Exception {
        return exchange("PUT", path, body);
    }

    public String putRawJson(String path, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .method("PUT", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return handle(http.send(builder.build()));
    }

    String exchange(String method, String path, Object body) throws Exception {
        String json = body == null ? "{}" : mapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return handle(http.send(builder.build()));
    }

    private String handle(HttpResponse<String> response) throws ApiException {
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        if (status >= 200 && status < 300) {
            return pretty(body);
        }
        String snippet = body.length() > 500 ? body.substring(0, 500) + "…" : body;
        throw new ApiException(status, snippet);
    }

    private String pretty(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = mapper.readTree(body);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ignored) {
            return body;
        }
    }

    private static HttpExecutor defaultExecutor() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return request -> client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
