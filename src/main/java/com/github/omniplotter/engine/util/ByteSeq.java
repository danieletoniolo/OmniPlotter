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

/**
 * A growable byte sequence that remembers the untruncated value written to each position.
 *
 * <p>This exists for one reason: the TI-Z80 checksum and the TI-Z80 file disagree about what a byte
 * is. The reference accumulates output in a plain JavaScript array, which happily holds values
 * outside 0..255, and then uses it two ways — {@code new Uint8Array(data)} for the file, which
 * truncates, and {@code crcti8x(data)}, which sums the array as-is. Two real cases reach that gap:
 *
 * <ul>
 *   <li>{@code 8ci} on an image with an odd pixel count pushes -1 for the pair that runs past the
 *       end. The file gets 0xFF; the checksum gets -1.</li>
 *   <li>{@code im8c.8xv} on an image with more than 256 colours pushes palette indices above 255.
 *       The file gets the low byte; the checksum gets the whole index.</li>
 * </ul>
 *
 * <p>So {@link #toBytes()} truncates and {@link #rawSum()} does not, and a file only matches the
 * reference if both go through this class. Keeping a plain {@code byte[]} would silently produce a
 * checksum the reference never writes.
 */
public final class ByteSeq {

    private int[] values = new int[64];
    private int size;

    public ByteSeq() {}

    public static ByteSeq of(int... values) {
        return new ByteSeq().add(values);
    }

    private void ensure(int extra) {
        if (size + extra > values.length) {
            int capacity = Math.max(values.length * 2, size + extra);
            int[] grown = new int[capacity];
            System.arraycopy(values, 0, grown, 0, size);
            values = grown;
        }
    }

    public ByteSeq add(int value) {
        ensure(1);
        values[size++] = value;
        return this;
    }

    public ByteSeq add(int... vs) {
        ensure(vs.length);
        for (int v : vs) {
            values[size++] = v;
        }
        return this;
    }

    public ByteSeq add(byte[] bytes) {
        ensure(bytes.length);
        for (byte b : bytes) {
            values[size++] = b & 0xFF;
        }
        return this;
    }

    public ByteSeq add(ByteSeq other) {
        ensure(other.size);
        System.arraycopy(other.values, 0, values, size, other.size);
        size += other.size;
        return this;
    }

    /** Appends an ASCII string, padded or truncated to {@code n} bytes with {@code pad}. */
    public ByteSeq addPadded(String str, int n, int pad) {
        for (int i = 0; i < n; i++) {
            add(i < str.length() ? str.charAt(i) & 0xFF : pad);
        }
        return this;
    }

    public int size() {
        return size;
    }

    /** Sum of the values as written, without truncation, masked to 16 bits — {@code crcti8x}. */
    public int rawSum() {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += values[i];
        }
        return sum & 0xFFFF;
    }

    /** The sequence as file bytes, each value truncated to its low 8 bits. */
    public byte[] toBytes() {
        byte[] out = new byte[size];
        for (int i = 0; i < size; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
