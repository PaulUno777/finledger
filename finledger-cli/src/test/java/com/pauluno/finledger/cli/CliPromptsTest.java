package com.pauluno.finledger.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class CliPromptsTest {

    @TempDir
    Path tempDir;

    @Test
    void should_read_client_secret_from_dump() throws Exception {
        Path dump = tempDir.resolve("sandbox-ready.txt");
        Files.writeString(dump, """
                scenario=simple
                clientId=sandbox
                clientSecret=from-dump-secret
                === end ===
                """, StandardCharsets.UTF_8);
        Optional<String> secret = CliPrompts.readSandboxClientSecret(dump);
        assertTrue(secret.isPresent());
        assertEquals("from-dump-secret", secret.get());
    }

    @Test
    void requireSecret_keeps_provided_value() {
        assertEquals("abc", CliPrompts.requireSecret("abc", "Secret").orElseThrow());
    }

    @Test
    void requireSecret_empty_without_tty() {
        // JUnit has no System.console()
        assertTrue(CliPrompts.requireSecret(null, "Secret").isEmpty());
        assertTrue(CliPrompts.requireSecret("  ", "Secret").isEmpty());
    }
}
