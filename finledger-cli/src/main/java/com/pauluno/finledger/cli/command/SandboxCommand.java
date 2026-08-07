package com.pauluno.finledger.cli.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pauluno.finledger.cli.CliPrompts;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Local sandbox scenario selection (writes .env; does not start Compose).
 */
@Command(name = "sandbox", description = "Sandbox scenario helpers (local eval)", subcommands = {
        SandboxInitCommand.class
})
public class SandboxCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (init). Use --help for details.");
    }
}

@Command(name = "init", description = "Write FINLEDGER_SANDBOX_SCENARIO into .env (then up --profile sandbox)")
class SandboxInitCommand implements Callable<Integer> {

    private static final Pattern KEY_LINE =
            Pattern.compile("^(\\s*(?:export\\s+)?)([A-Za-z_][A-Za-z0-9_]*)(\\s*=)(.*)$");

    @Option(names = "--scenario", description = "simple | aggregator | remittance")
    String scenario;

    @Option(names = "--env-file", description = "Path to .env file", defaultValue = ".env")
    Path envFile;

    @Override
    public Integer call() throws IOException {
        String chosen = scenario;
        if (chosen == null || chosen.isBlank()) {
            if (!CliPrompts.isInteractive()) {
                System.err.println("Missing --scenario (required when stdin is not a TTY).");
                System.err.println("Example: finledger-cli sandbox init --scenario aggregator");
                return 1;
            }
            chosen = promptScenario();
            if (chosen == null) {
                return 1;
            }
        }
        String normalized = normalizeScenario(chosen);
        if (normalized == null) {
            System.err.println("Unknown scenario '" + chosen + "' (expected simple|aggregator|remittance)");
            return 1;
        }

        upsertEnvKey(envFile, "FINLEDGER_SANDBOX_SCENARIO", normalized);
        System.out.println("Wrote FINLEDGER_SANDBOX_SCENARIO=" + normalized + " to " + envFile.toAbsolutePath());
        System.out.println("Next: ./bin/finledger-cli up --profile sandbox --build");
        System.out.println("(Compose must load this .env; restart the sandbox app after changing scenario.)");
        return 0;
    }

    private static String promptScenario() {
        System.out.println("Select sandbox scenario:");
        System.out.println("  1) simple      — EcoPay + two USD wallets (default UUID contract)");
        System.out.println("  2) aggregator  — EcoPay Network + Send Tunnel sub-merchant");
        System.out.println("  3) remittance  — Send Tunnel Remit USD+EUR wallets");
        String line = CliPrompts.optionalLine(null, "Scenario", "simple");
        if (line == null || line.isBlank()) {
            return "simple";
        }
        String trimmed = CliPrompts.normalizeLower(line);
        return switch (trimmed) {
            case "1", "simple" -> "simple";
            case "2", "aggregator" -> "aggregator";
            case "3", "remittance" -> "remittance";
            default -> trimmed;
        };
    }

    static String normalizeScenario(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = CliPrompts.normalizeLower(raw);
        return switch (n) {
            case "simple", "aggregator", "remittance" -> n;
            default -> null;
        };
    }

    static void upsertEnvKey(Path envFile, String key, String value) throws IOException {
        List<String> lines = Files.exists(envFile)
                ? new ArrayList<>(Files.readAllLines(envFile, StandardCharsets.UTF_8))
                : new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = KEY_LINE.matcher(lines.get(i));
            if (m.matches() && key.equals(m.group(2))) {
                lines.set(i, m.group(1) + key + m.group(3) + value);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add(key + "=" + value);
        }
        Path parent = envFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(envFile, lines, StandardCharsets.UTF_8);
    }
}
