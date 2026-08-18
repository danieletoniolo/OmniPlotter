package com.github.omniplotter.app;

import java.nio.file.Path;

/**
 * Where the application keeps the things it writes.
 *
 * <p>Two directories, resolved the way each desktop expects rather than dropping a dot-folder in
 * the home directory everywhere. Nothing here creates anything: the callers that write know when
 * they are about to need a directory, and one that is never written to should never appear.
 */
public final class AppPaths {

    private AppPaths() {}

    /** The name the two graphical desktops expect; Linux uses the lower-case executable name. */
    private static final String NAME = "OmniPlotter";

    /** Settings, and anything else a user could reasonably want to read or delete by hand. */
    public static Path config() {
        if (isMac()) {
            return home().resolve("Library").resolve("Application Support").resolve(NAME);
        }
        if (isWindows()) {
            return localAppData().resolve(NAME).resolve("config");
        }
        return xdg("XDG_CONFIG_HOME", ".config").resolve("omniplotter");
    }

    /** Log files. Separate from the configuration, because on two of the three platforms it is. */
    public static Path logs() {
        if (isMac()) {
            return home().resolve("Library").resolve("Logs").resolve(NAME);
        }
        if (isWindows()) {
            return localAppData().resolve(NAME).resolve("logs");
        }
        return xdg("XDG_STATE_HOME", ".local/state").resolve("omniplotter");
    }

    public static Path settingsFile() {
        return config().resolve("settings.properties");
    }

    public static boolean isMac() {
        return osName().contains("mac");
    }

    public static boolean isWindows() {
        return osName().contains("win");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase();
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    private static Path localAppData() {
        String local = System.getenv("LOCALAPPDATA");
        return local == null || local.isBlank() ? home().resolve("AppData").resolve("Local") : Path.of(local);
    }

    /** An XDG base directory, falling back to the default the specification names for it. */
    private static Path xdg(String variable, String fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? home().resolve(fallback) : Path.of(value);
    }
}
