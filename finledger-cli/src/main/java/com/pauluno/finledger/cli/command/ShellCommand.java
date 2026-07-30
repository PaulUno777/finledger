package com.pauluno.finledger.cli.command;

import java.util.concurrent.Callable;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.pauluno.finledger.cli.CliSupport;
import com.pauluno.finledger.cli.FinledgerCli;
import com.pauluno.finledger.cli.GlobalOptions;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "shell", description = "Interactive JLine REPL reusing the same command tree")
public class ShellCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        GlobalOptions globals = CliSupport.globals(spec);
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        terminal.writer().println("FinLedger shell — type 'exit' or Ctrl-D to quit. Shared options apply from the outer invocation.");
        terminal.flush();

        while (true) {
            String line;
            try {
                line = reader.readLine("finledger> ");
            } catch (UserInterruptException e) {
                continue;
            } catch (EndOfFileException e) {
                break;
            }
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                break;
            }
            if ("help".equalsIgnoreCase(line)) {
                new CommandLine(newShellRoot(globals)).usage(terminal.writer());
                terminal.flush();
                continue;
            }

            String[] args = splitArgs(line);
            FinledgerCli root = newShellRoot(globals);
            CommandLine cmd = new CommandLine(root);
            // Prevent shell from nesting another shell REPL forever
            cmd.getSubcommands().remove("shell");
            int code = cmd.execute(args);
            if (code != 0) {
                terminal.writer().println("exit code " + code);
                terminal.flush();
            }
        }
        return 0;
    }

    private static FinledgerCli newShellRoot(GlobalOptions globals) {
        FinledgerCli root = new FinledgerCli();
        root.globals = globals;
        return root;
    }

    /** Whitespace split with simple double-quoted tokens. */
    static String[] splitArgs(String line) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens.toArray(String[]::new);
    }
}
