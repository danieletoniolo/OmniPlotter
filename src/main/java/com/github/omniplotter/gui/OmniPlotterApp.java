package com.github.omniplotter.gui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.github.omniplotter.app.Log;
import com.github.omniplotter.app.Settings;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The application window.
 *
 * <p>Uses the native window decoration rather than a custom title bar. The previous version drew
 * its own, which is the single thing that most makes a desktop app look out of place — and it
 * brought a few hundred lines of hand-rolled resize and drag handling with it.
 */
public class OmniPlotterApp extends Application {

    /** Called reflectively from {@code Main} so that class carries no JavaFX reference. */
    public static void launchApp(String[] args) {
        Application.launch(OmniPlotterApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        Log.toFile();
        Theme.apply(Theme.preference());

        ConverterView view = new ConverterView(stage);
        Scene scene = new Scene(view, 1180, 760);
        scene.getStylesheets().add(
            OmniPlotterApp.class.getResource("/omniplotter.css").toExternalForm());

        stage.setScene(scene);
        stage.setTitle("OmniPlotter");
        // jpackage sets the installed app's icon; this is for the dock and task switcher when
        // running straight from the jar.
        var icon = OmniPlotterApp.class.getResourceAsStream("/icon/icon.png");
        if (icon != null) {
            stage.getIcons().add(new javafx.scene.image.Image(icon));
        }
        stage.setMinWidth(900);
        stage.setMinHeight(620);

        // Restored onto the stage rather than the scene: the stage includes the window decoration,
        // so round-tripping through the scene would grow the window by the title bar every launch.
        int width = Settings.getInt(Settings.WINDOW_WIDTH, 0);
        int height = Settings.getInt(Settings.WINDOW_HEIGHT, 0);
        if (width >= stage.getMinWidth() && height >= stage.getMinHeight()) {
            stage.setWidth(width);
            stage.setHeight(height);
        }

        // One place where everything worth keeping is written, so the rest of the application can
        // set values without each of them deciding when to touch the disk.
        stage.setOnHiding(e -> {
            Settings.setInt(Settings.WINDOW_WIDTH, (int) stage.getWidth());
            Settings.setInt(Settings.WINDOW_HEIGHT, (int) stage.getHeight());
            view.rememberState();
            Settings.save();
        });

        stage.show();
    }

    /** Light and dark Primer themes, plus a guess at what the desktop is currently using. */
    public static final class Theme {
        private Theme() {}

        private static boolean dark;

        public static boolean isDark() {
            return dark;
        }

        public static void apply(boolean useDark) {
            dark = useDark;
            Application.setUserAgentStylesheet(
                useDark ? new PrimerDark().getUserAgentStylesheet()
                        : new PrimerLight().getUserAgentStylesheet());
        }

        public static void toggle() {
            apply(!dark);
            Settings.set(Settings.THEME, dark ? "dark" : "light");
            Settings.save();
        }

        /** What was chosen last time, or the desktop's setting on a first launch. */
        public static boolean preference() {
            String stored = Settings.get(Settings.THEME, null);
            return stored == null ? detectSystemPreference() : "dark".equalsIgnoreCase(stored);
        }

        /**
         * Best effort at the desktop's light/dark setting.
         *
         * <p>JavaFX has no portable way to ask, so this reads the one place that is cheap to check
         * on macOS and falls back to light everywhere else. The user can always toggle.
         */
        public static boolean detectSystemPreference() {
            if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
                return false;
            }
            try {
                Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor();
                return out.equalsIgnoreCase("Dark");
            } catch (Exception e) {
                return false;
            }
        }
    }
}
