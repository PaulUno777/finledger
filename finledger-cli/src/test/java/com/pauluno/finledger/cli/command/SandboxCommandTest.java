package com.pauluno.finledger.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import com.pauluno.finledger.cli.FinledgerCli;

@Tag("unit")
class SandboxCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void should_write_scenario_to_env_file() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "SPRING_PROFILES_ACTIVE=sandbox\n", StandardCharsets.UTF_8);

        CommandLine cmd = new CommandLine(new FinledgerCli());
        int code = cmd.execute(
                "sandbox", "init",
                "--scenario", "aggregator",
                "--env-file", env.toString());
        assertEquals(0, code);

        String text = Files.readString(env);
        assertTrue(text.contains("SPRING_PROFILES_ACTIVE=sandbox"), text);
        assertTrue(text.contains("FINLEDGER_SANDBOX_SCENARIO=aggregator"), text);
    }

    @Test
    void should_update_existing_scenario_key() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "FINLEDGER_SANDBOX_SCENARIO=simple\n", StandardCharsets.UTF_8);

        CommandLine cmd = new CommandLine(new FinledgerCli());
        int code = cmd.execute(
                "sandbox", "init",
                "--scenario", "remittance",
                "--env-file", env.toString());
        assertEquals(0, code);
        assertEquals(
                "FINLEDGER_SANDBOX_SCENARIO=remittance\n",
                Files.readString(env));
    }

    @Test
    void should_reject_unknown_scenario() {
        Path env = tempDir.resolve(".env");
        CommandLine cmd = new CommandLine(new FinledgerCli());
        int code = cmd.execute(
                "sandbox", "init",
                "--scenario", "complex",
                "--env-file", env.toString());
        assertEquals(1, code);
    }

    @Test
    void should_require_scenario_when_not_tty() {
        // System.console() is null in JUnit — missing --scenario must fail.
        Path env = tempDir.resolve(".env");
        CommandLine cmd = new CommandLine(new FinledgerCli());
        int code = cmd.execute("sandbox", "init", "--env-file", env.toString());
        assertEquals(1, code);
    }
}
