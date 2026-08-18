package com.github.omniplotter.engine;

import com.github.omniplotter.engine.converter.ImagePreprocessor;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.encoder.FileEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Entry point for converting an image to a calculator file.
 *
 * <p>Two stages: {@link ImagePreprocessor} produces the exact pixels the format calls for, then a
 * {@link FileEncoder} wraps them in its container. Both the CLI and the UI go through here, so
 * there is one conversion path to keep correct.
 */
public final class EngineApi {

    private EngineApi() {}

    /** Converts an already-decoded image. */
    public static ConversionResult convert(BufferedImage image, String originalFileName,
                                           Format format, ConversionOptions options) throws IOException {
        BufferedImage processed = ImagePreprocessor.preprocess(image, format, options);
        return FileEncoder.getEncoder(format).encode(processed, format, originalFileName, options);
    }

    /** Converts encoded image bytes — PNG, JPEG, or anything else ImageIO reads. */
    public static ConversionResult convert(byte[] inputImageBytes, String originalFileName,
                                           Format format, ConversionOptions options) throws IOException {
        return convert(decode(inputImageBytes, originalFileName), originalFileName, format, options);
    }

    public static ConversionResult convert(byte[] inputImageBytes, String originalFileName,
                                           String formatId, ConversionOptions options) throws IOException {
        return convert(inputImageBytes, originalFileName, Format.fromString(formatId), options);
    }

    /**
     * Runs only the preprocessing stage.
     *
     * <p>The UI previews this rather than a scaled-down source, so what the user sees is the actual
     * quantised and remapped result that will be encoded.
     */
    public static BufferedImage preview(BufferedImage image, Format format, ConversionOptions options)
            throws IOException {
        return ImagePreprocessor.preprocess(image, format, options);
    }

    public static BufferedImage decode(byte[] imageBytes, String name) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IOException("Not a readable image: " + name);
        }
        return image;
    }
}
