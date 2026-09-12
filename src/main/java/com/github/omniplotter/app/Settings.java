package com.github.omniplotter.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Everything the application remembers between launches.
 *
 * <p>A properties file rather than {@link java.util.prefs.Preferences}, because the point of these
 * values is that someone can look at them. "Delete this file and try again" has to be an
 * instruction a bug report can carry, and neither the Windows registry nor a binary plist is that.
 *
 * <p>Reads never fail. A missing file is a first launch, an unreadable one is someone who edited it
 * into nonsense, and both mean every value falls back to the default the caller passed. Losing a
 * remembered window size is not a reason to refuse to start.
 */
public final class Settings {

    private Settings() {}

    private static final Logger LOG = Logger.getLogger(Settings.class.getName());

    public static final String THEME = "theme";
    public static final String OUTPUT_DIR = "output.dir";
    public static final String LAST_MODE = "last.mode";
    public static final String LAST_TARGET = "last.target";
    public static final String LAST_FORMAT = "last.format";
    public static final String WINDOW_WIDTH = "window.width";
    public static final String WINDOW_HEIGHT = "window.height";
    public static final String UPDATE_CHECK = "updates.check";
    public static final String UPDATE_LAST_CHECK = "updates.lastCheck";
    public static final String UPDATE_SKIPPED = "updates.skippedVersion";
    public static final String UI_LANGUAGE = "ui.language";
    public static final String UI_AURORA = "ui.aurora";
    public static final String UI_QUEUE_FOLDED = "ui.queue.folded";
    public static final String UI_SETTINGS_FOLDED = "ui.settings.folded";

    private static Properties values;

    public static String get(String key, String fallback) {
        String value = values().getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Synchronized, like {@link #save()}, on the monitor {@link #values()} already uses.
     *
     * <p>The update check writes from its own thread while the window writes from the toolkit's, and
     * {@code Properties.store} iterates what a concurrent write would be modifying underneath it.
     */
    public static synchronized void set(String key, String value) {
        if (value == null) {
            values().remove(key);
        } else {
            values().setProperty(key, value);
        }
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String value = get(key, null);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public static void setBoolean(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    public static int getInt(String key, int fallback) {
        try {
            String value = get(key, null);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void setInt(String key, int value) {
        set(key, Integer.toString(value));
    }

    public static long getLong(String key, long fallback) {
        try {
            String value = get(key, null);
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void setLong(String key, long value) {
        set(key, Long.toString(value));
    }

    /** Falls back when the stored directory has been deleted or renamed since it was written. */
    public static Path getDirectory(String key, Path fallback) {
        String value = get(key, null);
        if (value == null) {
            return fallback;
        }
        Path path = Path.of(value);
        return Files.isDirectory(path) ? path : fallback;
    }

    /**
     * Writes every value, from whichever thread got here first.
     *
     * <p>Two of these at once both truncate the same file with {@code newOutputStream} and can
     * interleave into a half-written one — which is then read back as nonsense on the next launch
     * and silently discarded, taking every remembered preference with it.
     */
    public static synchronized void save() {
        try {
            Files.createDirectories(AppPaths.config());
            try (OutputStream out = Files.newOutputStream(AppPaths.settingsFile())) {
                values().store(out, "OmniPlotter settings. Delete this file to start again.");
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save settings", e);
        }
    }

    private static synchronized Properties values() {
        if (values == null) {
            values = new Properties();
            Path file = AppPaths.settingsFile();
            if (Files.isReadable(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    values.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    LOG.log(Level.WARNING, "Ignoring unreadable settings at " + file, e);
                }
            }
        }
        return values;
    }
}
