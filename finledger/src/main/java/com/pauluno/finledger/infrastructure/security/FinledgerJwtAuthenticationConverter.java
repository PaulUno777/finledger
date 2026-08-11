package com.pauluno.finledger.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class FinledgerJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final FinledgerSecurityProperties properties;

    public FinledgerJwtAuthenticationConverter(FinledgerSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt));
    }

    Collection<GrantedAuthority> authorities(Jwt jwt) {
        Set<String> scopes = new HashSet<>();
        String claimName = properties.getClaim().getScopes();
        Object raw = jwt.getClaim(claimName);
        if (raw == null && !"scope".equals(claimName)) {
            raw = jwt.getClaim("scope");
        }
        if (raw instanceof String s) {
            for (String part : s.split("[\\s,]+")) {
                if (!part.isBlank()) {
                    scopes.add(normalize(part));
                }
            }
        } else if (raw instanceof Collection<?> c) {
            for (Object item : c) {
                if (item != null) {
                    scopes.add(normalize(item.toString()));
                }
            }
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        return authorities;
    }

    private String normalize(String scope) {
        String trimmed = scope.trim();
        return properties.getScopeAliases().getOrDefault(trimmed, trimmed);
    }
}
