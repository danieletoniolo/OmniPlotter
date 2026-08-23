package com.github.omniplotter.gui;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Crop;
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

    /**
     * The part of this image to convert, or null for all of it.
     *
     * <p>Kept here rather than with the rest of the settings because it is the one choice that is
     * about the picture and not about the file being produced: a queue of ten photographs wants ten
     * rectangles, while they all want the same format.
     */
    private Crop crop;

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

    public Crop crop() {
        return crop;
    }

    public void setCrop(Crop crop) {
        this.crop = crop;
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

    /**
     * What the encoder will actually see, plus what it will actually produce.
     *
     * <p>Encoding as well as preprocessing lets the window show the output size and any capacity
     * warning <em>before</em> converting, which the web tool can only report afterwards. Encoding
     * is cheap next to the preprocessing that precedes it.
     */
    public record Preview(BufferedImage image, int encodedSize) {}

    public Preview preview(Format format, ConversionOptions options) throws Exception {
        BufferedImage src = source();
        if (src == null) {
            throw new IllegalStateException(error == null ? "could not read image" : error);
        }
        BufferedImage processed = EngineApi.preview(src, format, options);
        int size = EngineApi.encode(processed, file.getName(), format, options).fileBytes().length;
        return new Preview(processed, size);
    }

    /** Converts and writes the result into {@code outputDir}, returning the file written. */
    public Path convert(Format format, ConversionOptions options, Path outputDir) throws Exception {
        BufferedImage src = source();
        if (src == null) {
            throw new IllegalStateException(error == null ? "could not read image" : error);
        }
        ConversionResult result = EngineApi.convert(src, file.getName(), format, options);
        Files.createDirectories(outputDir);
        // allFiles() is the main output plus any companion the format wants alongside it.
        Path destination = null;
        for (var out : result.allFiles()) {
            Path path = outputDir.resolve(out.name());
            Files.write(path, out.bytes());
            if (destination == null) {
                destination = path;
            }
        }
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
