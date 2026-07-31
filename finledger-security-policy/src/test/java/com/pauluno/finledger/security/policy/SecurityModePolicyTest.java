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
class SecurityModePolicyTest {

    @Test
    void should_parse_modes() {
        assertEquals(SecurityMode.ENFORCED, SecurityMode.parse("enforced"));
        assertEquals(SecurityMode.STATIC_TOKEN, SecurityMode.parse("static-token"));
        assertEquals(SecurityMode.DISABLED, SecurityMode.parse("disabled"));
        assertEquals(SecurityMode.ENFORCED, SecurityMode.parse(null));
    }

    @Test
    void should_detect_production() {
        assertTrue(SecurityModePolicy.isProductionEnvironment("production", List.of("local")));
        assertTrue(SecurityModePolicy.isProductionEnvironment("local", List.of("prod")));
        assertFalse(SecurityModePolicy.isProductionEnvironment("local", List.of("sandbox")));
    }

    @Test
    void should_reject_non_enforced_in_production() {
        assertThrows(
                SecurityModeViolationException.class,
                () -> SecurityModePolicy.assertBootAllowed(
                        SecurityMode.DISABLED, "production", List.of()));
        assertThrows(
                SecurityModeViolationException.class,
                () -> SecurityModePolicy.assertBootAllowed(
                        SecurityMode.STATIC_TOKEN, "local", List.of("prod")));
    }

    @Test
    void should_allow_disabled_locally_and_enforced_in_prod() {
        assertDoesNotThrow(() -> SecurityModePolicy.assertBootAllowed(
                SecurityMode.DISABLED, "local", List.of("sandbox")));
        assertDoesNotThrow(() -> SecurityModePolicy.assertBootAllowed(
                SecurityMode.ENFORCED, "production", List.of("prod")));
    }
}
