package com.pauluno.finledger.cli.ops;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.pauluno.finledger.security.policy.RuntimeSecurityPolicy;

import picocli.CommandLine.Command;

@Command(name = "doctor", description = "Local diagnostics for Compose + runtime profiles")
public class DoctorCommand extends AbstractOpsCommand {

    @Override
    public Integer call() {
        Path root = resolveProject();
        printModeBanner(root);
        int failures = 0;

        DockerComposeRunner runner = new DockerComposeRunner(root);
        if (runner.dockerAvailable()) {
            System.out.println("OK  docker available");
        } else {
            System.out.println("FAIL docker not available on PATH");
            failures++;
        }

        System.out.println("OK  " + ComposeProjectResolver.COMPOSE_FILE + " present");

        Path example = root.resolve("finledger.env.example");
        Path env = root.resolve(".env");
        if (Files.isRegularFile(example)) {
            System.out.println("OK  finledger.env.example present");
        } else {
            System.out.println("WARN finledger.env.example missing");
        }
        if (Files.isRegularFile(env)) {
            System.out.println("OK  .env present");
        } else {
            System.out.println("WARN .env missing — cp finledger.env.example .env");
        }

        EffectiveConfig cfg = EffectiveConfig.detect(root);
        try {
            List<String> profiles = cfg.profilesHint().isBlank()
                    ? List.of()
                    : List.of(cfg.profilesHint().split(","));
            RuntimeSecurityPolicy.assertProfilesExclusive(profiles);
            RuntimeSecurityPolicy.assertSandboxProfileAllowed(cfg.env(), profiles);
            System.out.println("OK  runtime profile allowed for env");
        } catch (RuntimeException ex) {
            System.out.println("FAIL " + ex.getMessage());
            failures++;
        }

        if ("internal".equals(cfg.issuer())
                && RuntimeSecurityPolicy.isProductionEnvironment(cfg.env())) {
            System.out.println("WARN issuer=internal with production env — prefer external IdP");
        }

        probeHealth(managementUrl);
        return failures == 0 ? 0 : 1;
    }
}
