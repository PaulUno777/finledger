package com.pauluno.finledger.cli;

import com.pauluno.finledger.cli.command.AccountCommand;
import com.pauluno.finledger.cli.command.ConfigCommand;
import com.pauluno.finledger.cli.command.FxCommand;
import com.pauluno.finledger.cli.command.ShellCommand;
import com.pauluno.finledger.cli.command.SplitRulesCommand;
import com.pauluno.finledger.cli.command.TenantCommand;
import com.pauluno.finledger.cli.ops.DoctorCommand;
import com.pauluno.finledger.cli.ops.DownCommand;
import com.pauluno.finledger.cli.ops.LogsCommand;
import com.pauluno.finledger.cli.ops.RestartCommand;
import com.pauluno.finledger.cli.ops.StatusCommand;
import com.pauluno.finledger.cli.ops.UpCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
        name = "finledger-cli",
        mixinStandardHelpOptions = true,
        version = "finledger-cli 0.0.1-SNAPSHOT",
        description = "Provision FinLedger via /api/v1 and run local Compose ops",
        subcommands = {
                TenantCommand.class,
                AccountCommand.class,
                FxCommand.class,
                SplitRulesCommand.class,
                ShellCommand.class,
                ConfigCommand.class,
                StatusCommand.class,
                DoctorCommand.class,
                UpCommand.class,
                DownCommand.class,
                RestartCommand.class,
                LogsCommand.class
        }
)
public class FinledgerCli implements Runnable {

    @Mixin
    public GlobalOptions globals = new GlobalOptions();

    public static void main(String[] args) {
        int exit = new CommandLine(new FinledgerCli()).execute(args);
        System.exit(exit);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
