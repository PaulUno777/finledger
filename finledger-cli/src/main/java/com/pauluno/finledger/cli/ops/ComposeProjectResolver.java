package com.pauluno.finledger.cli.ops;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves a local Compose project directory (FL-152). Refuses to leave the project tree.
 */
public final class ComposeProjectResolver {

    public static final String COMPOSE_FILE = "docker-compose.yml";

    private ComposeProjectResolver() {
    }

    /**
     * @param projectDir user-supplied dir, or null/blank for CWD
     * @return absolute normalized project root containing {@code docker-compose.yml}
     */
    public static Path resolve(Path projectDir) {
        Path root = projectDir == null
                ? Path.of("").toAbsolutePath().normalize()
                : projectDir.toAbsolutePath().normalize();
        Path compose = root.resolve(COMPOSE_FILE);
        if (!Files.isRegularFile(compose)) {
            throw new IllegalArgumentException(
                    "No " + COMPOSE_FILE + " in " + root
                            + " — run from the FinLedger repo root or pass --project-dir");
        }
        return root;
    }

    public static Path requireUnderProject(Path projectRoot, Path candidate) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path abs = candidate.toAbsolutePath().normalize();
        if (!abs.startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "Path " + abs + " is outside project dir " + projectRoot);
        }
        return abs;
    }
}
