/*
 * OmniPlotter — convert images to calculator picture and script formats.
 * Copyright (C) 2026 Daniele Toniolo
 *
 * Derived from TI-Planet's img2calc (https://github.com/TI-Planet/img2calc),
 * by Xavier Andreani (@critor) and Adrien Bertrand (@Adriweb).
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.github.omniplotter.engine.encoder;

import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.util.EncoderUtils;
import com.github.omniplotter.engine.util.PixelBuffer;

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
        if (format != Format.ZPIC) {
            throw new IllegalArgumentException("Unsupported format for ZeroEncoder: " + format);
        }

        PixelBuffer px = PixelBuffer.of(image);
        ByteArrayOutputStream pixelData = new ByteArrayOutputStream();

        // The format is a list of draw-pixel commands rather than a raster, so fully transparent
        // pixels are simply left out and the drawing shows through.
        //
        // Coordinates advance against the *requested* canvas width, not the image's own: when the
        // preprocessed image is narrower than the target canvas, the reference still wraps at the
        // canvas edge, which shears the picture. Reproduced deliberately.
        int wrapWidth = options.width() > 0 ? options.width() : px.width();
        int x = 0;
        int y = 22;   // the first drawable row on the device
        int n = 0;

        for (int i = 0; i < px.size(); i++) {
            int[] raw = px.raw(i);
            if (raw != null && raw[3] >= 255) {
                n++;
                int color = px.packed(i, 5, 6, 5, 0);
                pixelData.write(0x0E);
                pixelData.write(0);
                pixelData.write(0);
                pixelData.write(0);
                pixelData.write(x & 0xFF);
                pixelData.write((x >> 8) & 0xFF);
                pixelData.write(y & 0xFF);
                pixelData.write((y >> 8) & 0xFF);
                pixelData.write(color & 0xFF);
                pixelData.write((color >> 8) & 0xFF);
            }
            x = (x + 1) % wrapWidth;
            if (x == 0) {
                y++;
            }
        }

        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        // 8-byte header: marker [0x32, 0, 0, 0] then the number of pixel commands that follow.
        resultStream.write(0x32);
        resultStream.write(0);
        resultStream.write(0);
        resultStream.write(0);
        resultStream.write(EncoderUtils.intToLittleEndian(n, 4));
        resultStream.write(pixelData.toByteArray());

        // Slot-numbered on the device, and the file carries no extension.
        return new ConversionResult(resultStream.toByteArray(), "pic" + options.onCalcNumber());
    }
}
