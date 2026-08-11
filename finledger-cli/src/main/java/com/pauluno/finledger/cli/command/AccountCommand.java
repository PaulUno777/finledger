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

@Command(name = "account", description = "Ledger account provisioning", subcommands = {
        AccountCreateCommand.class,
        AccountListCommand.class
})
public class AccountCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (e.g. account create). Use --help for details.");
    }
}

@Command(name = "create", description = "POST /api/v1/tenants/{id}/accounts")
class AccountCreateCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--tenant-id", required = true, description = "Tenant UUID")
    UUID tenantId;

    @Option(names = "--owner-ref", required = true, description = "Owner reference")
    String ownerRef;

    @Option(names = "--currency", required = true, description = "ISO currency code")
    String currency;

    @Option(names = "--type", required = true, description = "Account type (e.g. MERCHANT_WALLET)")
    String type;

    @Option(names = "--overdraft", description = "Allow overdraft (default: false)", defaultValue = "false")
    boolean overdraft;

    @Override
    public Integer call() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ownerRef", ownerRef);
        body.put("currencyCode", currency);
        body.put("type", type);
        body.put("allowsOverdraft", overdraft);
        String path = "/api/v1/tenants/" + tenantId + "/accounts";
        return CliSupport.runMutating(spec, client -> client.post(path, body));
    }
}

@Command(name = "list", description = "GET /api/v1/tenants/{id}/accounts")
class AccountListCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--tenant-id", required = true, description = "Tenant UUID")
    UUID tenantId;

    @Override
    public Integer call() {
        String path = "/api/v1/tenants/" + tenantId + "/accounts";
        return CliSupport.runMutating(spec, client -> client.get(path));
    }
}
