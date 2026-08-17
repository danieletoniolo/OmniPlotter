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
     * Renders a character sequence as one or more Python {@code bytes} literals.
     *
     * <p>Port of {@code stringToPythonBytes} (tmp/index.html:698). Three behaviours are worth
     * knowing:
     *
     * <ul>
     *   <li>The quote character is chosen by counting: whichever of {@code "} or {@code '} appears
     *       less often in the data becomes the delimiter, and only the other one gets escaped.</li>
     *   <li>Octal escapes are used for values below 64, but only when the next character is not a
     *       digit 0-7, since {@code \\1} followed by {@code 7} would read as {@code \\17}.</li>
     *   <li>With {@code lmax} above 6 the output is split into several adjacent literals, one per
     *       line. Casio's on-calc editor will not open lines longer than 256 characters.</li>
     * </ul>
     *
     * @param allowOctal emit {@code \\a} and octal escapes. False for the MaClasseTI.fr target,
     *                   whose Python does not accept them.
     */
    public static String stringToPythonBytes(CharSequence s, int lmax, boolean allowOctal) {
        int n = s.length();
        int singleQuotes = 0;
        int doubleQuotes = 0;
        for (int i = 0; i < n; i++) {
            char v = s.charAt(i);
            if (v == '"') {
                doubleQuotes++;
            } else if (v == '\'') {
                singleQuotes++;
            }
        }
        boolean useSingle = doubleQuotes > singleQuotes;

        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int v = s.charAt(i);
            int next = i < n - 1 ? s.charAt(i + 1) : -1;
            String c;
            if (allowOctal && v == 7) c = "\\a";
            else if (v == 8) c = "\\b";
            else if (v == 9) c = "\\t";
            else if (v == 10) c = "\\n";
            else if (v == 11) c = "\\v";
            else if (v == 12) c = "\\f";
            else if (v == 13) c = "\\r";
            else if (v == 92) c = "\\\\";
            else if (v == 34 && doubleQuotes <= singleQuotes) c = "\\\"";
            else if (v == 39 && doubleQuotes > singleQuotes) c = "\\'";
            else if (v >= 32 && v <= 0x7E) c = String.valueOf((char) v);
            else if (allowOctal && v <= 077 && (next < '0' || next > '7')) c = "\\" + Integer.toOctalString(v);
            else {
                String hex = Integer.toHexString(v);
                c = "\\x" + (v < 0x10 ? "0" + hex : hex);
            }

            if (lmax >= 7 && line.length() + c.length() + 3 >= lmax) {
                out.append(quote(line, useSingle)).append('\n');
                line.setLength(0);
            }
            line.append(c);
        }
        out.append(quote(line, useSingle)).append('\n');
        return out.toString();
    }

    private static String quote(CharSequence body, boolean useSingle) {
        return useSingle ? "b'" + body + "'" : "b\"" + body + "\"";
    }

    /**
     * Encodes a generated script to bytes the way the reference writes it out.
     *
     * <p>{@code addBlobFileLink} pushes each character's code into a {@code Uint8Array}, so a
     * character above 0xFF contributes only its low byte. That is reachable: the RLE writes palette
     * indices straight into the string, and a large palette pushes them past 255.
     */
    public static byte[] scriptToBytes(CharSequence script) {
        byte[] out = new byte[script.length()];
        for (int i = 0; i < script.length(); i++) {
            out[i] = (byte) script.charAt(i);
        }
        return out;
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
