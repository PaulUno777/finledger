package com.pauluno.finledger.cli.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.pauluno.finledger.cli.CliPrompts;
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

@Command(name = "create", description = "POST /api/v1/tenants; prompts for missing fields on TTY")
class TenantCreateCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--name", description = "Tenant display name (prompted if omitted on TTY)")
    String name;

    @Option(names = "--type", description = "STANDALONE | AGGREGATOR | SUB_MERCHANT (prompted if omitted)")
    String type;

    @Option(names = "--parent-id", description = "Parent tenant UUID (required for SUB_MERCHANT)")
    UUID parentId;

    @Option(names = "--id", description = "Client-supplied UUID (platform:admin only)")
    UUID id;

    @Override
    public Integer call() {
        name = CliPrompts.requireLine(name, "Tenant name").orElse(null);
        if (name == null) {
            System.err.println("Missing --name (required when stdin is not a TTY).");
            return 1;
        }
        type = CliPrompts.requireLine(type, "Tenant type (STANDALONE|AGGREGATOR|SUB_MERCHANT)").orElse(null);
        if (type == null) {
            System.err.println("Missing --type (required when stdin is not a TTY).");
            return 1;
        }
        type = type.trim().toUpperCase();

        if (parentId == null && "SUB_MERCHANT".equals(type) && CliPrompts.isInteractive()) {
            String raw = CliPrompts.requireLine(null, "Parent tenant UUID").orElse(null);
            if (raw == null) {
                System.err.println("SUB_MERCHANT requires --parent-id.");
                return 1;
            }
            try {
                parentId = UUID.fromString(raw);
            } catch (IllegalArgumentException ex) {
                System.err.println("Invalid parent UUID: " + raw);
                return 1;
            }
        }

        if (id == null && CliPrompts.isInteractive()) {
            String raw = CliPrompts.optionalLine(null, "Tenant id (optional, platform:admin cold-start)", "");
            if (raw != null && !raw.isBlank()) {
                try {
                    id = UUID.fromString(raw);
                } catch (IllegalArgumentException ex) {
                    System.err.println("Invalid tenant id UUID: " + raw);
                    return 1;
                }
            }
        }

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
