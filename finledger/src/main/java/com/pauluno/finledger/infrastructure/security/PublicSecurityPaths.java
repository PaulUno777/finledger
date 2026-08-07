package com.pauluno.finledger.infrastructure.security;

/**
 * Paths that must remain reachable without a JWT (health, mint, OpenAPI UI).
 */
public final class PublicSecurityPaths {

    public static final String[] MATCHERS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/api/v1/auth/jwks",
            "/api/v1/auth/token",
            "/api/v1/platform/bootstrap",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/index.html"
    };

    private PublicSecurityPaths() {
    }
}
