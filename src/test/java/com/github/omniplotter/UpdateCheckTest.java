package com.github.omniplotter;

import com.github.omniplotter.app.UpdateCheck;
import com.github.omniplotter.app.Version;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The update check, against a local server standing in for GitHub.
 *
 * <p>What is actually being tested is that the tag is read out of a redirect rather than a body:
 * the check deliberately never parses JSON, so the {@code Location} header is the whole protocol
 * and the one thing that can quietly stop working.
 */
class UpdateCheckTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        System.clearProperty("omniplotter.update.url");
        if (server != null) {
            server.stop(0);
        }
    }

    /** Answers /releases/latest with the redirect GitHub sends, pointing at {@code location}. */
    private void serveRedirect(String location) throws IOException {
        serve(302, location);
    }

    /** Answers with a status and no Location at all, which is what a private repository does. */
    private void serveStatus(int status) throws IOException {
        serve(status, null);
    }

    private void serve(int status, String location) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (location != null) {
                exchange.getResponseHeaders().add("Location", location);
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        System.setProperty("omniplotter.update.url",
            "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void readsTheTagFromTheRedirect() throws Exception {
        serveRedirect("https://github.com/danieletoniolo/OmniPlotter/releases/tag/v1.4.0");

        Optional<UpdateCheck.Result> result = UpdateCheck.fetchLatest();

        assertTrue(result.isPresent());
        assertEquals("1.4.0", result.get().latest().toString());
        assertTrue(result.get().url().endsWith("/tag/v1.4.0"));
    }

    @Test
    void reportsNothingWhenNoReleaseHasBeenPublished() throws Exception {
        // With no releases the redirect lands on the releases page, which carries no tag.
        serveRedirect("https://github.com/danieletoniolo/OmniPlotter/releases");

        assertTrue(UpdateCheck.fetchLatest().isEmpty());
    }

    @Test
    void reportsNothingWhenTheRepositoryRefusesToSay() throws Exception {
        // A private repository answers 404 with no Location to read. That has to come back empty
        // rather than as an exception, and it is why the window stays silent before a repository
        // is made public.
        serveStatus(404);

        assertTrue(UpdateCheck.fetchLatest().isEmpty());
    }

    @Test
    void reportsNothingWhenTheTagIsNotAVersion() throws Exception {
        serveRedirect("https://github.com/danieletoniolo/OmniPlotter/releases/tag/nightly");

        assertTrue(UpdateCheck.fetchLatest().isEmpty());
    }

    @Test
    void comparesVersionsByNumber() {
        assertTrue(Version.parse("1.10.0").isNewerThan(Version.parse("1.9.9")));
        assertTrue(Version.parse("v2.0").isNewerThan(Version.parse("1.99.99")));
        assertFalse(Version.parse("1.0.0").isNewerThan(Version.parse("1.0.0")));
        assertFalse(Version.parse("1.0").isNewerThan(Version.parse("1.0.0")));
    }

    @Test
    void sortsPreReleasesBelowTheReleaseTheyLeadTo() {
        assertTrue(Version.parse("1.0.0").isNewerThan(Version.parse("1.0.0-rc1")));
        assertFalse(Version.parse("1.0.0-rc1").isNewerThan(Version.parse("1.0.0")));
        assertTrue(Version.parse("1.0.0-rc2").isNewerThan(Version.parse("1.0.0-rc1")));
    }

    @Test
    void treatsAnUnversionedBuildAsNothingToCompare() {
        // A development build has no Implementation-Version, and that is the signal used to skip
        // the check entirely rather than to report every release as an update.
        assertNull(Version.parse(null));
        assertNull(Version.parse("not-a-version"));
        assertNull(UpdateCheck.current());
        assertFalse(UpdateCheck.shouldCheck());
    }
}
