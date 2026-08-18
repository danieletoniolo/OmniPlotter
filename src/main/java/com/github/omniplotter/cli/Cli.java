package com.github.omniplotter.cli;

import com.github.omniplotter.engine.data.Mode;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Command line entry point.
 *
 * <p>Format and target identifiers are the same strings img2calc uses in its URLs, so a link from
 * the web tool translates directly into a command here.
 */
@Command(
    name = "omniplotter",
    mixinStandardHelpOptions = true,
    version = "omniplotter 1.0.0",
    description = "Convert images to calculator picture and script formats.",
    subcommands = {
        ConvertCommand.class,
        FormatsCommand.class,
        TargetsCommand.class,
        InspectCommand.class,
    },
    synopsisSubcommandLabel = "COMMAND")
public class Cli implements Runnable {

    @Override
    public void run() {
        // No subcommand: show usage rather than doing something surprising.
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Cli())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args));
    }

    /** Shared parser for the {@code --mode} option. */
    static Mode parseMode(String value) {
        return value == null ? null : Mode.fromString(value);
    }
}
