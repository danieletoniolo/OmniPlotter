package com.github.casiopicture.engine.converter;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.util.Palette;
import com.github.casiopicture.engine.util.PixelBuffer;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ImagePreprocessor {

    // Format groups for cleaner processing logic
    private static final Set<Format> QUANTIZE_FORMATS = EnumSet.of(
        Format.TI_8XV, Format.TI_GRAPHICS_PY, Format.TI_DRAW_CE_PY, Format.TI_DRAW_CX_PY
    );

    private static final Set<Format> MONOCHROME_WITH_FLATTEN_FORMATS = EnumSet.of(
        Format.TI_8XI, Format.TI_83I, Format.TI_73I, Format.TI_82I,
        Format.TI_85I, Format.TI_86I, Format.ZPIC
    );

    private static final Set<Format> MONOCHROME_SIMPLE_FORMATS = EnumSet.of(
        Format.MICROBIT_PY, Format.TI_HUB_MB_PY,
        Format.GRAPHIC_G3_PY, Format.GINT_G3_PY, Format.NSP_NS_PY
    );

    private static final Set<Format> INDEXED_CASIO_FORMATS = EnumSet.of(
        Format.I_C2P, Format.CP_I_G3P, Format.CP01_I_G3P, Format.CP01_I_G4P
    );

    /**
     * Private constructor to prevent instantiation
     */
    private ImagePreprocessor() {}

    /**
     * Preprocesses an image for conversion to a calculator format.
     *
     * @param inputImageBytes The raw bytes of the source image.
     * @param format          The target format.
     * @param options         The conversion options.
     * @return The preprocessed image as PNG bytes.
     * @throws Exception if any part of the preprocessing fails.
     */
    public static byte[] preprocess(byte[] inputImageBytes, Format format, ConversionOptions options) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(inputImageBytes));

        // 1. Handle resizing
        image = resize(image, options);

        // 2. Handle fit (centering on canvas)
        if (options.fit()) {
            image = fitToCanvas(image, options);
        }

        // 3. Apply format-specific processing
        image = applyFormatSpecificProcessing(image, format, options);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private static BufferedImage resize(BufferedImage image, ConversionOptions options) {
        if (options.keepRatio()) {
            if (image.getWidth() > options.width() || image.getHeight() > options.height()) {
                return Scalr.resize(image, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, options.width(), options.height());
            }
            return image;
        } else {
            return Scalr.resize(image, Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, options.width(), options.height());
        }
    }

    private static BufferedImage fitToCanvas(BufferedImage image, ConversionOptions options) {
        BufferedImage canvas = new BufferedImage(options.width(), options.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        int x = (canvas.getWidth() - image.getWidth()) / 2;
        int y = (canvas.getHeight() - image.getHeight()) / 2;
        g.drawImage(image, x, y, null);
        g.dispose();
        return canvas;
    }

    private static BufferedImage applyFormatSpecificProcessing(BufferedImage image, Format format, ConversionOptions options) throws IOException {
        if (QUANTIZE_FORMATS.contains(format) && options.colors() > 0) {
            return quantize(image, options.colors());
        }

        if (format == Format.TI_8CI) {
            return remapToPalette(image);
        }

        if (MONOCHROME_WITH_FLATTEN_FORMATS.contains(format)) {
            image = flatten(image);
            image = toGrayscale(image);
            return toBlackAndWhite(image);
        }

        if (MONOCHROME_SIMPLE_FORMATS.contains(format)) {
            image = toGrayscale(image);
            return toBlackAndWhite(image);
        }

        if (INDEXED_CASIO_FORMATS.contains(format)) {
            image = flatten(image);
            image = toGrayscale(image);
            return toBlackAndWhite(image);
        }

        // Default: no additional processing needed
        return image;
    }

    private static BufferedImage quantize(BufferedImage source, int colors) {
        return ImageQuantizer.quantize(source, colors);
    }

    /**
     * Flattens a potentially transparent image onto a white background.
     */
    private static BufferedImage flatten(BufferedImage source) {
        BufferedImage flat = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return flat;
    }

    private static BufferedImage toGrayscale(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return source;
        }
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return gray;
    }

    private static BufferedImage toBlackAndWhite(BufferedImage source) {
        BufferedImage bw = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = bw.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return bw;
    }

    /**
     * Remaps an image to the TI-8CI fixed palette.
     */
    private static BufferedImage remapToPalette(BufferedImage source) throws IOException {
        Palette palette = Palette.load("pal8ci.png");

        BufferedImage remapped = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, IndexColorModelFactory.create(palette));
        byte[] pixels = ((DataBufferByte) remapped.getRaster().getDataBuffer()).getData();
        PixelBuffer px = PixelBuffer.of(source);

        for (int i = 0; i < px.size(); i++) {
            pixels[i] = (byte) palette.nearest(px.raw(i));
        }
        return remapped;
    }
}
