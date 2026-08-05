package com.pauluno.finledger.cli.command;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.cli.CliPrompts;
import com.pauluno.finledger.cli.CliSupport;
import com.pauluno.finledger.cli.GlobalOptions;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Mint a short-lived JWT from FinLedger's in-box issuer (sandbox / internal).
 * For normal+external IdP: export a token from your IdP/BFF into FINLEDGER_TOKEN.
 */
@Command(name = "auth", description = "In-box issuer token helpers (sandbox / internal)", subcommands = {
        AuthTokenCommand.class
})
public class AuthCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (token). Use --help for details.");
    }
}

@Command(name = "token", description = "POST /api/v1/auth/token (client_credentials); prompts for secret on TTY")
class AuthTokenCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--client-id", description = "OAuth client id (env: FINLEDGER_SANDBOX_CLIENT_ID)",
            defaultValue = "${env:FINLEDGER_SANDBOX_CLIENT_ID:-sandbox}")
    String clientId;

    @Option(names = "--client-secret", description = "OAuth client secret (env or interactive prompt)",
            defaultValue = "${env:FINLEDGER_SANDBOX_CLIENT_SECRET:-}")
    String clientSecret;

    @Option(names = "--tenant-id", description = "Sandbox only: mint JWT for this seeded tenant UUID")
    String tenantId;

    @Option(names = "--dump", description = "Sandbox dump file to read clientSecret from",
            defaultValue = "config/sandbox-ready.txt")
    Path dumpPath;

    @Override
    public Integer call() throws Exception {
        String secret = clientSecret;
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            secret = CliPrompts.readSandboxClientSecret(dumpPath).orElse(null);
        }
        secret = CliPrompts.requireSecret(secret, "Client secret").orElse(null);
        if (secret == null || secret.isBlank()) {
            System.err.println("Missing client secret.");
            System.err.println("Pass --client-secret, set FINLEDGER_SANDBOX_CLIENT_SECRET");
            System.err.println("(or FINLEDGER_SECURITY_INTERNAL_CLIENTS_0_CLIENT_SECRET),");
            System.err.println("or run interactively (TTY) / ensure " + dumpPath + " has clientSecret=.");
            return 1;
        }

        if (tenantId == null || tenantId.isBlank()) {
            if (CliPrompts.isInteractive()) {
                String maybe = CliPrompts.optionalLine(null, "Tenant id (optional, sandbox)", "");
                if (maybe != null && !maybe.isBlank()) {
                    tenantId = maybe;
                }
            }
        }

        GlobalOptions globals = CliSupport.globals(spec);
        String base = globals.baseUrl.endsWith("/")
                ? globals.baseUrl.substring(0, globals.baseUrl.length() - 1)
                : globals.baseUrl;
        StringBuilder bodyJson = new StringBuilder(160);
        bodyJson.append("{\"grant_type\":\"client_credentials\",\"client_id\":\"")
                .append(clientId)
                .append("\",\"client_secret\":\"")
                .append(secret)
                .append('"');
        if (tenantId != null && !tenantId.isBlank()) {
            bodyJson.append(",\"tenant_id\":\"").append(tenantId.trim()).append('"');
        }
        bodyJson.append('}');
        String body = bodyJson.toString();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            System.err.println("HTTP " + response.statusCode() + ": " + response.body());
            System.err.println("Hint: sandbox/internal issuer only. For normal+external IdP, set FINLEDGER_TOKEN from your IdP.");
            System.err.println("Hint: --tenant-id is sandbox-only; persistent internal issuer rejects body tenant_id (400).");
            return 1;
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode parsed = mapper.readTree(response.body());
        String token = parsed.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            System.err.println("Response missing access_token: " + response.body());
            return 1;
        }
        System.out.println(token);
        long expiresIn = parsed.path("expires_in").asLong(-1);
        if (expiresIn > 0) {
            System.err.println("# expires_in=" + expiresIn + "s — remint before expiry (sandbox/internal only)");
        }
        return 0;
    }
}
