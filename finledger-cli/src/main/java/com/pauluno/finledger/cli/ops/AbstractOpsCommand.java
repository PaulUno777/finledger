package com.pauluno.finledger.cli.ops;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine.Option;

/**
 * Shared options and helpers for local Compose ops (ADR-015 / ADR-016).
 */
abstract class AbstractOpsCommand implements Callable<Integer> {

    @Option(names = "--project-dir", description = "FinLedger repo root (default: CWD)")
    Path projectDir;

    @Option(
            names = "--management-url",
            description = "Actuator base URL (env: FINLEDGER_MANAGEMENT_URL)",
            defaultValue = "${env:FINLEDGER_MANAGEMENT_URL:-http://localhost:8081}"
    )
    String managementUrl;

    protected Path resolveProject() {
        return ComposeProjectResolver.resolve(projectDir);
    }

    protected void printModeBanner(Path root) {
        EffectiveConfig cfg = EffectiveConfig.detect(root);
        System.out.println(
                "[ops] project=" + root
                        + " issuer=" + cfg.issuer()
                        + " env=" + cfg.env()
                        + (cfg.profilesHint().isBlank() ? "" : " profiles~=" + cfg.profilesHint()));
    }

    protected int probeHealth(String url) {
        String healthUrl = url.endsWith("/") ? url + "actuator/health" : url + "/actuator/health";
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("health " + healthUrl + " → HTTP " + response.statusCode());
            if (response.body() != null && !response.body().isBlank()) {
                String body = response.body();
                if (body.length() > 240) {
                    body = body.substring(0, 240) + "…";
                }
                System.out.println(body);
            }
            return response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : 1;
        } catch (Exception ex) {
            System.out.println("health " + healthUrl + " → unreachable (" + ex.getMessage() + ")");
            return 1;
        }
    }
}

record EffectiveConfig(String issuer, String env, String profilesHint) {

    static EffectiveConfig detect(Path projectRoot) {
        String issuerRaw = firstNonBlank(
                readDotEnv(projectRoot, "FINLEDGER_SECURITY_ISSUER"),
                readYamlScalar(projectRoot.resolve("config/application.yml"), "issuer"),
                System.getenv("FINLEDGER_SECURITY_ISSUER")
        ).orElse("external");
        String env = firstNonBlank(
                readDotEnv(projectRoot, "FINLEDGER_ENV"),
                readYamlScalar(projectRoot.resolve("config/application.yml"), "env"),
                System.getenv("FINLEDGER_ENV")
        ).orElse("local");
        String profiles = firstNonBlank(
                readDotEnv(projectRoot, "SPRING_PROFILES_ACTIVE"),
                readYamlScalar(projectRoot.resolve("config/application.yml"), "active"),
                System.getenv("SPRING_PROFILES_ACTIVE")
        ).orElse("");
        String issuer;
        try {
            issuer = com.pauluno.finledger.security.policy.RuntimeSecurityPolicy.normalizeIssuer(issuerRaw);
        } catch (IllegalArgumentException ex) {
            issuer = issuerRaw;
        }
        return new EffectiveConfig(issuer, env, profiles);
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return Optional.of(v.trim());
            }
        }
        return Optional.empty();
    }

    private static String readDotEnv(Path root, String key) {
        Path envFile = root.resolve(".env");
        if (!Files.isRegularFile(envFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                if (trimmed.substring(0, eq).trim().equals(key)) {
                    return unquote(trimmed.substring(eq + 1).trim());
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static String readYamlScalar(Path file, String key) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:\\s*(.+?)\\s*$");
            Matcher matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
            if (matcher.find()) {
                return unquote(matcher.group(1).trim());
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static String unquote(String raw) {
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }
}
