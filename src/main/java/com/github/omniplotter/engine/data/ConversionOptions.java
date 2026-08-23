package com.github.omniplotter.engine.data;

/**
 * Everything about a conversion request other than the image itself.
 *
 * @param target         the calculator model. It is not cosmetic: the script generators emit
 *                       different import lines and drawing calls per target, and Nspire targets
 *                       wrap the result in a {@code .tns} document.
 * @param width          canvas width
 * @param height         canvas height
 * @param colors         colour budget passed to quantisation
 * @param keepRatio      preserve the source aspect ratio instead of stretching to the canvas
 * @param enlargeSmaller scale a source smaller than the canvas up to fill it; when false the image
 *                       is only ever shrunk. This is the reference's "enlarge smaller" checkbox.
 * @param onCalcName     name the variable takes on the calculator, for formats that carry one
 * @param onCalcNumber   slot number for formats addressed as Pic1..Pic9 / Image1..Image9
 * @param adjustments    tone applied before the format's own pipeline; {@link Adjustments#NONE}
 *                       is the reference's behaviour
 * @param dither         whether quantisation dithers; {@link Dither#AUTO} is the reference's
 *                       per-format choice
 * @param framing        what part of the source to convert; {@link Framing#DEFAULT} is all of it
 */
public record ConversionOptions(
    Target target,
    int width,
    int height,
    int colors,
    boolean keepRatio,
    boolean enlargeSmaller,
    String onCalcName,
    int onCalcNumber,
    Adjustments adjustments,
    Dither dither,
    Framing framing
) {

    /**
     * The three latest components are grouped rather than spread flat on purpose.
     *
     * <p>Every wither below has to name every component, so each new knob added flat costs a line
     * in eight methods and turns their call sites into long runs of positional arguments where two
     * transposed ints compile perfectly. Grouped, a new adjustment touches {@link Adjustments} and
     * nothing here.
     */

    /** Options a format would start with in the UI: its default canvas and full colour budget. */
    public static ConversionOptions defaults(Target target, Format format) {
        FormatConfig config = FormatConfig.of(format);
        return new ConversionOptions(target,
            config.defaultWidth(), config.defaultHeight(), config.maxColors(),
            true, false, "IMAGE", 1,
            Adjustments.NONE, Dither.AUTO, Framing.DEFAULT);
    }

    /** Clamps size and colours to what the format actually accepts. */
    public ConversionOptions clampedTo(Format format) {
        FormatConfig config = FormatConfig.of(format);
        return new ConversionOptions(target,
            config.clampWidth(width), config.clampHeight(height), config.clampColors(colors),
            keepRatio, enlargeSmaller, onCalcName, onCalcNumber,
            adjustments, dither, framing);
    }

    public ConversionOptions withTarget(Target target) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withSize(int width, int height) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withColors(int colors) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withKeepRatio(boolean keepRatio) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withEnlargeSmaller(boolean enlargeSmaller) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withOnCalc(String onCalcName, int onCalcNumber) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withAdjustments(Adjustments adjustments) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withDither(Dither dither) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    public ConversionOptions withFraming(Framing framing) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber, adjustments, dither, framing);
    }

    /** The crop is the one setting the window keeps per image rather than for the whole queue. */
    public ConversionOptions withCrop(Crop crop) {
        return withFraming(framing.withCrop(crop));
    }

    /** Applies a look: its tone and its dithering together, since half of one is not the look. */
    public ConversionOptions withLook(Look look) {
        return withAdjustments(look.adjustments()).withDither(look.dither());
    }
}
