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
package com.github.omniplotter.engine.util;

import java.awt.image.BufferedImage;

/**
 * A row-major RGBA view of an image, with the same accessors the reference encoders use.
 *
 * <p>This mirrors {@code img_a}, the {@code Uint8Array} img2calc pulls off a canvas, and the
 * {@code getPixel_*} helpers that read it (tmp/index.html:658-684). Centralising them here keeps
 * every encoder reading pixels the same way.
 *
 * <p><b>Reads past the last pixel are legal and meaningful.</b> Several reference encoders pack two
 * or eight pixels per byte and step over the image without checking the tail, so on an image whose
 * pixel count is not a multiple of the packing they read off the end of the array. JavaScript
 * answers {@code undefined} there, and each helper coerces that differently — which is why the tail
 * bytes look the way they do in real files. Those coercions are reproduced exactly:
 *
 * <ul>
 *   <li>{@link #packed} — {@code undefined >> n} is 0, so the whole pixel reads as colour 0.</li>
 *   <li>{@link #simplified} — likewise 0 in every channel, giving {@code [0,0,0,0]}.</li>
 *   <li>{@link #raw} — an array of {@code undefined}s, represented here as {@code null}, which
 *       {@link Palette#nearest} turns into index -1 the same way NaN comparisons do.</li>
 * </ul>
 */
public final class PixelBuffer {

    private final byte[] rgba;
    private final int width;
    private final int height;

    private PixelBuffer(byte[] rgba, int width, int height) {
        this.rgba = rgba;
        this.width = width;
        this.height = height;
    }

    /** Flattens an image to RGBA, matching what a canvas would hand the browser tool. */
    public static PixelBuffer of(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgba = new byte[width * height * 4];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                rgba[i++] = (byte) (argb >> 16);
                rgba[i++] = (byte) (argb >> 8);
                rgba[i++] = (byte) argb;
                rgba[i++] = (byte) (argb >> 24);
            }
        }
        return new PixelBuffer(rgba, width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Number of pixels; the reference writes this as {@code img.width * img.height}. */
    public int size() {
        return width * height;
    }

    private boolean has(int i) {
        return i >= 0 && i < width * height;
    }

    private int channel(int i, int c) {
        return has(i) ? rgba[i * 4 + c] & 0xFF : 0;
    }

    /**
     * Packs pixel {@code i} into an integer with the given per-channel bit widths.
     *
     * <p>Port of {@code getPixel_RGBA_int}. Note the channel order the reference uses: blue lands in
     * the low bits and red in the high ones, so {@code packed(i, 5, 6, 5, 0)} is RGB-565.
     */
    public int packed(int i, int rBits, int gBits, int bBits, int aBits) {
        return channel(i, 2) >> (8 - rBits)
             | channel(i, 1) >> (8 - gBits) << rBits
             | channel(i, 0) >> (8 - bBits) << (rBits + gBits)
             | channel(i, 3) >> (8 - aBits) << (rBits + gBits + bBits);
    }

    /**
     * Pixel {@code i} as {@code [r, g, b, a]}, or {@code null} past the end of the image.
     *
     * <p>Port of {@code getPixel_raw}. The null stands in for JavaScript's array-of-undefined; see
     * the class comment.
     */
    public int[] raw(int i) {
        if (!has(i)) {
            return null;
        }
        return new int[]{
            rgba[i * 4] & 0xFF, rgba[i * 4 + 1] & 0xFF,
            rgba[i * 4 + 2] & 0xFF, rgba[i * 4 + 3] & 0xFF,
        };
    }

    /**
     * Pixel {@code i} with each channel truncated to the given bit width and re-expanded to 8 bits.
     *
     * <p>Port of {@code getPixel_RGBA_array} / {@code simpl_byte}. This is how the Python generators
     * collapse near-identical colours into one palette entry.
     */
    public int[] simplified(int i, int rBits, int gBits, int bBits, int aBits) {
        return new int[]{
            simplify(channel(i, 0), rBits),
            simplify(channel(i, 1), gBits),
            simplify(channel(i, 2), bBits),
            simplify(channel(i, 3), aBits),
        };
    }

    /** Port of {@code simpl_byte}: keep the top {@code bits} bits, zero the rest. */
    public static int simplify(int value, int bits) {
        return (value >> (8 - bits)) << (8 - bits);
    }
}
