package com.github.casiopicture.engine.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads back a Casio picture file and reports what a calculator would find in it.
 *
 * <p>These containers carry the same length in several places and a pair of checksums derived from
 * it, so a file can look plausible while being rejected on the device. This walks the structure and
 * says which field disagrees, which is far easier to act on than "the calculator won't open it".
 */
public final class CasioFileInspector {

    private CasioFileInspector() {}

    public record Field(String name, String value, boolean ok) {}

    public record Report(String kind, List<Field> fields, List<String> problems, byte[] pixels) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private static int be(byte[] data, int at, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) {
            v = (v << 8) | (data[at + i] & 0xFF);
        }
        return v;
    }

    /** Inspects a {@code .g3p}, {@code .g4p} or {@code .c2p} file. */
    public static Report inspect(byte[] data) {
        List<Field> fields = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        if (data.length < 0xD0) {
            problems.add("file is only " + data.length + " bytes; too short to be a Casio picture");
            return new Report("unknown", fields, problems, null);
        }

        // header1 is stored bit-inverted, so the magic has to be un-inverted to read it.
        byte[] magic = new byte[8];
        for (int i = 0; i < 8; i++) {
            magic[i] = (byte) ~data[i];
        }
        String signature = new String(magic, java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean isC2p = signature.startsWith("CASIO");
        String kind = isC2p ? "c2p" : signature.startsWith("USBPower") ? "g3p/g4p" : "unknown";
        fields.add(new Field("signature", signature.trim(), !"unknown".equals(kind)));
        if ("unknown".equals(kind)) {
            problems.add("leading magic is neither USBPower nor CASIO once un-inverted");
            return new Report(kind, fields, problems, null);
        }

        // Both layouts put the 16-byte format tag at 0x20.
        String tag = new String(data, 0x20, 16, java.nio.charset.StandardCharsets.ISO_8859_1)
            .replace('\0', ' ').trim();
        fields.add(new Field("format tag @0x20", tag, !tag.isEmpty()));

        int dataBlockSize = be(data, 0x30, 4);
        int footerLength = be(data, 0x44, 4);
        int payloadLength = be(data, 0x38, 4);
        int compressedLength = be(data, 0xBC, 4);
        int width = be(data, 0xC0, 4);
        int height = be(data, 0xC4, 2);
        int depth = be(data, 0xC6, 2);

        fields.add(new Field("width @0xC0", String.valueOf(width), width > 0 && width <= 1024));
        fields.add(new Field("height @0xC4", String.valueOf(height), height > 0 && height <= 1024));
        fields.add(new Field("colour depth @0xC6",
            depth == 0x10 ? "16-bit RGB-565" : depth == 0x03 ? "4-bit indexed" : "0x" + Integer.toHexString(depth),
            depth == 0x10 || depth == 0x03));
        fields.add(new Field("compressed size @0xBC", compressedLength + " bytes",
            compressedLength > 0 && compressedLength <= data.length));
        fields.add(new Field("payload size @0x38", payloadLength + " bytes", payloadLength > 0));
        fields.add(new Field("footer size @0x44", footerLength + " bytes", footerLength >= 0));
        fields.add(new Field("data block @0x30", dataBlockSize + " bytes", dataBlockSize > 0));

        // The whole-file length is stored in header1, also inverted, and must agree with the
        // actual size. c2p keeps it in 3 bytes after a 23-byte magic; g3p in 4 bytes after a
        // 14-byte magic plus two derived bytes.
        int declared = 0;
        for (int i = 0; i < (isC2p ? 3 : 4); i++) {
            declared = (declared << 8) | (~data[(isC2p ? 23 : 16) + i] & 0xFF);
        }
        boolean sizeOk = declared == data.length;
        fields.add(new Field("declared file size", declared + " bytes (actual " + data.length + ")", sizeOk));
        if (!sizeOk) {
            problems.add("the size recorded in the header does not match the file; "
                + "a calculator checks this and will refuse the file");
        }

        // The compressed stream starts after the palette block and the 4-byte length prefix; c2p
        // carries a 16-byte palette where g3p carries 4. Obfuscated variants have their bits
        // inverted and rotated, so the zlib magic is what tells the two apart.
        int at = isC2p ? 0xDC : 0xD0;
        // The size at 0xBC counts the 4-byte length prefix that precedes the stream, so the stream
        // body itself is four bytes shorter.
        int streamLength = compressedLength - 4;
        if (streamLength < 0 || at + streamLength > data.length) {
            problems.add("the compressed stream runs past the end of the file");
            return new Report(kind, fields, problems, null);
        }
        byte[] stream = new byte[streamLength];
        System.arraycopy(data, at, stream, 0, stream.length);

        boolean obfuscated = (stream[0] & 0xFF) != 0x78;
        fields.add(new Field("stream", obfuscated ? "obfuscated (CP variant)" : "plain zlib", true));
        if (obfuscated) {
            for (int i = 0; i < stream.length; i++) {
                int b = stream[i] & 0xFF;
                // Undo swap(5,3) then the inversion.
                b = ((b & 0x07) << 5) | ((b & 0xF8) >> 3);
                stream[i] = (byte) ~b;
            }
        }

        byte[] pixels = null;
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(stream);
            var out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            inflater.end();
            pixels = out.toByteArray();

            int expected = depth == 0x10 ? width * height * 2 : (width * height + 1) / 2;
            boolean lengthOk = pixels.length == expected;
            fields.add(new Field("decompressed", pixels.length + " bytes (expected " + expected + ")", lengthOk));
            if (!lengthOk) {
                problems.add("the pixel data does not match the declared size and depth");
            }
        } catch (DataFormatException e) {
            problems.add("the compressed stream does not decompress: " + e.getMessage());
        }

        return new Report(kind, fields, problems, pixels);
    }
}
