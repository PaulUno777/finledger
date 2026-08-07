package com.pauluno.finledger.cli.ops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import com.pauluno.finledger.cli.CliSupport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Prefer {@code /actuator/health/readiness}; fall back to overall health == UP.
 */
@Command(
        name = "ready",
        description = "Probe readiness (…/actuator/health/readiness, else …/actuator/health UP)"
)
public class ReadyCommand extends AbstractOpsCommand {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        try {
            System.out.println(CliSupport.globals(spec).contextBanner());
        } catch (IllegalStateException ignored) {
            // not under FinledgerCli root
        }
        String base = managementUrl.endsWith("/")
                ? managementUrl.substring(0, managementUrl.length() - 1)
                : managementUrl;
        String readinessUrl = base + "/actuator/health/readiness";
        ProbeResult readiness = probe(readinessUrl);
        if (readiness.httpOk()) {
            System.out.println("ready " + readinessUrl + " → HTTP " + readiness.statusCode());
            printBodySnippet(readiness.body());
            return 0;
        }
        System.out.println("ready " + readinessUrl + " → HTTP " + readiness.statusCode()
                + " (fallback to /actuator/health)");
        String healthUrl = base + "/actuator/health";
        ProbeResult health = probe(healthUrl);
        System.out.println("health " + healthUrl + " → HTTP " + health.statusCode());
        printBodySnippet(health.body());
        if (!health.httpOk()) {
            return 1;
        }
        if (isUp(health.body())) {
            return 0;
        }
        System.err.println("Actuator responded but status is not UP");
        return 1;
    }

    static boolean isUp(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT).replace(" ", "");
        return normalized.contains("\"status\":\"up\"");
    }

    private static void printBodySnippet(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        String snippet = body.length() > 240 ? body.substring(0, 240) + "…" : body;
        System.out.println(snippet);
    }

    private static ProbeResult probe(String url) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new ProbeResult(response.statusCode(), response.body());
        } catch (Exception ex) {
            System.out.println("probe " + url + " → unreachable (" + ex.getMessage() + ")");
            return new ProbeResult(0, null);
        }
    }

    private record ProbeResult(int statusCode, String body) {
        boolean httpOk() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
