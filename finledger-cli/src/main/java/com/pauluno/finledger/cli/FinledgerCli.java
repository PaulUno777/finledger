package com.pauluno.finledger.cli;

import com.pauluno.finledger.cli.command.AccountCommand;
import com.pauluno.finledger.cli.command.AuthCommand;
import com.pauluno.finledger.cli.command.ConfigCommand;
import com.pauluno.finledger.cli.command.FxCommand;
import com.pauluno.finledger.cli.command.JwtCommand;
import com.pauluno.finledger.cli.command.PlatformCommand;
import com.pauluno.finledger.cli.command.SandboxCommand;
import com.pauluno.finledger.cli.command.ShellCommand;
import com.pauluno.finledger.cli.command.SplitRulesCommand;
import com.pauluno.finledger.cli.command.TenantCommand;
import com.pauluno.finledger.cli.ops.DoctorCommand;
import com.pauluno.finledger.cli.ops.DownCommand;
import com.pauluno.finledger.cli.ops.HealthCommand;
import com.pauluno.finledger.cli.ops.LogsCommand;
import com.pauluno.finledger.cli.ops.ReadyCommand;
import com.pauluno.finledger.cli.ops.RestartCommand;
import com.pauluno.finledger.cli.ops.StatusCommand;
import com.pauluno.finledger.cli.ops.UpCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
        name = "finledger-cli",
        mixinStandardHelpOptions = true,
        version = "finledger-cli 0.2.0",
        description = "Provision FinLedger via /api/v1 and run local Compose ops",
        subcommands = {
                TenantCommand.class,
                AccountCommand.class,
                FxCommand.class,
                SplitRulesCommand.class,
                ShellCommand.class,
                ConfigCommand.class,
                AuthCommand.class,
                SandboxCommand.class,
                PlatformCommand.class,
                JwtCommand.class,
                HealthCommand.class,
                ReadyCommand.class,
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
        // No args → interactive REPL (launcher + java -jar both enter CLI mode).
        String[] effective = (args == null || args.length == 0) ? new String[] {"shell"} : args;
        int exit = new CommandLine(new FinledgerCli()).execute(effective);
        System.exit(exit);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
