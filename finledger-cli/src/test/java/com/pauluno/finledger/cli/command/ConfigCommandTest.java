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
            int init = cmd.execute("config", "init", "--mode", "disabled", "--out", file.toString());
            assertEquals(0, init);
            assertTrue(Files.readString(file).contains("mode: disabled"));

            int set = cmd.execute("config", "set", "--file", file.toString(), "security.mode", "static-token");
            assertEquals(0, set);
            assertTrue(Files.readString(file).contains("mode: static-token"));

            int ok = cmd.execute("config", "validate", "--file", file.toString());
            assertEquals(0, ok);

            String text = out.toString(StandardCharsets.UTF_8);
            assertTrue(text.contains("finledger-cli restart"), text);
            assertTrue(text.contains("do not use down -v"), text);

            int bad = cmd.execute(
                    "config", "validate", "--file", file.toString(), "--profiles", "prod");
            assertEquals(1, bad);
        } finally {
            System.setOut(previous);
        }
    }
}
