package com.pauluno.finledger.cli.command;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.cli.CliSupport;
import com.pauluno.finledger.cli.GlobalOptions;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Mint a short-lived JWT from FinLedger's in-box issuer (sandbox / internal — FL-155).
 * Not for normal+external IdP: there export a token from your IdP/BFF into FINLEDGER_TOKEN.
 */
@Command(name = "auth", description = "Sandbox/internal token helpers (not enterprise IdP)", subcommands = {
        AuthTokenCommand.class
})
public class AuthCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (token). Use --help for details.");
    }
}

@Command(name = "token", description = "POST /api/v1/auth/token (client_credentials)")
class AuthTokenCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--client-id", description = "OAuth client id (env: FINLEDGER_SANDBOX_CLIENT_ID)",
            defaultValue = "${env:FINLEDGER_SANDBOX_CLIENT_ID:-sandbox}")
    String clientId;

    @Option(names = "--client-secret", description = "OAuth client secret (env: FINLEDGER_SANDBOX_CLIENT_SECRET)",
            defaultValue = "${env:FINLEDGER_SANDBOX_CLIENT_SECRET:-}")
    String clientSecret;

    @Override
    public Integer call() throws Exception {
        if (clientSecret == null || clientSecret.isBlank()) {
            System.err.println("Missing --client-secret (or FINLEDGER_SANDBOX_CLIENT_SECRET). See config/sandbox-ready.txt");
            return 1;
        }
        GlobalOptions globals = CliSupport.globals(spec);
        String base = globals.baseUrl.endsWith("/")
                ? globals.baseUrl.substring(0, globals.baseUrl.length() - 1)
                : globals.baseUrl;
        String body = """
                {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s"}
                """.formatted(clientId, clientSecret).trim();
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
            System.err.println("Hint: sandbox/internal issuer only. For normal+OIDC, set FINLEDGER_TOKEN from your IdP.");
            return 1;
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(response.body());
        String token = json.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            System.err.println("Response missing access_token: " + response.body());
            return 1;
        }
        System.out.println(token);
        long expiresIn = json.path("expires_in").asLong(-1);
        if (expiresIn > 0) {
            System.err.println("# expires_in=" + expiresIn + "s — remint before expiry (sandbox/internal only)");
        }
        return 0;
    }
}
