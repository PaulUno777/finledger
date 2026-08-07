package com.pauluno.finledger.cli.ops;

import java.nio.file.Path;
import java.util.List;

import com.pauluno.finledger.cli.CliSupport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "status", description = "Show Compose containers and probe actuator health")
public class StatusCommand extends AbstractOpsCommand {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        try {
            System.out.println(CliSupport.globals(spec).contextBanner());
        } catch (IllegalStateException ignored) {
            // not under FinledgerCli root
        }
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

