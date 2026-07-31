package com.pauluno.finledger.cli.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class ComposeProjectResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void should_resolve_when_compose_file_present() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), "services: {}\n");
        Path resolved = ComposeProjectResolver.resolve(tempDir);
        assertEquals(tempDir.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void should_reject_missing_compose_file() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ComposeProjectResolver.resolve(tempDir));
        assertTrue(ex.getMessage().contains("docker-compose.yml"));
    }

    @Test
    void should_reject_path_outside_project() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), "services: {}\n");
        Path outside = tempDir.getParent().resolve("outside-sibling");
        assertThrows(
                IllegalArgumentException.class,
                () -> ComposeProjectResolver.requireUnderProject(tempDir, outside));
    }
}
