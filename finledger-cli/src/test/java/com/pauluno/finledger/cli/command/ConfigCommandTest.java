package com.pauluno.finledger.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        int init = cmd.execute("config", "init", "--mode", "disabled", "--out", file.toString());
        assertEquals(0, init);
        assertTrue(Files.readString(file).contains("mode: disabled"));

        int set = cmd.execute("config", "set", "--file", file.toString(), "security.mode", "static-token");
        assertEquals(0, set);
        assertTrue(Files.readString(file).contains("mode: static-token"));

        int ok = cmd.execute("config", "validate", "--file", file.toString());
        assertEquals(0, ok);

        int bad = cmd.execute(
                "config", "validate", "--file", file.toString(), "--profiles", "prod");
        assertEquals(1, bad);
    }
}
