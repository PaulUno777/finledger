package com.pauluno.finledger.cli.command;

/**
 * Shared operator messaging after local config changes (FL-152).
 */
public final class ConfigRestartHints {

    public static final String RESTART_NOTE = """
            NOTE: Restart the FinLedger app to apply security/config changes.
              finledger-cli restart
              # Postgres volumes keep data — do not use down -v unless wiping.
            """;

    private ConfigRestartHints() {
    }

    public static void printToStdout() {
        System.out.print(RESTART_NOTE);
    }
}
