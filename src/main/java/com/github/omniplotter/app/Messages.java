package com.github.omniplotter.app;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The window's text, in the language the desktop is set to.
 *
 * <p>The window only. The command line stays English on purpose: its output is asserted verbatim by
 * {@code CliSnapshotTest}, and the format and target identifiers are the same strings img2calc uses
 * in its URLs — an interface rather than prose. The same goes for the sentences the engine returns,
 * which both faces show.
 *
 * <p>A missing key comes back as {@code !key!} rather than throwing. A window that will not open
 * because one label is missing is a worse failure than a window with one label reading oddly, and
 * the bundles are checked against each other by a test instead.
 */
public final class Messages {

    private Messages() {}

    private static final Logger LOG = Logger.getLogger(Messages.class.getName());

    public static final String BUNDLE = "i18n.messages";

    /** What {@link Settings#UI_LANGUAGE} holds when nothing has been chosen. */
    public static final String AUTOMATIC = "auto";

    private static ResourceBundle bundle;

    /** The plain string for {@code key}. Not passed through {@link MessageFormat}: see {@link
     *  #get(String, Object...)}. */
    public static String get(String key) {
        try {
            return bundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.log(Level.WARNING, "No message for " + key);
            return "!" + key + "!";
        }
    }

    /**
     * The string for {@code key} with {@code {0}}, {@code {1}} … filled in.
     *
     * <p>Only this overload formats, which is what keeps an apostrophe in a plain Italian sentence
     * from having to be written twice. In a string that does take arguments it still does — that is
     * {@link MessageFormat}'s rule — and a test enforces it.
     */
    public static String get(String key, Object... args) {
        String pattern = get(key);
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }

    /**
     * One of two forms, by count.
     *
     * <p>Both languages here have exactly two, so this is two keys and a question rather than a
     * plural-rules dependency. The count is passed as {@code {0}} to both.
     */
    public static String plural(String key, int count, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = count;
        System.arraycopy(args, 0, all, 1, args.length);
        return get(key + (count == 1 ? ".one" : ".other"), all);
    }

    /** What was chosen, or what the desktop is set to. */
    public static Locale locale() {
        String chosen = Settings.get(Settings.UI_LANGUAGE, AUTOMATIC);
        if (AUTOMATIC.equals(chosen)) {
            return Locale.getDefault();
        }
        // ROOT rather than ENGLISH, and deliberately: asked for a locale it has no bundle for,
        // getBundle falls back to the *default* locale before the base one — so on an Italian
        // desktop, choosing English would have come back Italian.
        return "en".equals(chosen) ? Locale.ROOT : Locale.of(chosen);
    }

    /**
     * The bundle for a locale, resolved the way the window resolves it.
     *
     * <p>With no {@code Control}, a request for a language that has no bundle of its own tries the
     * *desktop's* language before the base one — so on an Italian machine, asking for French came
     * back Italian. Loaded from the classpath rather than a named module, where a custom
     * {@code Control} would not be allowed; jpackage puts the jar on the class path.
     */
    public static ResourceBundle bundleFor(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE, locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    private static synchronized ResourceBundle bundle() {
        if (bundle == null) {
            bundle = bundleFor(locale());
        }
        return bundle;
    }
}
