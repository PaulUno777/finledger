package com.pauluno.finledger.security.policy;

import java.util.Locale;
import java.util.Objects;

/**
 * Boot-time security modes (FL-151 / ADR-014).
 */
public enum SecurityMode {

    ENFORCED,
    STATIC_TOKEN,
    DISABLED;

    public static SecurityMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ENFORCED;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "enforced", "oidc" -> ENFORCED;
            case "static-token", "statictoken", "api-key", "apikey" -> STATIC_TOKEN;
            case "disabled", "open", "sandbox" -> DISABLED;
            default -> throw new IllegalArgumentException(
                    "Unknown finledger.security.mode '" + raw
                            + "' (expected enforced|static-token|disabled)");
        };
    }

    public String configValue() {
        return switch (this) {
            case ENFORCED -> "enforced";
            case STATIC_TOKEN -> "static-token";
            case DISABLED -> "disabled";
        };
    }
}
