package com.pauluno.finledger.cli.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import com.pauluno.finledger.cli.FinledgerCli;

@Tag("unit")
class OpsCommandsTest {

    @TempDir
    Path tempDir;

    @Test
    void doctor_reports_missing_env_and_passes_mode_check() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), "services: {}\n");
        Files.writeString(tempDir.resolve("finledger.env.example"), "FINLEDGER_ENV=local\n");
        Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(
                tempDir.resolve("config/application.yml"),
                """
                finledger:
                  env: local
                  security:
                    mode: disabled
                """);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new FinledgerCli()).execute(
                    "doctor",
                    "--project-dir", tempDir.toString(),
                    "--management-url", "http://127.0.0.1:1");
            String text = out.toString(StandardCharsets.UTF_8);
            assertTrue(text.contains("finledger.env.example present"), text);
            assertTrue(text.contains(".env missing"), text);
            assertTrue(text.contains("mode=disabled"), text);
            assertTrue(text.contains("OK  security mode allowed"), text);
            // Docker daemon may be unavailable in CI/sandbox; policy checks still pass.
            assertTrue(code == 0 || code == 1, "unexpected exit " + code);
        } finally {
            System.setOut(previous);
        }
    }

    @Test
    void down_refuses_volumes_flag() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), "services: {}\n");
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new FinledgerCli()).execute(
                    "down", "--volumes", "--project-dir", tempDir.toString());
            assertEquals(1, code);
            assertTrue(err.toString(StandardCharsets.UTF_8).contains("Refusing --volumes"));
        } finally {
            System.setErr(previous);
        }
    }

    @Test
    void compose_runner_builds_expected_args() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), "services: {}\n");
        List<List<String>> captured = new ArrayList<>();
        DockerComposeRunner runner = new DockerComposeRunner(
                tempDir,
                (command, workingDir) -> {
                    captured.add(List.copyOf(command));
                    return 0;
                });
        assertEquals(0, runner.run(List.of("--profile", "sandbox", "up", "-d", "--build")));
        assertEquals(
                List.of("docker", "compose", "--profile", "sandbox", "up", "-d", "--build"),
                captured.getFirst());
    }
}
