package com.github.omniplotter.engine.data;

/**
 * Represents the result of a conversion.
 *
 * @param fileBytes         The raw bytes of the generated file.
 * @param suggestedFileName A suggested file name based on the original name and target format.
 */
public record ConversionResult(
    byte[] fileBytes,
    String suggestedFileName
) {}
