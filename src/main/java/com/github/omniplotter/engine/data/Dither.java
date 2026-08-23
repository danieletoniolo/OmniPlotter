package com.github.omniplotter.engine.data;

/**
 * Whether quantisation spreads its error across neighbouring pixels.
 *
 * <p>Three values rather than two, because the honest default is neither on nor off: the reference
 * decides per format, and reproducing that decision is the whole point of {@link #AUTO}. A plain
 * boolean would force every caller to know what the reference does for the format in hand, and get
 * it wrong.
 *
 * <p>It is the single most useful control on a sixteen-colour screen. Dithering keeps a photograph
 * readable by trading colour accuracy for apparent depth; on text it does the opposite, scattering
 * the strokes into noise.
 */
public enum Dither {

    /** What the reference does for this format. */
    AUTO,
    ON,
    OFF;

    /**
     * @param formatDefault what the reference passes for the format being converted
     * @return whether to dither
     */
    public boolean resolve(boolean formatDefault) {
        return switch (this) {
            case AUTO -> formatDefault;
            case ON -> true;
            case OFF -> false;
        };
    }

    /** Parses the command-line spelling. Throws like the other identifier lookups in this package. */
    public static Dither fromString(String value) {
        return switch (value.toLowerCase()) {
            case "auto", "default" -> AUTO;
            case "on", "true", "yes" -> ON;
            case "off", "false", "no" -> OFF;
            default -> throw new IllegalArgumentException("Unknown dither setting: " + value);
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
