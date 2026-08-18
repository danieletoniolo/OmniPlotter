package com.github.omniplotter.app;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handing a URL or a folder to whatever the desktop opens it with.
 *
 * <p>{@link Desktop} is the portable answer and fails often enough — a Linux session without the
 * pieces AWT looks for — that the platform's own opener is kept behind it. Both run off the calling
 * thread: starting a cold browser can block for seconds, and the caller is the interface thread.
 */
public final class Desktops {

    private Desktops() {}

    private static final Logger LOG = Logger.getLogger(Desktops.class.getName());

    public static void openUrl(String url) {
        off(() -> {
            if (!desktop(Desktop.Action.BROWSE, desktop -> desktop.browse(URI.create(url)))) {
                exec(AppPaths.isMac() ? new String[]{"open", url}
                    : AppPaths.isWindows() ? new String[]{"rundll32", "url.dll,FileProtocolHandler", url}
                    : new String[]{"xdg-open", url});
            }
        });
    }

    public static void openFolder(Path folder) {
        off(() -> {
            if (!desktop(Desktop.Action.OPEN, desktop -> desktop.open(folder.toFile()))) {
                exec(AppPaths.isMac() ? new String[]{"open", folder.toString()}
                    : AppPaths.isWindows() ? new String[]{"explorer", folder.toString()}
                    : new String[]{"xdg-open", folder.toString()});
            }
        });
    }

    /** Runs {@code action} through AWT, reporting whether it got that far. */
    private static boolean desktop(Desktop.Action action, DesktopAction task) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action)) {
                task.run(Desktop.getDesktop());
                return true;
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Desktop." + action + " failed; falling back to the platform opener", e);
        }
        return false;
    }

    private static void exec(String[] command) {
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not run " + String.join(" ", command), e);
        }
    }

    private static void off(Runnable task) {
        Thread thread = new Thread(task, "desktop-open");
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    private interface DesktopAction {
        void run(Desktop desktop) throws IOException;
    }
}
