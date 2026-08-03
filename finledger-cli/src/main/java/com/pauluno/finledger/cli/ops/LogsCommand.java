package com.pauluno.finledger.cli.ops;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "logs", description = "docker compose logs")
public class LogsCommand extends AbstractOpsCommand {

    @Option(names = {"-f", "--follow"}, description = "Follow log output")
    boolean follow;

    @Option(names = "--service", description = "Optional service name")
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
        List<String> args = new ArrayList<>(List.of("logs"));
        if (follow) {
            args.add("-f");
        }
        if (service != null && !service.isBlank()) {
            args.add(service);
        }
        return runner.run(args);
    }
}

