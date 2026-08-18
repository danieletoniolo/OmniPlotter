package com.github.omniplotter.cli;

import com.github.omniplotter.app.CommandSetup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Puts {@code omniplotter} where the shell will find it.
 *
 * <p>The one part that asks before acting is the shell startup file. Everything else happens inside
 * directories that belong to the user and can be undone with {@code --uninstall}.
 */
@Command(name = "setup", description = "Make the omniplotter command available in a terminal.")
public class SetupCommand implements Callable<Integer> {

    @Option(names = "--uninstall", description = "Remove the link instead of creating it.")
    private boolean uninstall;

    @Option(names = "--yes", description = "Answer yes to the PATH question instead of asking.")
    private boolean assumeYes;

    @Override
    public Integer call() {
        try {
            return uninstall ? remove() : install();
        } catch (IOException e) {
            System.err.println("Setup failed: " + e.getMessage());
            return 1;
        }
    }

    private Integer install() throws IOException {
        Optional<Path> launcher = CommandSetup.launcher();
        if (launcher.isEmpty()) {
            // Linking to the JDK would produce a command that ignores everything typed after it.
            System.err.println("This is running as a plain jar, which has nothing to link to.");
            System.err.println("Install the application first, then run 'omniplotter setup' from it.");
            return 1;
        }

        Path target = CommandSetup.consoleLauncher(launcher.get());
        CommandSetup.Outcome outcome = CommandSetup.install(target);
        System.out.println("Linked " + outcome.link() + " -> " + target);

        if (outcome.onPath()) {
            System.out.println("It is on your PATH: open a new terminal and run 'omniplotter --help'.");
            return 0;
        }

        System.out.println();
        System.out.println(outcome.link().getParent() + " is not on your PATH.");
        System.out.println("This line would add it, in " + outcome.shellFile() + ":");
        System.out.println();
        System.out.println("    " + outcome.shellLine());
        System.out.println();

        if (!confirm("Add it? [y/N] ")) {
            System.out.println("Left alone. Add the line yourself when you want the command.");
            return 0;
        }

        CommandSetup.addToShellFile(outcome.shellFile(), outcome.shellLine());
        System.out.println("Added. Open a new terminal and run 'omniplotter --help'.");
        return 0;
    }

    private Integer remove() throws IOException {
        if (CommandSetup.uninstall()) {
            System.out.println("Removed " + CommandSetup.linkPath() + ".");
            System.out.println("Any line added to your shell startup file is still there.");
        } else {
            System.out.println("Nothing to remove: " + CommandSetup.linkPath() + " does not exist.");
        }
        return 0;
    }

    /**
     * Asks, unless told not to.
     *
     * <p>With no console — piped, or launched from the window — there is nobody to answer, so the
     * answer is no. Editing a shell's configuration on a guess is not something to get wrong.
     */
    private boolean confirm(String prompt) {
        if (assumeYes) {
            return true;
        }
        Console console = System.console();
        if (console == null) {
            return false;
        }
        String answer = console.readLine(prompt);
        return answer != null && answer.trim().equalsIgnoreCase("y");
    }
}
