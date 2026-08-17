package com.github.casiopicture.engine.util;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A utility class with static methods for encoding, mirroring the global helper functions in index.html.
 */
public final class EncoderUtils {

    private EncoderUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Compresses pixel data using Run-Length Encoding (RLE).
     * Output format: pairs of (runLength, colorIndex), where runLength is max 255.
     *
     * @param pixelData The list of pixel color indices.
     * @return A StringBuilder containing escaped hex string for Python byte literals.
     */
    public static StringBuilder compressRLE(List<Byte> pixelData) {
        StringBuilder rleHex = new StringBuilder();
        int i = 0;
        while (i < pixelData.size()) {
            byte current = pixelData.get(i);
            int runLength = 1;
            while ((i + runLength) < pixelData.size() && pixelData.get(i + runLength) == current) {
                runLength++;
            }

            int originalRunLength = runLength;
            while (runLength > 0) {
                int chunk = Math.min(runLength, 255);
                rleHex.append(String.format("\\x%02x\\x%02x", chunk, current & 0xFF));
                runLength -= chunk;
            }
            i += originalRunLength;
        }
        return rleHex;
    }

    /**
     * Converts a long to a little-endian byte array of a specific size.
     *
     * @param value The long value.
     * @param n     The number of bytes in the output array.
     * @return The little-endian byte array.
     */
    public static byte[] intToLittleEndian(long value, int n) {
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }

    /**
     * Converts an integer to a big-endian byte array of a specific size.
     *
     * @param value The integer value.
     * @param n     The number of bytes in the output array.
     * @return The big-endian byte array.
     */
    public static byte[] intToBigEndian(int value, int n) {
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = (byte) (value >> ((n - 1 - i) * 8));
        }
        return bytes;
    }

    /**
     * Converts a string to a fixed-size, null-padded ASCII byte array.
     *
     * @param str The input string.
     * @param n   The desired length of the byte array.
     * @return The padded byte array.
     */
    public static byte[] stringToPaddedASCII(String str, int n) {
        byte[] stringBytes = str.getBytes(StandardCharsets.US_ASCII);
        byte[] paddedBytes = new byte[n];
        System.arraycopy(stringBytes, 0, paddedBytes, 0, Math.min(stringBytes.length, n));
        return paddedBytes;
    }

    /**
     * Calculates the TI 8.x checksum for a given data array.
     *
     * @param data The input byte array.
     * @return The 16-bit checksum.
     */
    public static int calculateTIChecksum(byte[] data) {
        int checksum = 0;
        for (byte b : data) {
            checksum = (checksum + (b & 0xFF)) & 0xFFFF;
        }
        return checksum;
    }

    /**
     * Inverts the bits of each byte in an array in-place.
     *
     * @param arr The byte array to modify.
     */
    public static void invertArray(byte[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (byte) (~arr[i]);
        }
    }

    /**
     * Swaps bit ranges within each byte of an array in-place.
     * Matches JavaScript's binarySwapArray function:
     * - mask1 = (1 << n1) - 1  (lower n1 bits)
     * - mask2 = ((1 << n2) - 1) << n1  (next n2 bits after n1 bits)
     * Result: lower n1 bits move to upper position, upper n2 bits move to lower position
     *
     * @param arr The byte array to modify.
     * @param n1  Number of lower bits to swap with upper bits.
     * @param n2  Number of upper bits to swap with lower bits.
     */
    public static void swapArrayBits(byte[] arr, int n1, int n2) {
        int mask1 = (1 << n1) - 1;  // Lower n1 bits
        int mask2 = ((1 << n2) - 1) << n1;  // Next n2 bits
        for (int i = 0; i < arr.length; i++) {
            int b = arr[i] & 0xFF;  // Convert to unsigned
            // Swap: lower n1 bits move up by n2, upper n2 bits move down by n1
            arr[i] = (byte)(((b & mask1) << n2) | ((b & mask2) >> n1));
        }
    }

    /**
     * Packs an RGB color into a 16-bit (5:6:5) integer.
     *
     * @param c The input color.
     * @return The packed 16-bit integer.
     */
    public static int packColor565(Color c) {
        int r = c.getRed() >> 3;    // 5 bits for red
        int g = c.getGreen() >> 2;  // 6 bits for green
        int b = c.getBlue() >> 3;   // 5 bits for blue
        return (r << 11) | (g << 5) | b;
    }

    /**
     * Pads a byte array on the right with null bytes to a specific length.
     *
     * @param data The input byte array.
     * @param n    The target length.
     * @return The padded byte array.
     */
    public static byte[] padRight(byte[] data, int n) {
        if (data.length >= n) {
            return data;
        }
        byte[] padded = new byte[n];
        System.arraycopy(data, 0, padded, 0, data.length);
        return padded;
    }

    /**
     * Encodes an image to monochrome bitmap data (1 bit per pixel).
     * Black pixels (brightness < 128) are set to 1, white pixels to 0.
     *
     * @param image The BufferedImage to encode.
     * @param msbFirst If true, the most significant bit represents the leftmost pixel (TI format).
     *                 If false, the least significant bit represents the leftmost pixel (Zero format).
     * @return Byte array containing the monochrome bitmap data.
     */
    public static byte[] encodeMonochromeBitmap(BufferedImage image, boolean msbFirst) {
        int width = image.getWidth();
        int height = image.getHeight();
        int bytesPerRow = (width + 7) / 8;
        byte[] data = new byte[bytesPerRow * height];
        int index = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x += 8) {
                int byteValue = 0;
                for (int bit = 0; bit < 8; bit++) {
                    if (x + bit < width) {
                        Color c = new Color(image.getRGB(x + bit, y));
                        if (c.getRed() < 128) { // Black pixel
                            if (msbFirst) {
                                byteValue |= (1 << (7 - bit));
                            } else {
                                byteValue |= (1 << bit);
                            }
                        }
                    }
                }
                data[index++] = (byte) byteValue;
            }
        }
        return data;
    }
}
