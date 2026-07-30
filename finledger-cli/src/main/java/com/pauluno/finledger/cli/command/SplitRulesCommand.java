package com.pauluno.finledger.cli.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.pauluno.finledger.cli.CliSupport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "split-rules", description = "Split rule set provisioning", subcommands = SplitRulesPutCommand.class)
public class SplitRulesCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (e.g. split-rules put). Use --help for details.");
    }
}

@Command(name = "put", description = "PUT /api/v1/tenants/{id}/split-rules/{key}")
class SplitRulesPutCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--tenant-id", required = true, description = "Tenant UUID")
    UUID tenantId;

    @Option(names = "--key", required = true, description = "Rule set key")
    String key;

    @Option(names = "--file", required = true, description = "Path to rules JSON body")
    Path file;

    @Override
    public Integer call() {
        String path = "/api/v1/tenants/" + tenantId + "/split-rules/" + key;
        return CliSupport.runMutating(spec, client -> {
            String json = Files.readString(file);
            return client.putRawJson(path, json);
        });
    }
}
