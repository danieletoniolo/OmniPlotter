package com.github.omniplotter;

import com.github.omniplotter.app.Messages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundles against each other, and against the window that reads them.
 *
 * <p>None of these can fail at runtime in a way anyone would notice: a missing key shows as
 * {@code !key!} in one label, and a stray apostrophe in a pattern silently drops the arguments out
 * of the sentence around it. Both are invisible until someone reads that exact string in that exact
 * language, which is what makes them worth a test rather than a proofread.
 */
class MessagesTest {

    private static final Path I18N = Path.of("src/main/resources/i18n");

    /** A dotted lower-case literal, which is what a key looks like in the source. */
    private static final Pattern KEY_SHAPED = Pattern.compile("\"([a-z][a-zA-Z]*(?:\\.[a-zA-Z]+)+)\"");

    /** Dotted literals in the window that are not messages: system and setting names. */
    private static final Set<String> NOT_KEYS =
        Set.of("user.home", "os.name", "omniplotter.animation");

    private static Properties load(String file) throws IOException {
        Properties values = new Properties();
        try (var in = Files.newBufferedReader(I18N.resolve(file))) {
            values.load(in);
        }
        return values;
    }

    @Test
    void bothLanguagesSayTheSameThings() throws Exception {
        Set<String> english = new TreeSet<>(load("messages.properties").stringPropertyNames());
        Set<String> italian = new TreeSet<>(load("messages_it.properties").stringPropertyNames());

        assertEquals(english, italian, "the two bundles have drifted apart");
        assertTrue(english.size() > 50, "suspiciously few messages: " + english.size());
    }

    @Test
    void anApostropheInAPatternIsWrittenTwice() throws Exception {
        for (String file : List.of("messages.properties", "messages_it.properties")) {
            Properties values = load(file);
            for (String key : values.stringPropertyNames()) {
                String value = values.getProperty(key);
                if (!value.contains("{")) {
                    // Not a pattern: it never reaches MessageFormat, so one apostrophe is right.
                    continue;
                }
                assertEquals(0, value.replace("''", "").chars().filter(c -> c == '\'').count(),
                    file + ": " + key + " takes arguments, so its apostrophe has to be doubled: "
                        + value);
            }
        }
    }

    @Test
    void everyKeyTheWindowAsksForExists() throws Exception {
        Properties english = load("messages.properties");
        Set<String> missing = new LinkedHashSet<>();

        try (Stream<Path> sources = Files.walk(Path.of("src/main/java/com/github/omniplotter/gui"))) {
            for (Path source : sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher found = KEY_SHAPED.matcher(Files.readString(source));
                while (found.find()) {
                    String key = found.group(1);
                    if (NOT_KEYS.contains(key) || english.containsKey(key)
                        // A plural is two keys, and the window asks for the stem.
                        || (english.containsKey(key + ".one") && english.containsKey(key + ".other"))) {
                        continue;
                    }
                    missing.add(source.getFileName() + ": " + key);
                }
            }
        }

        assertEquals(Set.of(), missing, "the window asks for messages that are not in the bundle");
    }

    @Test
    void aLanguageWithNoBundleFallsBackToEnglishAndNotToTheDesktop() {
        assertEquals("Convert", Messages.bundleFor(Locale.ROOT).getString("dock.convert"));
        assertEquals("Converti", Messages.bundleFor(Locale.of("it")).getString("dock.convert"));
        // Without the no-fallback control this came back Italian when run on an Italian desktop,
        // which is the whole reason that control is there.
        assertEquals("Convert", Messages.bundleFor(Locale.of("fr")).getString("dock.convert"));
    }
}
