package com.pauluno.finledger.cli.command;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
 * Decode a JWT without verifying the signature — operator DX for Token Profile.
 */
@Command(name = "jwt", description = "Inspect a FinLedger-shaped JWT (no signature verify)", subcommands = {
        JwtInspectCommand.class
})
public class JwtCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (inspect). Use --help for details.");
    }
}

@Command(name = "inspect", description = "Print alg, iss, TTL, scopes, tenant_id, and Token Profile verbs")
class JwtInspectCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--token", description = "JWT to inspect (env: FINLEDGER_TOKEN)")
    String token;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        GlobalOptions globals = CliSupport.globals(spec);
        String raw = token != null && !token.isBlank() ? token : globals.token;
        if (raw == null || raw.isBlank()) {
            System.err.println("Missing token. Pass --token or set FINLEDGER_TOKEN.");
            return 1;
        }
        String[] parts = raw.split("\\.");
        if (parts.length < 2) {
            System.err.println("Not a JWT (expected header.payload.signature).");
            return 1;
        }
        JsonNode header = mapper.readTree(decode(parts[0]));
        JsonNode payload = mapper.readTree(decode(parts[1]));
        String alg = header.path("alg").asText("");
        String iss = payload.path("iss").asText("");
        String scopes = payload.path("scope").asText(payload.path("scp").asText(""));
        String tenantId = payload.path("tenant_id").asText("");
        long exp = payload.path("exp").asLong(0);
        long iat = payload.path("iat").asLong(0);
        long ttl = (exp > 0 && iat > 0) ? exp - iat : -1;

        System.out.println("alg=" + alg);
        System.out.println("iss=" + iss);
        System.out.println("exp=" + (exp > 0 ? Instant.ofEpochSecond(exp) : ""));
        System.out.println("ttlSeconds=" + (ttl >= 0 ? ttl : "unknown"));
        System.out.println("scopes=" + scopes);
        System.out.println("tenant_id=" + (tenantId.isBlank() ? "(absent)" : tenantId));
        System.out.println("profile=" + profile(scopes, tenantId));
        System.out.println("canCall=" + canCall(scopes, tenantId));
        return 0;
    }

    private static String profile(String scopes, String tenantId) {
        boolean platform = scopes.contains("platform:admin");
        boolean admin = scopes.contains("ledger:admin");
        boolean write = scopes.contains("ledger:write");
        if (platform && tenantId.isBlank()) {
            return "Platform (control-plane)";
        }
        if (admin && !tenantId.isBlank()) {
            return "Parent admin / tenant admin (ADR-018 account routes if AGGREGATOR)";
        }
        if (write && !tenantId.isBlank()) {
            return "Tenant worker";
        }
        return "unknown / mixed — do not use for day-0";
    }

    private static String canCall(String scopes, String tenantId) {
        if (scopes.contains("platform:admin") && tenantId.isBlank()) {
            return "POST /tenants, POST /platform/provision";
        }
        if (scopes.contains("ledger:admin") && !tenantId.isBlank()) {
            return "accounts (+ direct child accounts if parent AGGREGATOR); money on claim tenant only";
        }
        if (scopes.contains("ledger:write") && !tenantId.isBlank()) {
            return "accounts, rails, settle, refunds, journals on claim tenant";
        }
        return "(none matched Token Profile)";
    }

    private static String decode(String part) {
        int mod = part.length() % 4;
        String padded = mod == 0 ? part : part + "====".substring(mod);
        return new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
    }
}
