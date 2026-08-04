package com.pauluno.finledger.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import com.pauluno.finledger.cli.FinledgerCli;

@Tag("unit")
class ConfigCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void should_init_set_and_validate() throws Exception {
        Path file = tempDir.resolve("application.yml");
        CommandLine cmd = new CommandLine(new FinledgerCli());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int init = cmd.execute(
                    "config", "init", "--profile", "sandbox", "--out", file.toString());
            assertEquals(0, init);
            String initText = Files.readString(file);
            assertTrue(initText.contains("issuer: internal"), initText);
            assertTrue(initText.contains("active: sandbox"), initText);

            int set = cmd.execute(
                    "config", "set", "--file", file.toString(), "security.issuer", "external");
            assertEquals(0, set);
            assertTrue(Files.readString(file).contains("issuer: external"));

            int ok = cmd.execute("config", "validate", "--file", file.toString());
            assertEquals(0, ok);

            String text = out.toString(StandardCharsets.UTF_8);
            assertTrue(text.contains("finledger-cli restart"), text);

            Files.writeString(
                    file,
                    """
                    spring:
                      profiles:
                        active: sandbox
                    finledger:
                      env: production
                      security:
                        issuer: internal
                    """);
            int forbidden = cmd.execute("config", "validate", "--file", file.toString());
            assertEquals(1, forbidden);
        } finally {
            System.setOut(previous);
        }
    }
}
