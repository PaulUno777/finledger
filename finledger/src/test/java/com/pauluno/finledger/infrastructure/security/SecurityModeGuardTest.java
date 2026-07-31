package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import com.pauluno.finledger.security.policy.SecurityModeViolationException;

@Tag("unit")
class SecurityModeGuardTest {

    @Test
    void should_reject_disabled_when_prod_profile_active() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        SecurityModeGuard guard = new SecurityModeGuard(env, "disabled", "local");

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(SecurityModeViolationException.class)
                .hasMessageContaining("SECURITY_MODE_FORBIDDEN_IN_PRODUCTION");
    }

    @Test
    void should_allow_disabled_locally() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("sandbox");
        SecurityModeGuard guard = new SecurityModeGuard(env, "disabled", "local");
        guard.run(new DefaultApplicationArguments());
        assertThat(env.getActiveProfiles()).contains("sandbox");
    }

    @Test
    void should_allow_enforced_in_production() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        SecurityModeGuard guard = new SecurityModeGuard(env, "enforced", "production");
        guard.run(new DefaultApplicationArguments());
    }
}
