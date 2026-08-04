package com.pauluno.finledger.security.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class RuntimeSecurityPolicyTest {

    @Test
    void should_detect_production_env() {
        assertTrue(RuntimeSecurityPolicy.isProductionEnvironment("production"));
        assertTrue(RuntimeSecurityPolicy.isProductionEnvironment("prod"));
        assertFalse(RuntimeSecurityPolicy.isProductionEnvironment("local"));
        assertFalse(RuntimeSecurityPolicy.isProductionEnvironment(null));
    }

    @Test
    void should_reject_sandbox_in_production() {
        assertThrows(
                RuntimeSecurityViolationException.class,
                () -> RuntimeSecurityPolicy.assertSandboxProfileAllowed(
                        "production", List.of("sandbox")));
        assertDoesNotThrow(() -> RuntimeSecurityPolicy.assertSandboxProfileAllowed(
                "local", List.of("sandbox")));
    }

    @Test
    void should_reject_sandbox_and_normal_together() {
        assertThrows(
                RuntimeSecurityViolationException.class,
                () -> RuntimeSecurityPolicy.assertProfilesExclusive(List.of("sandbox", "normal")));
        assertDoesNotThrow(() -> RuntimeSecurityPolicy.assertProfilesExclusive(List.of("sandbox")));
        assertDoesNotThrow(() -> RuntimeSecurityPolicy.assertProfilesExclusive(List.of("normal")));
    }

    @Test
    void should_normalize_issuer() {
        assertEquals("external", RuntimeSecurityPolicy.normalizeIssuer(null));
        assertEquals("internal", RuntimeSecurityPolicy.normalizeIssuer("internal"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeSecurityPolicy.normalizeIssuer("oidc"));
    }

    @Test
    void should_detect_profiles() {
        assertTrue(RuntimeSecurityPolicy.hasSandboxProfile(List.of("sandbox")));
        assertTrue(RuntimeSecurityPolicy.hasNormalProfile(List.of("normal")));
        assertFalse(RuntimeSecurityPolicy.hasSandboxProfile(List.of("normal")));
    }
}
