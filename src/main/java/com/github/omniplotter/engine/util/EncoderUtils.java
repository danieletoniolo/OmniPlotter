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

import java.nio.charset.StandardCharsets;

/**
 * Byte-level helpers shared by the encoders, ported from the reference's global functions.
 */
public final class EncoderUtils {

    private EncoderUtils() {}

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
}
