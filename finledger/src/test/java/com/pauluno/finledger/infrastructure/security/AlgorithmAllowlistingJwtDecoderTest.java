package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Tag("unit")
class AlgorithmAllowlistingJwtDecoderTest {

    private final JwtDecoder acceptingDelegate = token -> Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject("x")
            .issuedAt(Instant.parse("2020-01-01T00:00:00Z"))
            .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
            .build();

    private final AlgorithmAllowlistingJwtDecoder decoder =
            new AlgorithmAllowlistingJwtDecoder(acceptingDelegate);

    @Test
    void should_reject_hs256() {
        String token = unsignedToken("HS256");
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("HS256");
    }

    @Test
    void should_reject_none() {
        String token = unsignedToken("none");
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("none");
    }

    @Test
    void should_accept_rs256_header_and_delegate() {
        String token = unsignedToken("RS256");
        Jwt jwt = decoder.decode(token);
        assert jwt.getTokenValue().equals(token);
    }

    private static String unsignedToken(String alg) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }
}
