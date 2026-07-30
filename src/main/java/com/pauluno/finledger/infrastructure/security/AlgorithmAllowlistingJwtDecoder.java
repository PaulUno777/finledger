package com.pauluno.finledger.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Rejects JWTs whose JOSE {@code alg} is outside the RS256/ES256 allowlist (plan §11)
 * before delegating signature verification to the wrapped decoder.
 */
public final class AlgorithmAllowlistingJwtDecoder implements JwtDecoder {

    static final Set<String> ALLOWED_ALGORITHMS = Set.of("RS256", "ES256");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtDecoder delegate;

    public AlgorithmAllowlistingJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String alg = readAlg(token);
        if (!ALLOWED_ALGORITHMS.contains(alg)) {
            throw new BadJwtException("JWT alg '" + alg + "' is not allowed; only RS256 and ES256 are accepted");
        }
        return delegate.decode(token);
    }

    static String readAlg(String token) {
        if (token == null || token.isBlank()) {
            throw new BadJwtException("Empty JWT");
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new BadJwtException("Malformed JWT");
        }
        try {
            byte[] headerJson = Base64.getUrlDecoder().decode(pad(parts[0]));
            JsonNode header = MAPPER.readTree(new String(headerJson, StandardCharsets.UTF_8));
            JsonNode algNode = header.get("alg");
            if (algNode == null || algNode.asText().isBlank()) {
                throw new BadJwtException("JWT header missing alg");
            }
            return algNode.asText();
        } catch (JwtException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadJwtException("Unable to parse JWT header", ex);
        }
    }

    private static String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }
}
