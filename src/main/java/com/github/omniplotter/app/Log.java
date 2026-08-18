package com.github.omniplotter.app;

import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * File logging, for the window only.
 *
 * <p>A command line writes to the terminal its user is already looking at, and giving every
 * invocation a log file — with the lock files that come with it — would be noise for no gain. The
 * window is the one that can fail with nobody watching, and whose users cannot be asked to
 * reproduce the failure with the output visible.
 */
public final class Log {

    private Log() {}

    private static final Logger ROOT = Logger.getLogger("com.github.omniplotter");

    /** Adds the file handler and routes anything that escapes a thread into it. */
    public static void toFile() {
        try {
            Files.createDirectories(AppPaths.logs());
            FileHandler handler = new FileHandler(
                AppPaths.logs().resolve("omniplotter.log.%g").toString(), 1_000_000, 2, true);
            handler.setFormatter(new SimpleFormatter());
            ROOT.addHandler(handler);
            ROOT.setLevel(Level.INFO);
        } catch (IOException e) {
            // Another instance holds the lock, or the directory cannot be written. The console
            // handler is still attached, and a missing log file is not worth refusing to start.
            ROOT.log(Level.WARNING, "Continuing without a log file: " + e.getMessage());
        }

        Thread.setDefaultUncaughtExceptionHandler(
            (thread, error) -> ROOT.log(Level.SEVERE, "Uncaught exception in " + thread.getName(), error));
    }
}
