package com.github.omniplotter.gui;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One queued image, along with the decoded source and its most recent preview.
 *
 * <p>The source is decoded once and kept, so re-previewing after a settings change does not re-read
 * the file.
 */
public class ConversionJob {

    private final File file;
    private BufferedImage source;
    private String error;

    public ConversionJob(File file) {
        this.file = file;
    }

    public File file() {
        return file;
    }

    public String name() {
        return file.getName();
    }

    public String error() {
        return error;
    }

    /** Decodes the image, remembering any failure so the list can show it. */
    public BufferedImage source() {
        if (source == null && error == null) {
            try {
                source = EngineApi.decode(Files.readAllBytes(file.toPath()), file.getName());
            } catch (Exception e) {
                error = e.getMessage();
            }
        }
        return source;
    }

    public BufferedImage preview(Format format, ConversionOptions options) throws Exception {
        BufferedImage src = source();
        if (src == null) {
            throw new IllegalStateException(error == null ? "could not read image" : error);
        }
        return EngineApi.preview(src, format, options);
    }

    /** Converts and writes the result into {@code outputDir}, returning the file written. */
    public Path convert(Format format, ConversionOptions options, Path outputDir) throws Exception {
        BufferedImage src = source();
        if (src == null) {
            throw new IllegalStateException(error == null ? "could not read image" : error);
        }
        ConversionResult result = EngineApi.convert(src, file.getName(), format, options);
        Files.createDirectories(outputDir);
        Path destination = outputDir.resolve(result.suggestedFileName());
        Files.write(destination, result.fileBytes());
        return destination;
    }

    /** Base name of the input, used as the default on-calculator variable name. */
    public String baseName() {
        String name = file.getName();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public String toString() {
        return name();
    }
}
