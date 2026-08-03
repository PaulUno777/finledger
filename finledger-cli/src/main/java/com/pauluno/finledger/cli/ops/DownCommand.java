package com.pauluno.finledger.cli.ops;

import java.nio.file.Path;
import java.util.List;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "down", description = "docker compose down (no -v — data preserved)")
public class DownCommand extends AbstractOpsCommand {

    @Option(names = "--profile", description = "sandbox | with-app", defaultValue = "sandbox")
    String profile;

    @Option(names = "--volumes", description = "DANGEROUS: also remove named volumes (-v)")
    boolean volumes;

    @Override
    public Integer call() throws Exception {
        Path root = resolveProject();
        printModeBanner(root);
        if (volumes) {
            System.err.println(
                    "Refusing --volumes without explicit wipe workflow; omit flag to preserve Postgres data.");
            return 1;
        }
        DockerComposeRunner runner = new DockerComposeRunner(root);
        if (!runner.dockerAvailable()) {
            System.err.println("docker is not available on PATH");
            return 1;
        }
        return runner.run(List.of("--profile", profile, "down"));
    }
}

