package com.pauluno.finledger.cli.ops;

import com.pauluno.finledger.cli.CliSupport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Thin wrapper: {@code GET {managementUrl}/actuator/health}.
 */
@Command(name = "health", description = "Probe management actuator health (env: FINLEDGER_MANAGEMENT_URL)")
public class HealthCommand extends AbstractOpsCommand {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        printCliBanner();
        return probeHealth(managementUrl);
    }

    private void printCliBanner() {
        try {
            System.out.println(CliSupport.globals(spec).contextBanner());
        } catch (IllegalStateException ignored) {
            // not under FinledgerCli root
        }
    }
}
