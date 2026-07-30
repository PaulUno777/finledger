package com.pauluno.finledger.cli.command;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.pauluno.finledger.cli.CliSupport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "fx", description = "FX configuration", subcommands = FxConfigCommand.class)
public class FxCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Specify a subcommand (e.g. fx config). Use --help for details.");
    }
}

@Command(name = "config", description = "PUT /api/v1/tenants/{id}/fx/config")
class FxConfigCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--tenant-id", required = true, description = "Tenant UUID")
    UUID tenantId;

    @Option(names = "--pivot", required = true, description = "Pivot currency code")
    String pivot;

    @Option(names = "--spread-bps", required = true, description = "Spread in basis points")
    int spreadBps;

    @Option(
            names = "--currencies",
            required = true,
            split = ",",
            description = "Comma-separated supported currency codes"
    )
    String[] currencies;

    @Override
    public Integer call() {
        List<String> codes = Arrays.stream(currencies).map(String::trim).filter(s -> !s.isEmpty()).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pivotCurrencyCode", pivot);
        body.put("spreadBps", spreadBps);
        body.put("supportedCurrencyCodes", codes);
        String path = "/api/v1/tenants/" + tenantId + "/fx/config";
        return CliSupport.runMutating(spec, client -> client.put(path, body));
    }
}
