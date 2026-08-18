package com.github.omniplotter.engine;

import com.github.omniplotter.engine.converter.ImagePreprocessor;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.encoder.FileEncoder;
import com.github.omniplotter.engine.util.Exif;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

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
        return encode(ImagePreprocessor.preprocess(image, format, options),
            originalFileName, format, options);
    }

    /** Encodes an image that has already been through {@link #preview}, skipping preprocessing. */
    public static ConversionResult encode(BufferedImage processed, String originalFileName,
                                          Format format, ConversionOptions options) throws IOException {
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
            throw new IOException(unreadable(imageBytes));
        }
        // Before anything measures it: a portrait photograph that arrives on its side would
        // otherwise be fitted to the canvas as though it were landscape.
        return Exif.applyOrientation(image, imageBytes);
    }

    /**
     * Says what went wrong with enough detail to act on.
     *
     * <p>HEIC gets named because it is what an iPhone saves by default, so it is the likeliest
     * thing anyone hands this first — and because "not a readable image" for the format your camera
     * produces reads as the application being broken.
     */
    private static String unreadable(byte[] imageBytes) {
        if (isHeif(imageBytes)) {
            return "HEIC/HEIF images cannot be read: no pure-Java decoder exists for them. "
                + "Export as PNG or JPEG and convert that.";
        }
        String readable = Arrays.stream(ImageIO.getReaderFormatNames())
            .map(format -> format.toLowerCase(Locale.ROOT))
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));
        // Both callers already say which file this was about, so the message does not repeat it.
        return "Not a readable image. Readable formats are: " + readable + ".";
    }

    /** The ISO base media brand sits at offset 4, after the size of the first box. */
    private static boolean isHeif(byte[] data) {
        if (data == null || data.length < 12
            || data[4] != 'f' || data[5] != 't' || data[6] != 'y' || data[7] != 'p') {
            return false;
        }
        String brand = new String(data, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("msf")
            || brand.startsWith("hev") || brand.startsWith("avi");
    }
}
