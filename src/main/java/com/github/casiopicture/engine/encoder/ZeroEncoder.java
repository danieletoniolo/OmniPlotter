package com.github.casiopicture.engine.encoder;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.util.EncoderUtils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ZeroEncoder implements FileEncoder {

    /**
     * Private constructor to prevent direct instantiation
     */
    private ZeroEncoder() {}

    /**
     * Thread-safe singleton holder pattern
     */
    private static final class Holder {
        static final ZeroEncoder INSTANCE = new ZeroEncoder();
    }

    /**
     * Gets the singleton {@link ZeroEncoder} instance
     *
     * @return The singleton {@link ZeroEncoder} instance
     */
    public static ZeroEncoder getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public ConversionResult encode(BufferedImage image, Format format, String originalFileName, ConversionOptions options) throws IOException {
        if (format != Format.ZERO_BIN) {
            throw new IllegalArgumentException("Unsupported format for ZeroEncoder: " + format);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        ByteArrayOutputStream pixelData = new ByteArrayOutputStream();

        int n = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                // Only include non-transparent pixels (Alpha >= 255)
                if (a >= 255) {
                    n++;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;

                    // Pack color to RGB565: RRRRRGGGGGGBBBBB (little-endian)
                    int color = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);

                    // 10-byte structure per pixel
                    pixelData.write(0x0E);
                    pixelData.write(0);
                    pixelData.write(0);
                    pixelData.write(0);
                    
                    // Coordinates (X, Y starting at row 22)
                    pixelData.write(x & 0xFF);
                    pixelData.write((x >> 8) & 0xFF);
                    int fileY = y + 22;
                    pixelData.write(fileY & 0xFF);
                    pixelData.write((fileY >> 8) & 0xFF);
                    
                    // Color
                    pixelData.write(color & 0xFF);
                    pixelData.write((color >> 8) & 0xFF);
                }
            }
        }

        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        // 8-byte header: marker [0x32, 0, 0, 0] + 4-byte count of valid pixels
        resultStream.write(0x32);
        resultStream.write(0);
        resultStream.write(0);
        resultStream.write(0);
        resultStream.write(EncoderUtils.intToLittleEndian(n, 4));
        resultStream.write(pixelData.toByteArray());

        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        return new ConversionResult(resultStream.toByteArray(), baseName + ".bin");
    }
}
