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
 */
public record ConversionOptions(
    Target target,
    int width,
    int height,
    int colors,
    boolean keepRatio,
    boolean enlargeSmaller,
    String onCalcName,
    int onCalcNumber
) {

    /** Options a format would start with in the UI: its default canvas and full colour budget. */
    public static ConversionOptions defaults(Target target, Format format) {
        FormatConfig config = FormatConfig.of(format);
        return new ConversionOptions(target,
            config.defaultWidth(), config.defaultHeight(), config.maxColors(),
            true, false, "IMAGE", 1);
    }

    /** Clamps size and colours to what the format actually accepts. */
    public ConversionOptions clampedTo(Format format) {
        FormatConfig config = FormatConfig.of(format);
        return new ConversionOptions(target,
            config.clampWidth(width), config.clampHeight(height), config.clampColors(colors),
            keepRatio, enlargeSmaller, onCalcName, onCalcNumber);
    }

    public ConversionOptions withTarget(Target target) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber);
    }

    public ConversionOptions withSize(int width, int height) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber);
    }

    public ConversionOptions withColors(int colors) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber);
    }

    public ConversionOptions withKeepRatio(boolean keepRatio) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber);
    }

    public ConversionOptions withEnlargeSmaller(boolean enlargeSmaller) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber);
    }

    public ConversionOptions withOnCalc(String onCalcName, int onCalcNumber) {
        return new ConversionOptions(target, width, height, colors, keepRatio, enlargeSmaller, onCalcName, onCalcNumber);
    }
}
