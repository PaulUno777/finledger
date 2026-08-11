package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

@Tag("unit")
class MachineAwareMaxTokenLifetimeValidatorTest {

    @Test
    void should_reject_user_token_longer_than_15m() {
        var validator = new MachineAwareMaxTokenLifetimeValidator(
                Duration.ofMinutes(15), Duration.ofHours(1), List.of());
        Jwt jwt = token(Duration.ofMinutes(20), java.util.Map.of());
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void should_accept_machine_token_up_to_1h() {
        var validator = new MachineAwareMaxTokenLifetimeValidator(
                Duration.ofMinutes(15), Duration.ofHours(1), List.of());
        Jwt jwt = token(Duration.ofMinutes(45), java.util.Map.of("token_use", "machine"));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void should_treat_allowlisted_azp_as_machine() {
        var validator = new MachineAwareMaxTokenLifetimeValidator(
                Duration.ofMinutes(15), Duration.ofHours(1), List.of("payhub-orch"));
        Jwt jwt = token(Duration.ofMinutes(45), java.util.Map.of("azp", "payhub-orch"));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    private static Jwt token(Duration lifetime, java.util.Map<String, Object> extra) {
        Instant iat = Instant.parse("2020-01-01T00:00:00Z");
        var builder = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("u")
                .issuedAt(iat)
                .expiresAt(iat.plus(lifetime));
        extra.forEach(builder::claim);
        return builder.build();
    }
}
