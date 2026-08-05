package com.pauluno.finledger.cli;

import picocli.CommandLine;

/**
 * Resolves shared {@link GlobalOptions} from the root {@link FinledgerCli} command.
 */
public final class CliSupport {

    @FunctionalInterface
    interface MintFn {
        InternalTokenMinter.MintResult mint(String baseUrl, String clientId, String clientSecret, String tenantId)
                throws Exception;
    }

    /** Overridable for unit tests (silent remint). */
    static MintFn mintFn = InternalTokenMinter::mint;

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
        GlobalOptions g = globals(spec);
        try {
            ensureFreshToken(g);
            try {
                System.out.println(call.execute(g.apiClient()));
                return 0;
            } catch (ApiException first) {
                if (first.statusCode() == 401 && g.canSilentRemint() && !g.dryRun) {
                    System.err.println("# silent remint after HTTP 401…");
                    remint(g);
                    System.out.println(call.execute(g.apiClient()));
                    return 0;
                }
                System.err.println(first.getMessage());
                if (first.statusCode() == 401) {
                    System.err.println("Hint: set FINLEDGER_TOKEN or run: finledger-cli auth token");
                }
                return 1;
            }
        } catch (ApiException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Request failed: " + e.getMessage());
            return 1;
        }
    }

    static void ensureFreshToken(GlobalOptions g) throws Exception {
        if (g.dryRun || !g.canSilentRemint()) {
            return;
        }
        if (g.tokenExpiringSoon() || !g.hasToken()) {
            System.err.println("# silent remint (token missing or near expiry)…");
            remint(g);
        }
    }

    static void remint(GlobalOptions g) throws Exception {
        InternalTokenMinter.MintResult result = mintFn.mint(
                g.baseUrl,
                g.sessionClientId,
                g.sessionClientSecret,
                g.sessionMintTenantId);
        g.acceptMint(result.accessToken(), result.expiresInSeconds());
    }

    @FunctionalInterface
    public interface MutatingCall {
        String execute(ApiClient client) throws Exception;
    }
}
