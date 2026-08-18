package com.github.omniplotter.engine.data;

import java.util.List;

/**
 * The output of a conversion.
 *
 * <p>Usually one file, but not always: {@code im8c.8xv} also produces a short Python script showing
 * how to draw the image it just encoded, since the variable alone is not much use without the two
 * lines that put it on screen.
 *
 * @param fileBytes         the converted picture or script
 * @param suggestedFileName the name it should be saved under, including any extension
 * @param extras            further files the format wants alongside the main one, often empty
 */
public record ConversionResult(
    byte[] fileBytes,
    String suggestedFileName,
    List<OutputFile> extras
) {

    /** One additional file emitted alongside the main output. */
    public record OutputFile(String name, byte[] bytes) {}

    public ConversionResult {
        extras = extras == null ? List.of() : List.copyOf(extras);
    }

    /** A conversion that produced a single file, which is the common case. */
    public ConversionResult(byte[] fileBytes, String suggestedFileName) {
        this(fileBytes, suggestedFileName, List.of());
    }

    /** Every file to write, the main output first. */
    public List<OutputFile> allFiles() {
        List<OutputFile> all = new java.util.ArrayList<>();
        all.add(new OutputFile(suggestedFileName, fileBytes));
        all.addAll(extras);
        return all;
    }
}
