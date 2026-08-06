package com.pauluno.finledger.cli.ops;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.pauluno.finledger.cli.CliSupport;
import com.pauluno.finledger.security.policy.RuntimeSecurityPolicy;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "doctor", description = "Local diagnostics for Compose + runtime profiles")
public class DoctorCommand extends AbstractOpsCommand {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        try {
            System.out.println(CliSupport.globals(spec).contextBanner());
        } catch (IllegalStateException ignored) {
            // not under FinledgerCli root
        }
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

        int health = probeHealth(managementUrl);
        if (health != 0) {
            System.out.println("FAIL actuator health probe");
            failures++;
        } else {
            System.out.println("OK  actuator health");
        }
        return failures == 0 ? 0 : 1;
    }
}
