package com.pauluno.finledger.infrastructure.security.internal;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Wires the sandbox in-box JWT issuer when {@code finledger.security.issuer=internal} (FL-155).
 */
@Configuration
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "internal")
public class InternalIssuerConfiguration {

    @Bean
    EphemeralInternalIssuer ephemeralInternalIssuer(
            @Value("${finledger.security.internal.issuer-uri:http://localhost:8080/internal/sandbox}")
            String issuerUri,
            @Value("${finledger.sandbox.client-id:sandbox}") String clientId,
            @Value("${finledger.sandbox.client-secret:}") String clientSecret,
            @Value("${finledger.security.max-token-ttl:15m}") Duration maxTokenTtl
    ) {
        String secret = (clientSecret == null || clientSecret.isBlank())
                ? generateSecret()
                : clientSecret;
        return EphemeralInternalIssuer.generate(issuerUri, clientId, secret, maxTokenTtl);
    }

    @Bean
    JwtDecoder internalJwtDecoder(EphemeralInternalIssuer issuer) {
        return issuer.jwtDecoder();
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
