package com.pauluno.finledger.cli.ops;

import java.nio.file.Path;
import java.util.List;

import picocli.CommandLine.Command;

@Command(name = "status", description = "Show Compose containers and probe actuator health")
public class StatusCommand extends AbstractOpsCommand {

    @Override
    public Integer call() throws Exception {
        Path root = resolveProject();
        printModeBanner(root);
        DockerComposeRunner runner = new DockerComposeRunner(root);
        if (!runner.dockerAvailable()) {
            System.err.println("docker is not available on PATH");
            return 1;
        }
        int ps = runner.run(List.of("ps"));
        int health = probeHealth(managementUrl);
        return ps != 0 ? ps : health;
    }
}

