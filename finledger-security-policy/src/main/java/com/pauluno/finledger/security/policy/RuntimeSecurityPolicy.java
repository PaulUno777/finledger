package com.pauluno.finledger.security.policy;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Boot rules for ADR-016 runtime profiles ({@code sandbox} | {@code normal}) — no AuthN modes.
 */
public final class RuntimeSecurityPolicy {

    private RuntimeSecurityPolicy() {
    }

    public static boolean isProductionEnvironment(String finledgerEnv) {
        if (finledgerEnv == null || finledgerEnv.isBlank()) {
            return false;
        }
        String normalized = finledgerEnv.trim().toLowerCase(Locale.ROOT);
        return "production".equals(normalized) || "prod".equals(normalized);
    }

    /**
     * Sandbox Spring profile must never run when {@code FINLEDGER_ENV} is production.
     */
    public static void assertSandboxProfileAllowed(
            String finledgerEnv,
            Collection<String> activeProfiles
    ) {
        if (!hasSandboxProfile(activeProfiles)) {
            return;
        }
        if (isProductionEnvironment(finledgerEnv)) {
            throw new RuntimeSecurityViolationException(
                    RuntimeSecurityViolationException.CODE
                            + ": spring profile 'sandbox' is forbidden when FINLEDGER_ENV is production. "
                            + "Use profile 'normal' + issuer=external.");
        }
    }

    public static void assertSandboxProfileAllowed(String finledgerEnv, String... activeProfiles) {
        assertSandboxProfileAllowed(
                finledgerEnv,
                activeProfiles == null ? null : Arrays.asList(activeProfiles));
    }

    public static boolean hasSandboxProfile(Collection<String> activeProfiles) {
        return hasProfile(activeProfiles, "sandbox");
    }

    public static boolean hasNormalProfile(Collection<String> activeProfiles) {
        return hasProfile(activeProfiles, "normal");
    }

    /**
     * Reject simultaneous {@code sandbox} and {@code normal} (operator axis is exclusive).
     */
    public static void assertProfilesExclusive(Collection<String> activeProfiles) {
        if (hasSandboxProfile(activeProfiles) && hasNormalProfile(activeProfiles)) {
            throw new RuntimeSecurityViolationException(
                    RuntimeSecurityViolationException.CODE
                            + ": profiles 'sandbox' and 'normal' are mutually exclusive");
        }
    }

    public static String normalizeEnv(String finledgerEnv) {
        if (finledgerEnv == null || finledgerEnv.isBlank()) {
            return "local";
        }
        return finledgerEnv.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return "external";
        }
        String normalized = issuer.trim().toLowerCase(Locale.ROOT);
        if (!"external".equals(normalized) && !"internal".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown finledger.security.issuer '" + issuer + "' (expected external|internal)");
        }
        return normalized;
    }

    private static boolean hasProfile(Collection<String> activeProfiles, String name) {
        Objects.requireNonNull(name, "name");
        if (activeProfiles == null) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (profile != null && name.equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
