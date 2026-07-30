package com.pauluno.finledger.support;

import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Supplies a {@link JwtDecoder} so the resource-server filter chain starts in tests.
 * MockMvc requests should use {@link TestJwtAuth} ({@code jwt()} post-processor) rather
 * than real Bearer tokens against this decoder.
 */
@TestConfiguration
public class IntegrationTestSecurityConfig {

    @Bean
    @Primary
    JwtDecoder integrationTestJwtDecoder() {
        return token -> {
            throw new JwtException(
                    "Integration tests must authenticate via MockMvc jwt() post-processor, not raw Bearer tokens"
            );
        };
    }
}
