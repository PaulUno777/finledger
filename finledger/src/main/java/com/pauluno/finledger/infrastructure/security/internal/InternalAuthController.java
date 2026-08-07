package com.pauluno.finledger.infrastructure.security.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pauluno.finledger.application.port.out.TenantRepository;

/**
 * In-box mint + JWKS (sandbox ephemeral or normal persistent). See docs/auth-integration.md.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "internal")
public class InternalAuthController {

    private final InternalJwtIssuer issuer;
    private final ObjectProvider<TenantRepository> tenantRepository;

    public InternalAuthController(
            InternalJwtIssuer issuer,
            ObjectProvider<TenantRepository> tenantRepository
    ) {
        this.issuer = issuer;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return issuer.jwks();
    }

    @PostMapping(
            value = "/token",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> token(
            @RequestBody(required = false) TokenRequest jsonBody,
            @RequestParam(value = "grant_type", required = false) String formGrantType,
            @RequestParam(value = "client_id", required = false) String formClientId,
            @RequestParam(value = "client_secret", required = false) String formClientSecret,
            @RequestParam(value = "tenant_id", required = false) String formTenantId
    ) {
        String grantType = firstNonBlank(jsonBody == null ? null : jsonBody.grantType, formGrantType);
        String clientId = firstNonBlank(jsonBody == null ? null : jsonBody.clientId, formClientId);
        String clientSecret = firstNonBlank(jsonBody == null ? null : jsonBody.clientSecret, formClientSecret);
        String tenantRaw = firstNonBlank(jsonBody == null ? null : jsonBody.tenantId, formTenantId);

        if (grantType != null && !grantType.isBlank() && !"client_credentials".equals(grantType)) {
            throw new UnsupportedGrantTypeException("Only grant_type=client_credentials is supported");
        }

        UUID tenantOverride = parseTenantId(tenantRaw);
        if (issuer instanceof EphemeralInternalIssuer && tenantOverride != null) {
            TenantRepository repo = tenantRepository.getIfAvailable();
            if (repo == null || repo.findById(tenantOverride).isEmpty()) {
                throw new UnknownSandboxTenantException(
                        "Unknown sandbox tenant_id: " + tenantOverride);
            }
        }

        InternalJwtIssuer.AccessToken accessToken =
                issuer.mintAccessToken(clientId, clientSecret, tenantOverride);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", accessToken.value());
        body.put("token_type", "Bearer");
        body.put("expires_in", accessToken.ttl().toSeconds());
        body.put("scope", accessToken.scope());
        return body;
    }

    @ExceptionHandler(InvalidClientCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> invalidClient(InvalidClientCredentialsException ex) {
        return Map.of("error", "invalid_client", "error_description", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedGrantTypeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badGrant(UnsupportedGrantTypeException ex) {
        return Map.of("error", "unsupported_grant_type", "error_description", ex.getMessage());
    }

    @ExceptionHandler(TenantIdNotAllowedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> tenantIdNotAllowed(TenantIdNotAllowedException ex) {
        return Map.of("error", "tenant_id_not_allowed", "error_description", ex.getMessage());
    }

    @ExceptionHandler(UnknownSandboxTenantException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> unknownTenant(UnknownSandboxTenantException ex) {
        return Map.of("error", "unknown_tenant", "error_description", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", "invalid_request", "error_description", ex.getMessage());
    }

    private static UUID parseTenantId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("tenant_id must be a UUID");
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class TokenRequest {
        @JsonProperty("grant_type")
        public String grantType;
        @JsonProperty("client_id")
        public String clientId;
        @JsonProperty("client_secret")
        public String clientSecret;
        @JsonProperty("tenant_id")
        public String tenantId;
    }

    static final class UnsupportedGrantTypeException extends RuntimeException {
        UnsupportedGrantTypeException(String message) {
            super(message);
        }
    }
}
