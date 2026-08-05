package com.pauluno.finledger.cli.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.pauluno.finledger.cli.CliSupport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "tenant", description = "Tenant provisioning", subcommands = TenantCreateCommand.class)
public class TenantCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (e.g. tenant create). Use --help for details.");
    }
}

@Command(name = "create", description = "POST /api/v1/tenants (ledger:admin or platform:admin)")
class TenantCreateCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--name", required = true, description = "Tenant display name")
    String name;

    @Option(names = "--type", required = true, description = "STANDALONE | AGGREGATOR | SUB_MERCHANT")
    String type;

    @Option(names = "--parent-id", description = "Parent tenant UUID (required for SUB_MERCHANT)")
    UUID parentId;

    @Option(names = "--id", description = "Client-supplied UUID (platform:admin only; align with internal client tenant_id)")
    UUID id;

    @Override
    public Integer call() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        body.put("parentTenantId", parentId);
        if (id != null) {
            body.put("id", id);
        }
        return CliSupport.runMutating(spec, client -> client.post("/api/v1/tenants", body));
    }
}
