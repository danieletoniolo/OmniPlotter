package com.github.omniplotter.engine.data;

/**
 * Tone, applied before the image reaches the format's own pipeline.
 *
 * <p>The reference tunes its pipeline for photographs. A screenshot of text reduced to sixteen
 * colours wants the opposite treatment — more contrast, no dithering, a firmer edge — and there is
 * no single setting that serves both. These are the five numbers that get from one to the other,
 * with {@link Look} holding the two combinations almost everybody actually wants.
 *
 * <p>Values are clamped rather than rejected: a slider cannot leave its range anyway, and a command
 * line asking for a contrast of 900 means "as much as there is".
 *
 * @param brightness -100 to 100, 0 leaves the image alone
 * @param contrast   -100 to 100, 0 leaves the image alone
 * @param gamma      0.1 to 5.0, 1.0 leaves the image alone. Above 1 lightens the midtones, which
 *                   is ImageMagick's convention for {@code -gamma} and so the one this follows
 * @param saturation -100 to 100, 0 leaves the image alone; -100 is grey
 * @param sharpen    0 to 5, the strength of an unsharp mask; 0 is off
 */
public record Adjustments(int brightness, int contrast, double gamma, int saturation, double sharpen) {

    /** The identity. Every default path in the application uses this and must keep using it. */
    public static final Adjustments NONE = new Adjustments(0, 0, 1.0, 0, 0.0);

    public Adjustments {
        brightness = clamp(brightness, -100, 100);
        contrast = clamp(contrast, -100, 100);
        saturation = clamp(saturation, -100, 100);
        gamma = clamp(gamma, 0.1, 5.0);
        sharpen = clamp(sharpen, 0.0, 5.0);
    }

    /**
     * Whether applying this would be a no-op.
     *
     * <p>Checked before the pixels are touched, so that the default path stays not merely
     * equivalent to the reference but literally the same code.
     */
    public boolean isNeutral() {
        return equals(NONE);
    }

    public Adjustments withBrightness(int brightness) {
        return new Adjustments(brightness, contrast, gamma, saturation, sharpen);
    }

    public Adjustments withContrast(int contrast) {
        return new Adjustments(brightness, contrast, gamma, saturation, sharpen);
    }

    public Adjustments withGamma(double gamma) {
        return new Adjustments(brightness, contrast, gamma, saturation, sharpen);
    }

    public Adjustments withSaturation(int saturation) {
        return new Adjustments(brightness, contrast, gamma, saturation, sharpen);
    }

    public Adjustments withSharpen(double sharpen) {
        return new Adjustments(brightness, contrast, gamma, saturation, sharpen);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
