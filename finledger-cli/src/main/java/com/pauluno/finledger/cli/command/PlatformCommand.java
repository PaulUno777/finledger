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
import com.pauluno.finledger.cli.CliPrompts;
import com.pauluno.finledger.cli.CliSupport;
import com.pauluno.finledger.cli.GlobalOptions;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Platform control-plane helpers (FL-158 one-shot bootstrap).
 */
@Command(name = "platform", description = "Platform control-plane (bootstrap)", subcommands = {
        PlatformBootstrapCommand.class
})
public class PlatformCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (bootstrap). Use --help for details.");
    }
}

@Command(name = "bootstrap", description = "POST /api/v1/platform/bootstrap; prompts for secret on TTY")
class PlatformBootstrapCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--secret", description = "Bootstrap secret (env or interactive prompt)",
            defaultValue = "${env:FINLEDGER_PLATFORM_BOOTSTRAP_SECRET:-}")
    String secret;

    @Override
    public Integer call() throws Exception {
        secret = CliPrompts.requireSecret(secret, "Platform bootstrap secret").orElse(null);
        if (secret == null || secret.isBlank()) {
            System.err.println("Missing bootstrap secret.");
            System.err.println("Pass --secret, set FINLEDGER_PLATFORM_BOOTSTRAP_SECRET, or run on a TTY to be prompted.");
            return 1;
        }
        GlobalOptions globals = CliSupport.globals(spec);
        String base = globals.baseUrl.endsWith("/")
                ? globals.baseUrl.substring(0, globals.baseUrl.length() - 1)
                : globals.baseUrl;
        String body = "{\"bootstrap_secret\":\"" + secret + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/platform/bootstrap"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            System.err.println("HTTP " + response.statusCode() + ": " + response.body());
            System.err.println("Hint: normal+internal only; blank secret → 404; already claimed → 410.");
            return 1;
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode parsed = mapper.readTree(response.body());
        String token = parsed.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            System.err.println("Response missing access_token: " + response.body());
            return 1;
        }
        globals.acceptMint(token, parsed.path("expires_in").asLong(0));
        // Platform secret is one-shot — no client-credentials remint session.
        System.out.println(token);
        long expiresIn = parsed.path("expires_in").asLong(-1);
        if (expiresIn > 0) {
            System.err.println("# expires_in=" + expiresIn
                    + "s — token stored in process memory; remint via auth token or export FINLEDGER_TOKEN");
        }
        return 0;
    }
}
