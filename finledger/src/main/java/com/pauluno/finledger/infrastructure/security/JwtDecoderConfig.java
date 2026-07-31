package com.pauluno.finledger.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds a {@link JwtDecoder} from issuer-uri or jwk-set-uri, always wrapped with the
 * RS256/ES256 algorithm allowlist. Only active in {@code enforced} mode (FL-151).
 */
@Configuration
@ConditionalOnProperty(prefix = "finledger.security", name = "mode", havingValue = "enforced", matchIfMissing = true)
public class JwtDecoderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "issuer-uri")
    JwtDecoder jwtDecoderFromIssuer(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        return new AlgorithmAllowlistingJwtDecoder(JwtDecoders.fromIssuerLocation(issuerUri));
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = "jwk-set-uri")
    JwtDecoder jwtDecoderFromJwkSet(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithms(algorithms -> {
                    algorithms.clear();
                    algorithms.add(SignatureAlgorithm.RS256);
                    algorithms.add(SignatureAlgorithm.ES256);
                })
                .build();
        return new AlgorithmAllowlistingJwtDecoder(decoder);
    }
}
