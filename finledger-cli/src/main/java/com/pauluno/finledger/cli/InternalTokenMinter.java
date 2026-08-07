package com.pauluno.finledger.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client-credentials mint against {@code POST /api/v1/auth/token} (in-box issuer).
 */
public final class InternalTokenMinter {

    public record MintResult(String accessToken, long expiresInSeconds, String scope) {
    }

    private InternalTokenMinter() {
    }

    public static MintResult mint(
            String baseUrl,
            String clientId,
            String clientSecret,
            String tenantIdOrNull
    ) throws Exception {
        String base = trimTrailingSlash(baseUrl);
        StringBuilder body = new StringBuilder(160);
        body.append("{\"grant_type\":\"client_credentials\",\"client_id\":\"")
                .append(clientId)
                .append("\",\"client_secret\":\"")
                .append(clientSecret)
                .append('"');
        if (tenantIdOrNull != null && !tenantIdOrNull.isBlank()) {
            body.append(",\"tenant_id\":\"").append(tenantIdOrNull.trim()).append('"');
        }
        body.append('}');

        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(response.statusCode(), response.body());
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode parsed = mapper.readTree(response.body());
        String token = parsed.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Response missing access_token: " + response.body());
        }
        long expiresIn = parsed.path("expires_in").asLong(0);
        String scope = parsed.path("scope").asText("");
        return new MintResult(token, expiresIn, scope);
    }

    private static String trimTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
