package com.pauluno.finledger.security.policy;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Single source of truth for security-mode boot rules (server guard + CLI validate).
 */
public final class SecurityModePolicy {

    private SecurityModePolicy() {
    }

    public static boolean isProductionEnvironment(String finledgerEnv, Collection<String> activeProfiles) {
        if (finledgerEnv != null && "production".equalsIgnoreCase(finledgerEnv.trim())) {
            return true;
        }
        if (activeProfiles == null) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (profile != null && "prod".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProductionEnvironment(String finledgerEnv, String... activeProfiles) {
        return isProductionEnvironment(
                finledgerEnv,
                activeProfiles == null ? null : Arrays.asList(activeProfiles));
    }

    /**
     * @throws SecurityModeViolationException when a non-enforced mode runs in production
     */
    public static void assertBootAllowed(
            SecurityMode mode,
            String finledgerEnv,
            Collection<String> activeProfiles
    ) {
        Objects.requireNonNull(mode, "mode");
        if (mode == SecurityMode.ENFORCED) {
            return;
        }
        if (isProductionEnvironment(finledgerEnv, activeProfiles)) {
            throw new SecurityModeViolationException(
                    SecurityModeViolationException.CODE + ": finledger.security.mode="
                            + mode.configValue()
                            + " is forbidden when FINLEDGER_ENV=production or spring.profiles.active includes prod. "
                            + "Use mode=enforced (OIDC/JWT) for production.");
        }
    }

    public static void assertBootAllowed(SecurityMode mode, String finledgerEnv, String... activeProfiles) {
        assertBootAllowed(
                mode,
                finledgerEnv,
                activeProfiles == null ? null : Arrays.asList(activeProfiles));
    }

    public static boolean looksLikeProductionProfileList(String commaSeparatedProfiles) {
        if (commaSeparatedProfiles == null || commaSeparatedProfiles.isBlank()) {
            return false;
        }
        String[] parts = commaSeparatedProfiles.split(",");
        for (String part : parts) {
            if ("prod".equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeEnv(String finledgerEnv) {
        if (finledgerEnv == null || finledgerEnv.isBlank()) {
            return "local";
        }
        return finledgerEnv.trim().toLowerCase(Locale.ROOT);
    }
}
