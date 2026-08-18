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
    versionProvider = Cli.Version.class,
    description = "Convert images to calculator picture and script formats.",
    subcommands = {
        ConvertCommand.class,
        FormatsCommand.class,
        TargetsCommand.class,
        InspectCommand.class,
        UpdateCommand.class,
        SetupCommand.class,
        DoctorCommand.class,
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

    /**
     * Reports the version recorded in the jar manifest at build time.
     *
     * <p>Reading it back rather than repeating it here keeps the POM the only place the version is
     * written, so a release tag cannot end up disagreeing with what {@code --version} prints.
     */
    static class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = Cli.class.getPackage().getImplementationVersion();
            return new String[]{"omniplotter " + (version == null ? "(development build)" : version)};
        }
    }

    /** Shared parser for the {@code --mode} option. */
    static Mode parseMode(String value) {
        return value == null ? null : Mode.fromString(value);
    }
}
