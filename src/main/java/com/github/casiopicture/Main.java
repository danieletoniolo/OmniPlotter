package com.github.casiopicture;

import com.github.casiopicture.cli.Cli;

/**
 * Single entry point for both faces of the application.
 *
 * <p>Arguments mean the command line; none means open the window. One main class keeps the packaged
 * app to a single artifact, and lets the installed binary serve as the CLI too.
 *
 * <p>The UI is loaded reflectively so that this class carries no compile-time reference to JavaFX.
 * A JavaFX {@code Application} subclass cannot be a fat jar's main class without the modules on the
 * module path, and touching one from {@code main} is enough to trip that.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length > 0) {
            Cli.main(args);
            return;
        }
        try {
            Class.forName("com.github.casiopicture.gui.CasioPictureApp")
                .getMethod("launchApp", String[].class)
                .invoke(null, (Object) args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println("Could not start the user interface: " + cause);
            System.err.println("Run with arguments to use the command line, or --help for usage.");
            System.exit(1);
        }
    }
}
