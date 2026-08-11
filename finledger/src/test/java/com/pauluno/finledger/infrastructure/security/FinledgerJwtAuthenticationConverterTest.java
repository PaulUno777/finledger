package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@Tag("unit")
class FinledgerJwtAuthenticationConverterTest {

    @Test
    void should_alias_dotted_scope_to_colon_form() {
        FinledgerSecurityProperties properties = new FinledgerSecurityProperties();
        properties.getScopeAliases().put("ledger.write", "ledger:write");
        FinledgerJwtAuthenticationConverter converter = new FinledgerJwtAuthenticationConverter(properties);

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("m2m")
                .claim("scope", "ledger.write")
                .issuedAt(Instant.parse("2020-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
                .build();

        assertThat(converter.authorities(jwt).stream().map(GrantedAuthority::getAuthority))
                .contains("SCOPE_ledger:write");
    }

    @Test
    void should_read_custom_scope_claim() {
        FinledgerSecurityProperties properties = new FinledgerSecurityProperties();
        properties.getClaim().setScopes("roles");
        FinledgerJwtAuthenticationConverter converter = new FinledgerJwtAuthenticationConverter(properties);

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("m2m")
                .claim("roles", java.util.List.of("ledger:admin"))
                .issuedAt(Instant.parse("2020-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
                .build();

        assertThat(converter.authorities(jwt).stream().map(GrantedAuthority::getAuthority))
                .contains("SCOPE_ledger:admin");
    }
}
