package com.pauluno.finledger.infrastructure.security.platform;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pauluno.finledger.application.exception.PlatformBootstrapAlreadyClaimedException;
import com.pauluno.finledger.application.port.in.ClaimPlatformBootstrapUseCase;
import com.pauluno.finledger.infrastructure.security.ConstantTimeSecrets;
import com.pauluno.finledger.infrastructure.security.internal.InternalJwtIssuer;
import com.pauluno.finledger.infrastructure.security.internal.PersistentInternalIssuer;

/**
 * One-shot IdP-less cold-start (FL-158). Secret-gated; permanently dead after first claim.
 * Lives in infrastructure (like {@code InternalAuthController}) — mint is an adapter concern.
 */
@RestController
@RequestMapping("/api/v1/platform")
@Profile("!sandbox")
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "internal")
public class PlatformBootstrapController {

    private final String configuredSecret;
    private final Duration bootstrapTokenTtl;
    private final ClaimPlatformBootstrapUseCase claimUseCase;
    private final InternalJwtIssuer issuer;

    public PlatformBootstrapController(
            @Value("${finledger.platform.bootstrap-secret:}") String configuredSecret,
            @Value("${finledger.platform.bootstrap-token-ttl:15m}") Duration bootstrapTokenTtl,
            ClaimPlatformBootstrapUseCase claimUseCase,
            InternalJwtIssuer issuer
    ) {
        this.configuredSecret = configuredSecret == null ? "" : configuredSecret;
        this.bootstrapTokenTtl = bootstrapTokenTtl;
        this.claimUseCase = claimUseCase;
        this.issuer = issuer;
    }

    @PostMapping(
            value = "/bootstrap",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> bootstrap(@RequestBody(required = false) BootstrapRequest body) {
        if (configuredSecret.isBlank()) {
            throw new BootstrapDisabledException();
        }
        if (!(issuer instanceof PersistentInternalIssuer persistent)) {
            throw new BootstrapDisabledException();
        }

        String provided = body == null ? null : body.bootstrapSecret;
        if (!ConstantTimeSecrets.equals(configuredSecret, provided)) {
            throw new InvalidBootstrapSecretException();
        }

        byte[] secretHash = ConstantTimeSecrets.sha256(configuredSecret);
        UUID jti = claimUseCase.claim(secretHash);
        InternalJwtIssuer.AccessToken token = persistent.mintPlatformAdminToken(jti, bootstrapTokenTtl);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", token.value());
        response.put("token_type", "Bearer");
        response.put("expires_in", token.ttl().toSeconds());
        response.put("scope", token.scope());
        return response;
    }

    @ExceptionHandler(BootstrapDisabledException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> disabled(BootstrapDisabledException ex) {
        return Map.of("error", "bootstrap_disabled", "error_description",
                "Platform bootstrap is not configured (set FINLEDGER_PLATFORM_BOOTSTRAP_SECRET)");
    }

    @ExceptionHandler(InvalidBootstrapSecretException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> unauthorized(InvalidBootstrapSecretException ex) {
        return Map.of("error", "invalid_bootstrap_secret", "error_description",
                "Invalid bootstrap_secret");
    }

    @ExceptionHandler(PlatformBootstrapAlreadyClaimedException.class)
    @ResponseStatus(HttpStatus.GONE)
    public Map<String, String> gone(PlatformBootstrapAlreadyClaimedException ex) {
        return Map.of("error", "bootstrap_already_claimed", "error_description", ex.getMessage());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BootstrapRequest {
        @JsonProperty("bootstrap_secret")
        public String bootstrapSecret;
    }

    static final class BootstrapDisabledException extends RuntimeException {
    }

    static final class InvalidBootstrapSecretException extends RuntimeException {
    }
}
