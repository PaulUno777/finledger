package com.pauluno.finledger.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ApiClientTest {

    @Test
    void should_build_url_and_inject_bearer_and_idempotency_headers() throws Exception {
        List<HttpRequest> captured = new ArrayList<>();
        ApiClient.HttpExecutor fake = request -> {
            captured.add(request);
            return new FakeResponse(201, "{\"id\":\"abc\"}");
        };

        ApiClient client = new ApiClient(
                "http://localhost:8080/",
                "jwt-token",
                "fixed-key",
                fake
        );
        String body = client.post("/api/v1/tenants", java.util.Map.of("name", "Acme", "type", "STANDALONE"));

        assertEquals(1, captured.size());
        HttpRequest req = captured.getFirst();
        assertEquals("http://localhost:8080/api/v1/tenants", req.uri().toString());
        assertEquals(Optional.of("Bearer jwt-token"), req.headers().firstValue("Authorization"));
        assertEquals(Optional.of("fixed-key"), req.headers().firstValue("Idempotency-Key"));
        assertEquals("POST", req.method());
        assertTrue(body.contains("\"id\""));
        assertEquals("fixed-key", client.idempotencyKey());
        assertEquals("http://localhost:8080", client.baseUrl());
    }

    @Test
    void should_throw_api_exception_on_non_2xx() {
        ApiClient.HttpExecutor fake = request -> new FakeResponse(403, "{\"error\":\"forbidden\"}");
        ApiClient client = new ApiClient("http://localhost:8080", "t", "k", fake);

        ApiException ex = assertThrows(ApiException.class, () ->
                client.put("/api/v1/tenants/x/fx/config", java.util.Map.of("pivotCurrencyCode", "USD")));
        assertEquals(403, ex.statusCode());
        assertTrue(ex.getMessage().contains("forbidden"));
    }

    private record FakeResponse(int status, String body) implements HttpResponse<String> {
        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public java.net.URI uri() {
            return java.net.URI.create("http://localhost");
        }

        @Override
        public java.net.http.HttpClient.Version version() {
            return java.net.http.HttpClient.Version.HTTP_1_1;
        }
    }
}
