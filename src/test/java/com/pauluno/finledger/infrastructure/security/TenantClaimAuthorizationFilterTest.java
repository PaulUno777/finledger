package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Tag("unit")
class TenantClaimAuthorizationFilterTest {

    private final TenantClaimAuthorizationFilter filter = new TenantClaimAuthorizationFilter();

    @Test
    void should_forbid_when_claim_mismatches_path() throws Exception {
        UUID pathTenant = UUID.randomUUID();
        UUID claimTenant = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("u")
                .claim(TenantClaimAuthorizationFilter.TENANT_ID_CLAIM, claimTenant.toString())
                .issuedAt(Instant.parse("2020-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/tenants/" + pathTenant + "/audit/integrity");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TENANT_CLAIM_MISMATCH");
    }

    @Test
    void should_pass_create_tenant_without_claim() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("admin")
                .issuedAt(Instant.parse("2020-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        try {
            filter.doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
