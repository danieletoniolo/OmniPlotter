package com.github.casiopicture.engine;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.data.FormatConfig;
import com.github.casiopicture.engine.converter.ImagePreprocessor;
import com.github.casiopicture.engine.encoder.FileEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * The main entry point for the Img2Calc conversion API.
 */
public class EngineApi {

    /**
     * Private constructor to prevent instantiation
     */
    private EngineApi() {}

    /**
     * Converts an input image to a target calculator format.
     *
     * @param inputImageBytes  The raw bytes of the source image.
     * @param originalFileName The original name of the file, used for naming suggestions.
     * @param targetFormat     The desired output format key (e.g., "im8c.8xv").
     * @param options          The conversion options like size and color depth.
     * @return A {@link ConversionResult} containing the final file bytes and suggested name.
     * @throws Exception if any part of the conversion process fails.
     */
    public static ConversionResult convert(
        byte[] inputImageBytes,
        String originalFileName,
        String targetFormat,
        ConversionOptions options
    ) throws Exception {
        Format format = Format.fromString(targetFormat);
        return convert(inputImageBytes, originalFileName, format, options);
    }

    /**
     * Converts an input image to a target calculator format.
     *
     * @param inputImageBytes  The raw bytes of the source image.
     * @param originalFileName The original name of the file, used for naming suggestions.
     * @param format           The desired output format.
     * @param options          The conversion options like size and color depth.
     * @return A {@link ConversionResult} containing the final file bytes and suggested name.
     * @throws Exception if any part of the conversion process fails.
     */
    public static ConversionResult convert(
        byte[] inputImageBytes,
        String originalFileName,
        Format format,
        ConversionOptions options
    ) throws Exception {

        // Load the configuration for the format (used for validation)
        FormatConfig config = FormatConfig.of(format);
        if (config == null) {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }

        // Pre-process the image
        byte[] pngBytes = ImagePreprocessor.preprocess(inputImageBytes, format, options);

        // Load the intermediate PNG into a BufferedImage
        BufferedImage processedImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (processedImage == null) {
            throw new IOException("Failed to read intermediate PNG from ImageMagick output.");
        }

        // Select and use the appropriate encoder
        FileEncoder encoder = FileEncoder.getEncoder(format);
        return encoder.encode(processedImage, format, originalFileName, options);
    }
}
