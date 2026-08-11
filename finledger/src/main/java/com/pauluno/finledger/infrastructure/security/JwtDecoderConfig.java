package com.pauluno.finledger.infrastructure.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * External IdP {@link JwtDecoder}: issuer-uri or jwk-set-uri, RS256/ES256,
 * issuer aliases, max TTL (user vs machine). Empty JWKS does not fail bean
 * creation — readiness goes DOWN instead.
 */
@Configuration
@EnableConfigurationProperties(FinledgerSecurityProperties.class)
@ConditionalOnProperty(prefix = "finledger.security", name = "issuer", havingValue = "external", matchIfMissing = true)
public class JwtDecoderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "issuer-uri")
    JwtDecoder jwtDecoderFromIssuer(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            FinledgerSecurityProperties properties,
            ObjectProvider<JwksReadiness> readiness
    ) {
        String keysUri = (jwkSetUri == null || jwkSetUri.isBlank())
                ? issuerUri.replaceAll("/$", "") + "/protocol/openid-connect/certs"
                : jwkSetUri;
        // Prefer explicit jwk-set-uri when discovery issuer ≠ JWT iss (Zitadel port quirk).
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            keysUri = jwkSetUri;
        } else {
            try {
                JwtDecoder discovered = org.springframework.security.oauth2.jwt.JwtDecoders.fromIssuerLocation(issuerUri);
                return wrap(discovered, issuerUri, properties, readiness.getIfAvailable(), issuerUri);
            } catch (RuntimeException ex) {
                readiness.ifAvailable(r -> r.markFailed(issuerUri, ex.getMessage()));
            }
        }
        return wrap(nimbus(keysUri), keysUri, properties, readiness.getIfAvailable(), issuerUri);
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "jwk-set-uri")
    JwtDecoder jwtDecoderFromJwkSet(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            FinledgerSecurityProperties properties,
            ObjectProvider<JwksReadiness> readiness
    ) {
        return wrap(nimbus(jwkSetUri), jwkSetUri, properties, readiness.getIfAvailable(), issuerUri);
    }

    private static NimbusJwtDecoder nimbus(String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithms(algorithms -> {
                    algorithms.clear();
                    algorithms.add(SignatureAlgorithm.RS256);
                    algorithms.add(SignatureAlgorithm.ES256);
                })
                .build();
    }

    private static JwtDecoder wrap(
            JwtDecoder delegate,
            String keysUri,
            FinledgerSecurityProperties properties,
            JwksReadiness readiness,
            String issuerUri
    ) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        if (issuerUri != null && !issuerUri.isBlank()) {
            if (properties.getIssuerAliases().isEmpty()) {
                validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
            } else {
                validators.add(JwtValidators.createDefault());
                validators.add(new AliasedIssuerValidator(issuerUri, properties.getIssuerAliases()));
            }
        } else {
            validators.add(JwtValidators.createDefault());
        }
        validators.add(new MachineAwareMaxTokenLifetimeValidator(
                properties.getMaxTokenTtl(),
                properties.getMaxTokenTtlMachine(),
                properties.getMachineAzpAllowlist()));
        OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(validators);
        if (delegate instanceof NimbusJwtDecoder nimbus) {
            nimbus.setJwtValidator(combined);
        }
        return new AlgorithmAllowlistingJwtDecoder(token -> {
            try {
                Jwt jwt = delegate.decode(token);
                if (readiness != null) {
                    readiness.markReady(keysUri);
                }
                if (!(delegate instanceof NimbusJwtDecoder)) {
                    var result = combined.validate(jwt);
                    if (result.hasErrors()) {
                        throw new JwtException(result.getErrors().iterator().next().getDescription());
                    }
                }
                return jwt;
            } catch (JwtException ex) {
                if (readiness != null && looksLikeEmptyJwks(ex)) {
                    readiness.markEmpty(keysUri);
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (readiness != null) {
                    readiness.markFailed(keysUri, ex.getMessage());
                }
                throw ex;
            }
        });
    }

    private static boolean looksLikeEmptyJwks(JwtException ex) {
        String msg = ex.getMessage();
        return msg != null && (msg.contains("JWKS") || msg.contains("jwk") || msg.contains("Signed JWT rejected"));
    }
}
