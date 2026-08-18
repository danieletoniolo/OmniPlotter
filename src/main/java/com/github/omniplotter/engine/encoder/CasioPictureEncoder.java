package com.github.omniplotter.engine.encoder;

import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.util.EncoderUtils;
import com.github.omniplotter.engine.util.Palette;
import com.github.omniplotter.engine.util.PixelBuffer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

public class CasioPictureEncoder implements FileEncoder {

    /**
     * Private constructor to enforce singleton pattern
     */
    private CasioPictureEncoder() {}

    /**
     * Thread-safe singleton holder pattern
     */
    private static final class Holder {
        static final CasioPictureEncoder INSTANCE = new CasioPictureEncoder();
    }

    /**
     * Get the singleton instance of CasioPictureEncoder
     * @return The {@link CasioPictureEncoder} singleton instance
     */
    public static CasioPictureEncoder getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public ConversionResult encode(BufferedImage image, Format format, String originalFileName, ConversionOptions options) throws IOException {
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        if (baseName.length() > 8) {
            baseName = baseName.substring(0, 8);
        }

        byte[] pixelData = isColorFormat(format)
            ? extractColorPixels(image)
            : extractIndexedPixels(image);

        return buildFile(pixelData, image.getWidth(), image.getHeight(), baseName, format);
    }

    private boolean isColorFormat(Format format) {
        return format == Format.C2P || format == Format.CP_G3P ||
               format == Format.CP01_G3P || format == Format.CP01_G4P;
    }

    private byte[] extractColorPixels(BufferedImage image) {
        PixelBuffer px = PixelBuffer.of(image);
        ByteArrayOutputStream pixelData = new ByteArrayOutputStream();

        for (int i = 0; i < px.size(); i++) {
            // RGB-565, big-endian.
            int color = px.packed(i, 5, 6, 5, 0);
            pixelData.write((color >> 8) & 0xFF);
            pixelData.write(color & 0xFF);
        }
        return pixelData.toByteArray();
    }

    private byte[] extractIndexedPixels(BufferedImage image) throws IOException {
        PixelBuffer px = PixelBuffer.of(image);
        Palette palette = Palette.load("palcp.png");
        ByteArrayOutputStream pixelData = new ByteArrayOutputStream();

        // Two pixels per byte, walked as one flat run rather than row by row: on an odd width the
        // reference pairs the last pixel of a row with the first of the next, and on an odd pixel
        // count the final pair reads past the end. There `nearest` answers -1, and `x | -1` is -1,
        // so the byte written is 0xFF. Both behaviours are load-bearing for byte equality.
        for (int i = 0; i < px.size(); i += 2) {
            int icolor = 0;
            for (int j = 0; j < 2; j++) {
                icolor = (icolor << 4) | palette.nearest(px.raw(i + j));
            }
            pixelData.write(icolor);
        }
        return pixelData.toByteArray();
    }

    private boolean isC2pFormat(Format format) {
        return format == Format.C2P || format == Format.I_C2P;
    }

    private boolean isG3pOrG4pFormat(Format format) {
        return format == Format.CP_G3P || format == Format.CP01_G3P || format == Format.CP01_G4P ||
               format == Format.CP_I_G3P || format == Format.CP01_I_G3P || format == Format.CP01_I_G4P;
    }

    private boolean isObfuscatedFormat(Format format) {
        return format == Format.CP_G3P || format == Format.CP_I_G3P;
    }

    private static final byte[] C2P_HEADER1 = new byte[]{
        'C', 'A', 'S', 'I', 'O', 0, 0, 0,
        'c', '2', 'p', 0, 0, 0, 0, 0, 0,
        1, 0, 0x10, 0, 1, 0
    };
    private static final byte[] USB_HEADER1 = new byte[]{
        'U', 'S', 'B', 'P', 'o', 'w', 'e', 'r', '}', 0,
        0x10, 0, 0x10, 0
    };

    private byte[] compressData(byte[] pixelData) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
        deflater.setInput(pixelData);
        deflater.finish();
        ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            compressedStream.write(buffer, 0, count);
        }
        deflater.end();
        return compressedStream.toByteArray();
    }

    private ConversionResult buildFile(byte[] pixelData, int width, int height, String baseName, Format format) throws IOException {
        byte[] compressedData = compressData(pixelData);

        // Obfuscate for CP.G3P format if applicable
        if (isObfuscatedFormat(format)) {
            EncoderUtils.invertArray(compressedData);
            EncoderUtils.swapArrayBits(compressedData, 5, 3);
        }

        // Prepend the 4-byte big-endian length prefix (unconditionally done in original JS)
        ByteArrayOutputStream withLength = new ByteArrayOutputStream();
        withLength.write(EncoderUtils.intToBigEndian(compressedData.length, 4));
        withLength.write(compressedData);
        compressedData = withLength.toByteArray();

        byte[] footer = buildFooter(format);
        ByteArrayOutputStream file = new ByteArrayOutputStream();

        // Calculate correct total size
        int size = isC2pFormat(format)
            ? 216 + compressedData.length + footer.length
            : 204 + compressedData.length + footer.length;

        byte[] sizeBytes = EncoderUtils.intToBigEndian(size, 4);

        // Main Header
        byte[] header1 = isC2pFormat(format) ? C2P_HEADER1.clone() : USB_HEADER1.clone();
        header1 = buildHeader1(header1, size, sizeBytes, format);
        EncoderUtils.invertArray(header1);
        file.write(header1);
        file.write(new byte[isC2pFormat(format) ? 1 : 7]);

        if (isG3pOrG4pFormat(format)) {
            int byteVal1 = isObfuscatedFormat(format) ? sizeBytes[2] + sizeBytes[3] + 0xAB : -sizeBytes[2] - sizeBytes[3] + 7;
            int byteVal2 = isObfuscatedFormat(format) ? sizeBytes[3] + 0x98 : -sizeBytes[3] + 0x16;
            file.write(byteVal1 % 0x100);
            file.write(byteVal2 % 0x100);
        }

        // Data block header
        String header2Str = getHeader2String(format);
        byte[] header2 = header2Str.getBytes(StandardCharsets.US_ASCII);
        file.write(new byte[isC2pFormat(format) ? 4 : 2]);
        file.write(EncoderUtils.padRight(header2, 16));
        
        int dataBlockSize = 16 + 132 + (isC2pFormat(format) ? 32 : 20) + compressedData.length + footer.length + 4;
        file.write(EncoderUtils.intToBigEndian(dataBlockSize, 4));

        // Image metadata
        file.write(EncoderUtils.intToBigEndian(isObfuscatedFormat(format) ? 1 : 9, 4));
        file.write(EncoderUtils.intToBigEndian((isC2pFormat(format) ? 32 : 20) + compressedData.length, 4));
        file.write(new byte[8]);
        file.write(EncoderUtils.intToBigEndian(footer.length, 4));
        file.write(new byte[0x70]);

        // Image properties
        file.write(isObfuscatedFormat(format) ? EncoderUtils.intToLittleEndian(0x100, 4) : "0100".getBytes(StandardCharsets.US_ASCII));
        file.write(EncoderUtils.intToBigEndian(compressedData.length, 4));
        file.write(EncoderUtils.intToBigEndian(width, 4));
        file.write(EncoderUtils.intToBigEndian(height, 2));
        file.write(EncoderUtils.intToBigEndian(isColorFormat(format) ? 0x10 : 0x03, 2));
        
        // Write raw bytes instead of string to bypass ASCII encoding mapping issues (e.g. \u00ff -> 0x3F)
        if (isG3pOrG4pFormat(format)) {
            file.write(EncoderUtils.intToLittleEndian(1, 4));
        } else {
            file.write(new byte[]{0, (byte)0xFF, 0, (byte)0xFF, 0, (byte)0xFF, 0, (byte)0xFF, 0, 1, 0, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
        }

        file.write(compressedData);
        file.write(footer);

        return new ConversionResult(file.toByteArray(), baseName + "." + format.getFileExtension());
    }

    private byte[] buildHeader1(byte[] header1, int size, byte[] sizeBytes, Format format) throws IOException {
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        temp.write(header1);
        if (isC2pFormat(format)) {
            temp.write(EncoderUtils.intToBigEndian(size, 3));
            temp.write((0x1D1 - (size & 0xFF)) & 0xFF);
        } else {
            temp.write((sizeBytes[3] + 0x41) % 0x100);
            temp.write(1);
            temp.write(sizeBytes);
            temp.write((sizeBytes[3] + 0xB8) % 0x100);
        }
        return temp.toByteArray();
    }

    private String getHeader2String(Format format) {
        return switch (format) {
            case CP_G3P, CP_I_G3P -> "CP\0\1";
            case CP01_G4P, CP01_I_G4P -> "CP0100Cy875";
            case CP01_G3P, CP01_I_G3P -> "CP0100Ly755";
            default -> "CC0100ColorCP";
        };
    }

    private byte[] buildFooter(Format format) throws IOException {
        ByteArrayOutputStream footer = new ByteArrayOutputStream();
        if (format == Format.CP01_G3P || format == Format.CP01_I_G3P || format == Format.CP01_G4P || format == Format.CP01_I_G4P) {
            footer.write(EncoderUtils.padRight("0100".getBytes(StandardCharsets.US_ASCII), 7));
            footer.write(EncoderUtils.intToLittleEndian(0x30066084, 13));
            footer.write(EncoderUtils.intToLittleEndian(0x300610, 12));
            footer.write(EncoderUtils.intToLittleEndian(0x0110, 12));
            footer.write(EncoderUtils.intToLittleEndian(0x33338309, 12));
            footer.write(EncoderUtils.intToLittleEndian(0x100360, 12));
            footer.write(EncoderUtils.intToLittleEndian(0x100310, 12));
            footer.write(EncoderUtils.intToLittleEndian(0x0110, 24));
            footer.write(EncoderUtils.intToLittleEndian(0x31280610, 4));
            footer.write(EncoderUtils.intToLittleEndian(0x85, 8));
            footer.write(EncoderUtils.intToLittleEndian(0x32288609, 12));
            footer.write(EncoderUtils.intToLittleEndian(0x42778609, 12));
        } else if (format == Format.C2P || format == Format.I_C2P) {
            ByteArrayOutputStream footer2 = new ByteArrayOutputStream();
            for (int i = 0; i < 3; i++) {
                footer2.write(EncoderUtils.intToLittleEndian(3, 10));
                footer2.write(EncoderUtils.intToLittleEndian(0x60, 2));
                footer2.write(EncoderUtils.intToLittleEndian(3, 10));
                footer2.write(EncoderUtils.intToLittleEndian(0x10, 2));
                footer2.write(EncoderUtils.intToLittleEndian(1, 10));
                footer2.write(EncoderUtils.intToLittleEndian(0x10, 2));
                footer2.write(EncoderUtils.intToLittleEndian(0x5002, 10));
                footer2.write(EncoderUtils.intToLittleEndian(0x110, 2));
            }
            byte[] footer2Bytes = footer2.toByteArray();
            ByteArrayOutputStream temp = new ByteArrayOutputStream();
            temp.write("0100".getBytes(StandardCharsets.US_ASCII));
            temp.write(EncoderUtils.intToBigEndian(footer2Bytes.length, 4));
            temp.write(footer2Bytes);
            footer2Bytes = temp.toByteArray();

            ByteArrayOutputStream footer1 = new ByteArrayOutputStream();
            footer1.write(EncoderUtils.intToLittleEndian(0, 8));
            footer1.write(EncoderUtils.intToLittleEndian(0x10, 2));
            footer1.write(EncoderUtils.intToLittleEndian(1, 10));
            footer1.write(EncoderUtils.intToLittleEndian(0x10, 2));
            footer1.write(EncoderUtils.intToLittleEndian(5, 10));
            footer1.write(EncoderUtils.intToLittleEndian(0x9809, 2));
            byte[] footer1Bytes = footer1.toByteArray();

            temp = new ByteArrayOutputStream();
            temp.write("0100".getBytes(StandardCharsets.US_ASCII));
            temp.write(EncoderUtils.intToLittleEndian(0, 3));
            temp.write(EncoderUtils.intToLittleEndian(0x70078C, 11));
            temp.write(EncoderUtils.intToLittleEndian(0x60, 2));
            temp.write(EncoderUtils.intToLittleEndian(0x7007, 2));
            temp.write(footer1Bytes);
            temp.write(EncoderUtils.intToLittleEndian(0x6004, 10));
            temp.write(EncoderUtils.intToLittleEndian(0x60, 2));
            temp.write(EncoderUtils.intToLittleEndian(0x6004, 2));
            temp.write(footer1Bytes);
            temp.write(EncoderUtils.intToLittleEndian(0, 12));
            temp.write(EncoderUtils.intToLittleEndian(0x85312806, 10));
            temp.write(EncoderUtils.intToLittleEndian(0x10, 2));
            temp.write(EncoderUtils.intToLittleEndian(0x322806, 10));
            temp.write(EncoderUtils.intToLittleEndian(0x9809, 4));
            temp.write(new byte[]{-1, -1, -1, -1, -1, -1});
            footer1Bytes = temp.toByteArray();

            int pi = 0x93151403;
            footer.write(footer1Bytes);
            footer.write(footer2Bytes);
            footer.write(EncoderUtils.intToLittleEndian(2, 10));
            footer.write(EncoderUtils.intToLittleEndian(0x070110, 12));
            footer.write(EncoderUtils.intToLittleEndian(0x0110, 2));
            footer.write(EncoderUtils.intToLittleEndian(pi, 4));
            footer.write(EncoderUtils.intToLittleEndian(0, 6));
            footer.write(EncoderUtils.intToLittleEndian(0x60, 2));
            footer.write(EncoderUtils.intToLittleEndian(pi, 10));
            footer.write(EncoderUtils.intToLittleEndian(0x10, 2));
            footer.write(EncoderUtils.intToLittleEndian(pi, 10));
            footer.write(EncoderUtils.intToLittleEndian(0x60, 2));
            footer.write(EncoderUtils.intToLittleEndian(pi, 10));
            footer.write(EncoderUtils.intToLittleEndian(0x10, 2));
            footer.write(new byte[]{1, 1, 1});
            footer.write(new byte[]{-1, -1, -1, -1, -1});
        }
        return footer.toByteArray();
    }
}
