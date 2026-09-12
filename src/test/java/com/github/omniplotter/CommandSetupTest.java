package com.github.omniplotter;

import com.github.omniplotter.app.CommandSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The line the setup hands over for the user to approve.
 *
 * <p>It is offered in one place and appended in another, so a syntax the shell does not understand
 * is not a message that reads oddly: it is a line written into a startup file that silently does
 * nothing, and a command that goes on not being found.
 */
class CommandSetupTest {

    private static final Path BIN = Path.of("/home/someone/.local/bin");

    /**
     * Runs a check as if the application were on {@code os}, whatever it is really running on.
     *
     * <p>Which platform it is on decides the answer to every question in here, so leaving that to
     * the machine the tests happen to run on is not a test at all: these passed on macOS and failed
     * on Windows CI, where every shell is offered the PowerShell line because there is no shell
     * startup file to put anything in.
     */
    private static void as(String os, Runnable check) {
        String was = System.getProperty("os.name");
        System.setProperty("os.name", os);
        try {
            check.run();
        } finally {
            System.setProperty("os.name", was);
        }
    }

    @Test
    void fishGetsItsOwnSyntax() {
        as("Linux", () -> {
            // fish has no `export`: the bash line in config.fish is an error at every login.
            assertEquals("fish_add_path " + BIN, CommandSetup.shellLine(BIN, "fish"));
            assertTrue(CommandSetup.shellFile("fish").orElseThrow().endsWith("config.fish"));
        });
    }

    @Test
    void everyOtherShellGetsExport() {
        as("Mac OS X", () -> {
            assertEquals("export PATH=\"" + BIN + ":$PATH\"", CommandSetup.shellLine(BIN, "zsh"));
            assertEquals("export PATH=\"" + BIN + ":$PATH\"", CommandSetup.shellLine(BIN, ""));
            assertTrue(CommandSetup.shellFile("zsh").orElseThrow().endsWith(".zshrc"));
            assertTrue(CommandSetup.shellFile("").orElseThrow().endsWith(".bashrc"));
        });
    }

    @Test
    void windowsHasNoStartupFileToAppendTo() {
        as("Windows 11", () -> {
            // Its PATH lives in the registry, so there is no file to offer to edit — which used to
            // be reported to the user as the word "null", and answering yes to it threw.
            assertTrue(CommandSetup.shellFile("bash").isEmpty());
            assertTrue(CommandSetup.shellLine(BIN, "bash").contains("SetEnvironmentVariable"),
                CommandSetup.shellLine(BIN, "bash"));
        });
    }

    @Test
    void addsTheLineOnceAndOnlyOnce(@TempDir Path home) throws Exception {
        Path rc = home.resolve(".zshrc");
        String[] held = new String[1];
        as("Mac OS X", () -> held[0] = CommandSetup.shellLine(BIN, "zsh"));
        String line = held[0];

        CommandSetup.addToShellFile(rc, line);
        CommandSetup.addToShellFile(rc, line);

        assertEquals(1, Files.readAllLines(rc).stream().filter(l -> l.trim().equals(line)).count(),
            Files.readString(rc));
        assertTrue(Files.readString(rc).contains("# Added by omniplotter setup"));
    }
}
