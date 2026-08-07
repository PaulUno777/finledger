package com.pauluno.finledger.infrastructure.boot.sandbox;

import java.util.Locale;

/**
 * Selectable sandbox seed packs (FL-157).
 */
public enum SandboxScenario {
    SIMPLE,
    AGGREGATOR,
    REMITTANCE;

    public static SandboxScenario fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return SIMPLE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "simple" -> SIMPLE;
            case "aggregator" -> AGGREGATOR;
            case "remittance" -> REMITTANCE;
            default -> throw new IllegalArgumentException(
                    "Unknown finledger.sandbox.scenario='" + raw
                            + "' (expected simple|aggregator|remittance)");
        };
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
