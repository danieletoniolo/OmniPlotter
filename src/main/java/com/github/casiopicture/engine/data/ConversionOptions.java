package com.github.casiopicture.engine.data;

/**
 * Holds the input parameters for an image conversion.
 *
 * @param width     Target width.
 * @param height    Target height.
 * @param colors    Number of colors for quantization.
 * @param keepRatio Whether to maintain the aspect ratio.
 * @param fit       Whether to fit the image within the dimensions by possibly letterboxing.
 */
public record ConversionOptions(
    int width,
    int height,
    int colors,
    boolean keepRatio,
    boolean fit
) {}
