package com.pauluno.finledger.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

@Tag("unit")
class GlobalOptionsAndCliSupportTest {

    @AfterEach
    void resetMintFn() {
        CliSupport.mintFn = InternalTokenMinter::mint;
    }

    @Test
    void should_track_mint_expiry_and_expiring_soon() {
        GlobalOptions g = new GlobalOptions();
        g.acceptMint("tok", 3600);
        assertTrue(g.hasToken());
        assertFalse(g.tokenExpiringSoon());
        assertEquals("tok", g.token);

        g.tokenExpiresAt = Instant.now().plusSeconds(30);
        assertTrue(g.tokenExpiringSoon());

        g.rememberClientCredentials("sandbox", "secret", "tenant-1");
        assertTrue(g.canSilentRemint());
        assertTrue(g.contextBanner().contains("remint=ready"));
        assertTrue(g.contextBanner().contains("token=set"));
    }

    @Test
    void should_not_send_http_on_dry_run() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        ApiClient.HttpExecutor fake = request -> {
            sends.incrementAndGet();
            throw new AssertionError("HTTP must not be called in dry-run");
        };
        ApiClient client = new ApiClient("http://localhost:8080", "jwt", "key-1", true, fake);
        String out = client.post("/api/v1/tenants", java.util.Map.of("name", "Acme", "type", "STANDALONE"));
        assertEquals("(dry-run — not sent)", out);
        assertEquals(0, sends.get());
        assertTrue(client.dryRun());
    }

    @Test
    void should_silent_remint_on_401_then_retry() {
        FinledgerCli root = new FinledgerCli();
        root.globals.baseUrl = "http://localhost:8080";
        root.globals.token = "expired";
        root.globals.rememberClientCredentials("sandbox", "sec", null);
        root.globals.tokenExpiresAt = Instant.now().plusSeconds(3600);

        AtomicInteger mintCalls = new AtomicInteger();
        CliSupport.mintFn = (base, id, secret, tenant) -> {
            mintCalls.incrementAndGet();
            return new InternalTokenMinter.MintResult("fresh-token", 3600, "ledger:admin");
        };

        AtomicInteger attempts = new AtomicInteger();
        CommandLine cmd = new CommandLine(root);
        int code = CliSupport.runMutating(cmd.getCommandSpec(), client -> {
            int n = attempts.incrementAndGet();
            if (n == 1) {
                throw new ApiException(401, "unauthorized");
            }
            assertEquals("fresh-token", root.globals.token);
            return "{\"ok\":true}";
        });

        assertEquals(0, code);
        assertEquals(1, mintCalls.get());
        assertEquals(2, attempts.get());
        assertEquals("fresh-token", root.globals.token);
    }

    @Test
    void should_fail_401_without_remint_credentials() {
        FinledgerCli root = new FinledgerCli();
        root.globals.baseUrl = "http://localhost:8080";
        root.globals.token = "bad";
        CommandLine cmd = new CommandLine(root);
        int code = CliSupport.runMutating(cmd.getCommandSpec(), client -> {
            throw new ApiException(401, "unauthorized");
        });
        assertEquals(1, code);
    }

    @Test
    void should_remint_when_token_expiring_soon_before_call() {
        FinledgerCli root = new FinledgerCli();
        root.globals.baseUrl = "http://localhost:8080";
        root.globals.token = "old";
        root.globals.tokenExpiresAt = Instant.now().plusSeconds(10);
        root.globals.rememberClientCredentials("sandbox", "sec", "t1");

        AtomicInteger mintCalls = new AtomicInteger();
        CliSupport.mintFn = (base, id, secret, tenant) -> {
            mintCalls.incrementAndGet();
            assertEquals("t1", tenant);
            return new InternalTokenMinter.MintResult("renewed", 3600, "x");
        };

        CommandLine cmd = new CommandLine(root);
        int code = CliSupport.runMutating(cmd.getCommandSpec(), client -> "{\"ok\":true}");
        assertEquals(0, code);
        assertEquals(1, mintCalls.get());
        assertEquals("renewed", root.globals.token);
    }
}
