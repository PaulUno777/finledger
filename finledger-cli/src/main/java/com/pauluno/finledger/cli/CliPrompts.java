package com.pauluno.finledger.cli;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Interactive console prompts for TTY use. Non-TTY (CI/pipes) must pass flags/env.
 */
public final class CliPrompts {

    private CliPrompts() {
    }

    public static boolean isInteractive() {
        return System.console() != null;
    }

    /**
     * Returns {@code current} if non-blank; otherwise prompts (hidden) when interactive.
     *
     * @return empty if still missing (caller should fail with a clear message)
     */
    public static Optional<String> requireSecret(String current, String promptLabel) {
        if (current != null && !current.isBlank()) {
            return Optional.of(current);
        }
        Console console = System.console();
        if (console == null) {
            return Optional.empty();
        }
        char[] chars = console.readPassword("%s: ", promptLabel);
        if (chars == null || chars.length == 0) {
            return Optional.empty();
        }
        return Optional.of(new String(chars));
    }

    /**
     * Returns {@code current} if non-blank; otherwise prompts when interactive.
     */
    public static Optional<String> requireLine(String current, String promptLabel) {
        if (current != null && !current.isBlank()) {
            return Optional.of(current.trim());
        }
        Console console = System.console();
        if (console == null) {
            return Optional.empty();
        }
        String line = console.readLine("%s: ", promptLabel);
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(line.trim());
    }

    /**
     * Optional line: blank answer keeps {@code current} (may be null).
     */
    public static String optionalLine(String current, String promptLabel, String defaultHint) {
        if (current != null && !current.isBlank()) {
            return current.trim();
        }
        Console console = System.console();
        if (console == null) {
            return current;
        }
        String hint = defaultHint == null || defaultHint.isBlank() ? "" : " [" + defaultHint + "]";
        String line = console.readLine("%s%s: ", promptLabel, hint);
        if (line == null || line.isBlank()) {
            return defaultHint != null && !defaultHint.isBlank() ? defaultHint : current;
        }
        return line.trim();
    }

    /**
     * Reads {@code clientSecret=} from a sandbox dump file if present.
     */
    public static Optional<String> readSandboxClientSecret(Path dumpPath) {
        if (dumpPath == null || !Files.isRegularFile(dumpPath)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(dumpPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("clientSecret=")) {
                    String value = line.substring("clientSecret=".length()).trim();
                    if (!value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static String normalizeLower(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
