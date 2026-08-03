package com.pauluno.finledger.cli.ops;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin ProcessBuilder wrapper around {@code docker compose} (FL-152).
 */
public final class DockerComposeRunner {

    private final Path projectDir;
    private final ProcessExecutor executor;

    public DockerComposeRunner(Path projectDir) {
        this(projectDir, new DefaultProcessExecutor());
    }

    DockerComposeRunner(Path projectDir, ProcessExecutor executor) {
        this.projectDir = projectDir;
        this.executor = executor;
    }

    public int run(List<String> composeArgs) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        cmd.addAll(composeArgs);
        return executor.execute(cmd, projectDir);
    }

    public boolean dockerAvailable() {
        try {
            return executor.execute(List.of("docker", "version", "--format", "{{.Server.Version}}"), projectDir) == 0;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    public interface ProcessExecutor {
        int execute(List<String> command, Path workingDir) throws IOException, InterruptedException;
    }

    static final class DefaultProcessExecutor implements ProcessExecutor {
        @Override
        public int execute(List<String> command, Path workingDir) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.inheritIO();
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Command timed out: " + command);
            }
            return process.exitValue();
        }
    }
}
