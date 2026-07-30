package com.pauluno.finledger.cli;

import picocli.CommandLine;

/**
 * Resolves shared {@link GlobalOptions} from the root {@link FinledgerCli} command.
 */
public final class CliSupport {

    private CliSupport() {
    }

    public static GlobalOptions globals(CommandLine.Model.CommandSpec spec) {
        Object root = spec.root().userObject();
        if (root instanceof FinledgerCli cli) {
            return cli.globals;
        }
        throw new IllegalStateException("Root command is not FinledgerCli: " + root);
    }

    public static int runMutating(CommandLine.Model.CommandSpec spec, MutatingCall call) {
        ApiClient client = globals(spec).apiClient();
        try {
            System.out.println(call.execute(client));
            return 0;
        } catch (ApiException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Request failed: " + e.getMessage());
            return 1;
        }
    }

    @FunctionalInterface
    public interface MutatingCall {
        String execute(ApiClient client) throws Exception;
    }
}
