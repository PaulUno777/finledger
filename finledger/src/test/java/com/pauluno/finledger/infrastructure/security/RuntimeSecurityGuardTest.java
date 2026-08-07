package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import com.pauluno.finledger.security.policy.RuntimeSecurityViolationException;

@Tag("unit")
class RuntimeSecurityGuardTest {

    @Test
    void should_reject_sandbox_when_production_env() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("sandbox");
        RuntimeSecurityGuard guard = new RuntimeSecurityGuard(env, "production", "internal");

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(RuntimeSecurityViolationException.class)
                .hasMessageContaining("sandbox");
    }

    @Test
    void should_allow_sandbox_locally() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("sandbox");
        RuntimeSecurityGuard guard = new RuntimeSecurityGuard(env, "local", "internal");
        guard.run(new DefaultApplicationArguments());
        assertThat(env.getActiveProfiles()).contains("sandbox");
    }

    @Test
    void should_allow_normal_external_in_production() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("normal");
        RuntimeSecurityGuard guard = new RuntimeSecurityGuard(env, "production", "external");
        guard.run(new DefaultApplicationArguments());
    }

    @Test
    void should_reject_sandbox_and_normal_together() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("sandbox", "normal");
        RuntimeSecurityGuard guard = new RuntimeSecurityGuard(env, "local", "internal");

        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
                .isInstanceOf(RuntimeSecurityViolationException.class)
                .hasMessageContaining("mutually exclusive");
    }
}
