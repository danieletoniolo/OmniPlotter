package com.github.omniplotter.engine.data;

/**
 * The two settings almost everybody wants, so that nobody has to find five numbers by hand.
 *
 * <p>A look is a starting point and not a mode: choosing one writes its values into the settings,
 * where they can then be changed. Nothing remembers afterwards that a look was ever chosen, which
 * is what stops {@code --look document --contrast 0} from being a riddle.
 */
public enum Look {

    /**
     * What the reference is already tuned for, nudged.
     *
     * <p>Downscaling softens, so a little sharpening puts back what the resampling took; dithering
     * is what keeps a photograph from banding on a small palette.
     */
    PHOTO("photo", new Adjustments(0, 10, 1.0, 5, 0.6), Dither.ON),

    /**
     * Text, screenshots, diagrams, sheet music.
     *
     * <p>The opposite treatment: contrast hard enough to separate ink from paper before the colour
     * budget is spent, colour pulled back because compression noise in a scan is coloured and the
     * content is not, and no dithering — on strokes it scatters into noise exactly where the eye
     * needs an edge.
     */
    DOCUMENT("document", new Adjustments(0, 45, 1.1, -25, 1.6), Dither.OFF);

    private final String id;
    private final Adjustments adjustments;
    private final Dither dither;

    Look(String id, Adjustments adjustments, Dither dither) {
        this.id = id;
        this.adjustments = adjustments;
        this.dither = dither;
    }

    public String id() {
        return id;
    }

    public Adjustments adjustments() {
        return adjustments;
    }

    public Dither dither() {
        return dither;
    }

    public static Look fromString(String value) {
        for (Look look : values()) {
            if (look.id.equalsIgnoreCase(value)) {
                return look;
            }
        }
        throw new IllegalArgumentException("Unknown look: " + value);
    }

    @Override
    public String toString() {
        return id;
    }
}
