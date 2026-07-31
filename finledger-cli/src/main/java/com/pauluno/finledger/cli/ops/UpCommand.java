package com.pauluno.finledger.cli.ops;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "up", description = "docker compose up -d (preserves volumes)")
public class UpCommand extends AbstractOpsCommand {

    @Option(names = "--profile", description = "sandbox | with-app", defaultValue = "sandbox")
    String profile;

    @Option(names = "--build", description = "Pass --build to compose")
    boolean build;

    @Override
    public Integer call() throws Exception {
        Path root = resolveProject();
        printModeBanner(root);
        String normalized = profile.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("sandbox") && !normalized.equals("with-app")) {
            System.err.println("Unsupported --profile (use sandbox|with-app)");
            return 1;
        }
        DockerComposeRunner runner = new DockerComposeRunner(root);
        if (!runner.dockerAvailable()) {
            System.err.println("docker is not available on PATH");
            return 1;
        }
        List<String> args = new ArrayList<>(List.of("--profile", normalized, "up", "-d"));
        if (build) {
            args.add("--build");
        }
        return runner.run(args);
    }
}

