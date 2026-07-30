package com.pauluno.finledger.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

@Tag("unit")
class TenantCreateParseTest {

    @Test
    void should_parse_tenant_create_options() {
        FinledgerCli root = new FinledgerCli();
        CommandLine cmd = new CommandLine(root);
        CommandLine.ParseResult result = cmd.parseArgs(
                "--token", "abc",
                "tenant", "create",
                "--name", "Acme",
                "--type", "STANDALONE"
        );

        assertEquals("abc", root.globals.token);
        assertTrue(result.hasSubcommand());
        CommandLine.ParseResult create = result.subcommand().subcommand();
        assertEquals("create", create.commandSpec().name());
        Object user = create.commandSpec().userObject();
        assertTrue(user.getClass().getSimpleName().contains("TenantCreate"));
    }
}
