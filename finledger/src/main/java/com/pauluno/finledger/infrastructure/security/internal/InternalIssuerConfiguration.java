package com.pauluno.finledger.infrastructure.security.internal;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Wires in-box JWT issuer when {@code finledger.security.issuer=internal} (FL-155 / FL-156).
 */
@Configuration
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "internal")
@EnableConfigurationProperties({
        InternalIssuerProperties.class,
        com.pauluno.finledger.infrastructure.security.FinledgerSecurityProperties.class
})
public class InternalIssuerConfiguration {

    @Bean
    @Profile("sandbox")
    InternalJwtIssuer internalJwtIssuer(
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
    @Profile("!sandbox")
    InternalJwtIssuer persistentInternalJwtIssuer(
            InternalIssuerProperties properties,
            @Value("${finledger.security.max-token-ttl:15m}") Duration maxTokenTtl
    ) {
        String pem = resolveSigningKeyPem(properties);
        List<InternalClientCredentials> clients = properties.getClients().stream()
                .map(InternalIssuerProperties.Client::toCredentials)
                .toList();
        return PersistentInternalIssuer.fromPemAndClients(
                properties.getIssuerUri(),
                pem,
                properties.getPublicKeyPem(),
                clients,
                maxTokenTtl);
    }

    @Bean
    JwtDecoder internalJwtDecoder(InternalJwtIssuer issuer) {
        return issuer.jwtDecoder();
    }

    private static String resolveSigningKeyPem(InternalIssuerProperties properties) {
        String pem = properties.getSigningKeyPem();
        if (pem != null && !pem.isBlank()) {
            return pem;
        }
        String path = properties.getSigningKeyPath();
        if (path != null && !path.isBlank()) {
            return PersistentInternalIssuer.readPemFile(Path.of(path));
        }
        throw new IllegalStateException(
                "normal+internal issuer requires finledger.security.internal.signing-key-pem "
                        + "or signing-key-path (FINLEDGER_INTERNAL_SIGNING_KEY_PEM / "
                        + "FINLEDGER_INTERNAL_SIGNING_KEY_PATH)");
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
