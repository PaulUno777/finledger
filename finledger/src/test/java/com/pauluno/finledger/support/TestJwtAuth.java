package com.pauluno.finledger.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.pauluno.finledger.infrastructure.security.LedgerAuthorities;
import com.pauluno.finledger.infrastructure.security.TenantClaimAuthorizationFilter;

/**
 * Shared MockMvc JWT helpers for integration tests (FL-100).
 */
public final class TestJwtAuth {

    private TestJwtAuth() {
    }

    public static RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(builder -> builder
                        .subject("admin")
                        .claim("scope", "ledger:admin"))
                .authorities(new SimpleGrantedAuthority(LedgerAuthorities.SCOPE_LEDGER_ADMIN));
    }

    public static RequestPostProcessor platformAdminJwt() {
        return jwt()
                .jwt(builder -> builder
                        .subject("platform-bootstrap")
                        .claim("scope", LedgerAuthorities.PLATFORM_ADMIN))
                .authorities(new SimpleGrantedAuthority(LedgerAuthorities.SCOPE_PLATFORM_ADMIN));
    }

    public static RequestPostProcessor tenantJwt(UUID tenantId, String... scopes) {
        String scopeClaim = String.join(" ", scopes);
        SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
        for (int i = 0; i < scopes.length; i++) {
            authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
        }
        return jwt()
                .jwt(builder -> builder
                        .subject("tester")
                        .claim(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM, tenantId.toString())
                        .claim("scope", scopeClaim))
                .authorities(authorities);
    }

    public static RequestPostProcessor tenantReadWriteJwt(UUID tenantId) {
        return tenantJwt(tenantId, "ledger:read", "ledger:write");
    }

    public static RequestPostProcessor tenantReadJwt(UUID tenantId) {
        return tenantJwt(tenantId, "ledger:read");
    }
}
