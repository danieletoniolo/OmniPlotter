package com.github.omniplotter.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Whether a newer release exists.
 *
 * <p>It asks by requesting {@code /releases/latest} without following the redirect and reading the
 * tag out of the {@code Location} header. That endpoint is the web one rather than the API: there
 * is no JSON to parse and so no dependency to add, and none of the sixty-requests-an-hour budget
 * the unauthenticated API would spend.
 *
 * <p>Nothing here downloads or installs anything. The builds are unsigned, a jpackage application
 * cannot reliably replace itself, and swapping a binary that nothing vouches for is not a thing to
 * do behind someone's back. It reports; the person decides.
 */
public final class UpdateCheck {

    private UpdateCheck() {}

    private static final Logger LOG = Logger.getLogger(UpdateCheck.class.getName());

    private static final String DEFAULT_BASE = "https://github.com/danieletoniolo/OmniPlotter";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long INTERVAL_MILLIS = Duration.ofHours(24).toMillis();

    /** A release newer than the running version. */
    public record Result(Version latest, String url) {}

    /**
     * The version this build reports, or {@code null} when it has none.
     *
     * <p>Only a packaged build carries {@code Implementation-Version} in its manifest, so its
     * absence is exactly the signal that this is a working copy — the same one the CLI uses to
     * print {@code (development build)}.
     */
    public static Version current() {
        return Version.parse(UpdateCheck.class.getPackage().getImplementationVersion());
    }

    /** The most recent release, whatever the running version is. Empty when there are none yet. */
    public static Optional<Result> fetchLatest() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/releases/latest"))
            .timeout(TIMEOUT)
            .header("Accept", "text/html")
            .GET()
            .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        String location = response.headers().firstValue("location").orElse("");

        // With no releases at all the redirect goes to the releases page instead, which has no
        // /tag/ in it. That is an answer, not a failure.
        int tag = location.indexOf("/tag/");
        if (tag < 0) {
            return Optional.empty();
        }
        Version version = Version.parse(location.substring(tag + "/tag/".length()));
        return version == null ? Optional.empty() : Optional.of(new Result(version, location));
    }

    /** The latest release if it is newer than what is running, and nothing otherwise. */
    public static Optional<Result> fetchIfNewer() throws IOException, InterruptedException {
        Version running = current();
        return fetchLatest().filter(result -> result.latest().isNewerThan(running));
    }

    /**
     * Runs the check off the calling thread, calling back only when there is something to say.
     *
     * <p>The callback arrives on the checking thread; a caller with a toolkit thread to respect has
     * to hop back onto it itself.
     */
    public static void inBackground(Consumer<Result> onUpdate) {
        if (!shouldCheck()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Settings.setLong(Settings.UPDATE_LAST_CHECK, System.currentTimeMillis());
                Settings.save();
                fetchIfNewer()
                    .filter(result -> !result.latest().toString()
                        .equals(Settings.get(Settings.UPDATE_SKIPPED, "")))
                    .ifPresent(onUpdate);
            } catch (IOException | InterruptedException | RuntimeException e) {
                // Being offline is the normal case here, not an error worth showing anyone.
                LOG.log(Level.FINE, "Update check did not complete", e);
            }
        }, "update-check");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Whether an automatic check should happen at all.
     *
     * <p>Deliberately unanimous: every one of these has to be true, and any of them being false is
     * a reason someone would be annoyed to be asked.
     */
    public static boolean shouldCheck() {
        if (current() == null) {
            return false;
        }
        if (!Settings.getBoolean(Settings.UPDATE_CHECK, true)) {
            return false;
        }
        if (System.getenv("OMNIPLOTTER_NO_UPDATE_CHECK") != null) {
            return false;
        }
        if (isManagedInstall()) {
            return false;
        }
        long last = Settings.getLong(Settings.UPDATE_LAST_CHECK, 0);
        return System.currentTimeMillis() - last >= INTERVAL_MILLIS;
    }

    /**
     * Whether something else is responsible for keeping this copy current.
     *
     * <p>Being told by the application to go and download a {@code .dmg} when {@code brew upgrade}
     * would have done it is the wrong answer twice. The {@code .deb} from the releases page is not
     * in this list: no repository serves it, so nothing else is going to update it.
     */
    public static boolean isManagedInstall() {
        String executable = ProcessHandle.current().info().command().orElse("");
        return executable.startsWith("/opt/homebrew/")
            || executable.startsWith("/usr/local/Cellar/")
            || executable.startsWith("/usr/lib/")
            || executable.startsWith("/usr/share/")
            || executable.startsWith("/snap/")
            || executable.startsWith("/var/lib/flatpak/");
    }

    /**
     * Overridable so tests, and anyone forking this, do not have to reach the real repository.
     *
     * <p>The system property exists because a test cannot set an environment variable; the
     * environment variable exists because someone running the installed application cannot easily
     * set a system property.
     */
    private static String base() {
        String override = System.getProperty("omniplotter.update.url");
        if (override == null || override.isBlank()) {
            override = System.getenv("OMNIPLOTTER_UPDATE_URL");
        }
        return override == null || override.isBlank() ? DEFAULT_BASE : override.replaceAll("/+$", "");
    }
}
