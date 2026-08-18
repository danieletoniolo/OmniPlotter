package com.github.omniplotter;

import com.github.omniplotter.cli.Cli;

/**
 * Single entry point for both faces of the application.
 *
 * <p>Arguments mean the command line; none means open the window. One main class keeps the packaged
 * app to a single artifact, and lets the installed binary serve as the CLI too.
 *
 * <p>The UI is loaded reflectively so that this class carries no compile-time reference to JavaFX.
 * A JavaFX {@code Application} subclass cannot be a fat jar's main class without the modules on the
 * module path, and touching one from {@code main} is enough to trip that.
 *
 * <p>The same indirection is what lets the command-line jar exist: with the window's classes and
 * JavaFX both left out, everything here still resolves.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length > 0) {
            Cli.main(args);
            return;
        }
        try {
            Class.forName("com.github.omniplotter.gui.OmniPlotterApp")
                .getMethod("launchApp", String[].class)
                .invoke(null, (Object) args);
        } catch (ClassNotFoundException e) {
            // The command-line jar, started with nothing to do. Not a failure — it is what that
            // build is, and a stack trace would suggest otherwise.
            System.err.println("This build has no window: it is the command-line jar.");
            System.err.println("Run it with arguments, or --help for usage. The installers on the");
            System.err.println("releases page carry the full application.");
            System.exit(1);
        } catch (ReflectiveOperationException | LinkageError e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println("Could not start the user interface: " + cause);
            System.err.println("Run with arguments to use the command line, or --help for usage.");
            System.exit(1);
        }
    }
}
