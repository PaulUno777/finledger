package com.pauluno.finledger.cli.ops;

import java.nio.file.Path;
import java.util.List;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "restart", description = "Restart app container (volumes keep data)")
public class RestartCommand extends AbstractOpsCommand {

    @Option(names = "--service", description = "app-sandbox | app", defaultValue = "app-sandbox")
    String service;

    @Override
    public Integer call() throws Exception {
        Path root = resolveProject();
        printModeBanner(root);
        DockerComposeRunner runner = new DockerComposeRunner(root);
        if (!runner.dockerAvailable()) {
            System.err.println("docker is not available on PATH");
            return 1;
        }
        return runner.run(List.of("restart", service));
    }
}

