package com.github.casiopicture.engine.encoder;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.util.EncoderUtils;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TIZ80Encoder implements FileEncoder {

    private TIZ80Encoder() {}

    private static final class Holder {
        static final TIZ80Encoder INSTANCE = new TIZ80Encoder();
    }

    public static TIZ80Encoder getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public ConversionResult encode(BufferedImage image, Format format, String originalFileName, ConversionOptions options) throws IOException {
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String varName = baseName.substring(0, Math.min(baseName.length(), 8));

        byte[] encodedData;
        String calcName;

        switch (format) {
            case TI_8XV -> {
                var name = varName.substring(0, 1).toUpperCase() + varName.substring(1);
                if (name.length() > 8) name = name.substring(0, 8);
                calcName = name;
                encodedData = encodeIm8c(image, format, calcName);
            }
            case TI_8CA -> {
                calcName = "Image1"; // default oncalc image number
                encodedData = encode8ca(image, format, calcName);
            }
            case TI_8CI -> {
                calcName = "Pic1"; // default oncalc picture number
                encodedData = encode8ci(image, format, calcName);
            }
            case TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> {
                calcName = "Pic1"; // default oncalc picture number
                encodedData = encodeMonochrome(image, format, calcName);
            }
            default -> throw new IllegalArgumentException("Unsupported format for TIZ80Encoder: " + format);
        }

        String extension = format == Format.TI_8XV ? ".8xv" : ".8xi";
        return new ConversionResult(encodedData, calcName + extension);
    }

    private byte[] encodeIm8c(BufferedImage image, Format format, String calcName) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        List<Color> palette = new ArrayList<>();
        int ialpha = -1;
        
        // 17-bit packed colors: alpha << 16 | red << 11 | green << 5 | blue
        List<Integer> packedPixels = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // getPixel_RGBA_int(..., r=5, g=6, b=5, a=1)
                int r5 = r >> 3;
                int g6 = g >> 2;
                int b5 = b >> 3;
                int a1 = a >= 255 ? 1 : 0;
                int packed = b5 | (g6 << 5) | (r5 << 11) | (a1 << 16);

                packedPixels.add(packed);

                if (!palette.contains(new Color(r, g, b, a))) {
                    Color c = new Color(r, g, b, a);
                    palette.add(c);
                }
            }
        }

        // Get unique palette colors mapped by packed integer representation
        List<Integer> palettePacked = new ArrayList<>();
        for (int i = 0; i < packedPixels.size(); i++) {
            int packed = packedPixels.get(i);
            if (!palettePacked.contains(packed)) {
                int icolor = palettePacked.size();
                palettePacked.add(packed);
                if (ialpha < 0 && (packed >> 16 == 0)) {
                    ialpha = icolor;
                }
            }
        }

        byte[] paletteHeader = new byte[4];
        if (ialpha < 0) {
            paletteHeader[0] = 1;
            paletteHeader[1] = 0;
            paletteHeader[2] = 0;
        } else {
            paletteHeader[0] = 1;
            paletteHeader[1] = 1;
            paletteHeader[2] = (byte) (ialpha & 0xFF);
        }
        paletteHeader[3] = (byte) (palettePacked.size() & 0xFF);

        ByteArrayOutputStream paletteRGB = new ByteArrayOutputStream();
        for (int packed : palettePacked) {
            int color16 = packed & 0xFFFF;
            paletteRGB.write(color16 & 0xFF);
            paletteRGB.write((color16 >> 8) & 0xFF);
        }

        ByteArrayOutputStream rleData = new ByteArrayOutputStream();
        List<Byte> pixBuffer = new ArrayList<>();

        for (int i = 0; i < height * width; i++) {
            int curcolor = packedPixels.get(i);
            int icolor = palettePacked.indexOf(curcolor);
            int ncurcolor = 1;
            while (ncurcolor < 128 && i < height * width - 1 && curcolor == packedPixels.get(i + 1)) {
                ncurcolor++;
                i++;
            }
            if (ncurcolor > 1) {
                while (!pixBuffer.isEmpty()) {
                    int n = Math.min(pixBuffer.size(), 128);
                    rleData.write(n - 1);
                    for (int j = 0; j < n; j++) {
                        rleData.write(pixBuffer.get(j));
                    }
                    pixBuffer = pixBuffer.subList(n, pixBuffer.size());
                }
                rleData.write(0x80 + ncurcolor - 2);
                rleData.write(icolor);
            } else {
                pixBuffer.add((byte) icolor);
            }
        }
        // Note: JS logic leaves remaining pixBuffer unwritten (so we discard it to match Ref)

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write("IM8C".getBytes(StandardCharsets.US_ASCII));
        payload.write(EncoderUtils.intToLittleEndian(width, 3));
        payload.write(EncoderUtils.intToLittleEndian(height, 3));
        payload.write(paletteHeader);
        payload.write(paletteRGB.toByteArray());
        payload.write(rleData.toByteArray());

        return buildTiFile(payload.toByteArray(), format, calcName);
    }

    private byte[] encode8ca(BufferedImage image, Format format, String calcName) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        
        payload.write(0x81);

        // JS loops bottom-up (Y is inverted)
        for (int i = 0; i < height * width; i++) {
            int y = height - (i / width) - 1;
            int x = i % width;
            int argb = image.getRGB(x, y);
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            // getPixel_RGBA_int(..., r=5, g=6, b=5, a=0)
            int r5 = r >> 3;
            int g6 = g >> 2;
            int b5 = b >> 3;
            int packed = b5 | (g6 << 5) | (r5 << 11);
            
            payload.write(packed & 0xFF);
            payload.write((packed >> 8) & 0xFF);
        }

        return buildTiFile(payload.toByteArray(), format, calcName);
    }

    private byte[] encode8ci(BufferedImage image, Format format, String calcName) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        List<Color> palette = EncoderUtils.loadPalette("pal8ci.png");
        ByteArrayOutputStream payload = new ByteArrayOutputStream();

        // Load 16 colors from palette image
        List<int[]> paletteRGBA = new ArrayList<>();
        for (Color c : palette) {
            paletteRGBA.add(new int[]{c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()});
        }

        // Extract raw RGBA bytes from buffered image
        byte[] imgRgba = new byte[width * height * 4];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                imgRgba[idx++] = (byte) ((argb >> 16) & 0xFF); // R
                imgRgba[idx++] = (byte) ((argb >> 8) & 0xFF);  // G
                imgRgba[idx++] = (byte) (argb & 0xFF);         // B
                imgRgba[idx++] = (byte) ((argb >> 24) & 0xFF); // A
            }
        }

        for (int i = 0; i < height * width; i += 2) {
            int icolor = 0;
            for (int j = 0; j < 2; j++) {
                int pixelIdx = (i + j) * 4;
                int[] color = new int[]{
                    imgRgba[pixelIdx] & 0xFF,
                    imgRgba[pixelIdx + 1] & 0xFF,
                    imgRgba[pixelIdx + 2] & 0xFF,
                    imgRgba[pixelIdx + 3] & 0xFF
                };
                icolor = (icolor << 4) | nearestiRGB(color, paletteRGBA);
            }
            payload.write(icolor);
        }

        return buildTiFile(payload.toByteArray(), format, calcName);
    }

    private byte[] encodeMonochrome(BufferedImage image, Format format, String calcName) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        ByteArrayOutputStream payload = new ByteArrayOutputStream();

        // Extract raw RGBA bytes from buffered image
        byte[] imgRgba = new byte[width * height * 4];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                imgRgba[idx++] = (byte) ((argb >> 16) & 0xFF);
                imgRgba[idx++] = (byte) ((argb >> 8) & 0xFF);
                imgRgba[idx++] = (byte) (argb & 0xFF);
                imgRgba[idx++] = (byte) ((argb >> 24) & 0xFF);
            }
        }

        for (int i = 0; i < height * width; i += 8) {
            int icolor = 0;
            for (int j = 0; j < 8; j++) {
                int pixelIdx = (i + j) * 4;
                // getPixel_RGBA_int(img_a, i+j, 0, 0, 0, 1) -> Alpha bit
                int a = imgRgba[pixelIdx + 3] & 0xFF;
                int bit = a >= 255 ? 1 : 0;
                icolor = (icolor << 1) | bit;
            }
            payload.write(icolor);
        }

        return buildTiFile(payload.toByteArray(), format, calcName);
    }

    private int nearestiRGB(int[] color, List<int[]> palette) {
        int min = 4 * 255 + 1;
        int imin = -1;
        for (int i = 0; i < palette.size(); i++) {
            int[] palColor = palette.get(i);
            int v = Math.abs(palColor[0] - color[0]) + Math.abs(palColor[1] - color[1]) + Math.abs(palColor[2] - color[2]) + Math.abs(palColor[3] - color[3]);
            if (v < min) {
                imin = i;
                min = v;
            }
        }
        return imin;
    }

    private byte[] buildTiFile(byte[] payload, Format format, String calcName) throws IOException {
        String formatStr = format.toString();

        ByteArrayOutputStream varData = new ByteArrayOutputStream();
        // size2 prefix = payload.length
        varData.write(EncoderUtils.intToLittleEndian(payload.length, 2));
        varData.write(payload);

        byte[] varDataBytes = varData.toByteArray();
        int size2 = varDataBytes.length;

        ByteArrayOutputStream dataSection = new ByteArrayOutputStream();
        dataSection.write(EncoderUtils.intToLittleEndian(size2, 2));
        dataSection.write(varDataBytes);

        // Prepend custom values
        boolean isIm8c = formatStr.equals("im8c.8xv");
        boolean is8ca = formatStr.equals("8ca");
        boolean is8ci = formatStr.equals("8ci");
        boolean is8xi = formatStr.equals("8xi");
        
        byte[] prependedData = dataSection.toByteArray();

        if (isIm8c || is8ca || is8ci || is8xi) {
            byte byte1 = (byte) ((is8ca || is8ci) ? 0x0A : 0x00);
            byte byte2 = (byte) (is8xi ? 0x00 : 0x80);
            
            ByteArrayOutputStream temp = new ByteArrayOutputStream();
            temp.write(byte1);
            temp.write(byte2);
            temp.write(prependedData);
            prependedData = temp.toByteArray();
        }

        // Variable Name Padding
        String name = calcName;
        int num = 1; // default
        if (formatStr.equals("im8c.8xv") || formatStr.equals("86i") || formatStr.equals("85i")) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
            if (name.length() > 8) name = name.substring(0, 8);
        } else {
            if (num == 0) num = 10;
            name = "" + (char) (is8ca ? 0x3C : 0x60) + (char) (num - (formatStr.equals("73i") ? 0 : 1));
        }

        byte paddingChar = (byte) ((formatStr.equals("85i") || formatStr.equals("86i")) ? 0x20 : 0x00);
        byte[] nameArray = EncoderUtils.stringToPaddedASCII(name, 8);
        for (int i = name.length(); i < 8; i++) {
            nameArray[i] = paddingChar;
        }

        ByteArrayOutputStream withName = new ByteArrayOutputStream();
        withName.write(nameArray);
        withName.write(prependedData);
        prependedData = withName.toByteArray();

        if (formatStr.equals("85i") || formatStr.equals("86i")) {
            ByteArrayOutputStream temp = new ByteArrayOutputStream();
            temp.write(calcName.length());
            temp.write(prependedData);
            prependedData = temp.toByteArray();
        }

        // Prepend Size2 + Type
        byte typeByte = (byte) (isIm8c ? 0x15 : is8ca ? 0x1A : (formatStr.equals("86i") || formatStr.equals("85i")) ? 0x11 : 0x07);
        ByteArrayOutputStream withType = new ByteArrayOutputStream();
        withType.write(EncoderUtils.intToLittleEndian(size2, 2));
        withType.write(typeByte);
        withType.write(prependedData);
        prependedData = withType.toByteArray();

        // Prepend Header size
        int headerSize = prependedData.length - size2 - 2;
        ByteArrayOutputStream withHeaderSize = new ByteArrayOutputStream();
        withHeaderSize.write(EncoderUtils.intToLittleEndian(headerSize, 2));
        withHeaderSize.write(prependedData);
        byte[] finalVarSection = withHeaderSize.toByteArray();

        // Calculate Header Signature
        String headerStr = (formatStr.equals("82i")) ? "**TI82**"
                         : (formatStr.equals("85i")) ? "**TI85**"
                         : (formatStr.equals("86i")) ? "**TI86**"
                         : (formatStr.equals("73i")) ? "**TI73**"
                         : (formatStr.equals("83i")) ? "**TI83**"
                         : "**TI83F*";

        byte sigByte1 = 0x1A;
        byte sigByte2 = (byte) (formatStr.equals("85i") ? 0x0C : 0x0A);
        byte sigByte3 = (byte) (isIm8c ? 0x0A : is8ci ? 0x0F : is8xi ? 0x0B : 0x00);

        byte[] commentBytes = EncoderUtils.stringToPaddedASCII("Created on TI-Planet.org by img2calc", 42);

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(headerStr.getBytes(StandardCharsets.US_ASCII));
        header.write(sigByte1);
        header.write(sigByte2);
        header.write(sigByte3);
        header.write(commentBytes);
        header.write(EncoderUtils.intToLittleEndian(finalVarSection.length, 2));
        header.write(finalVarSection);

        byte[] fileBytesWithoutChecksum = header.toByteArray();
        
        // Sum from finalVarSection starting for checksum
        int checksum = EncoderUtils.calculateTIChecksum(finalVarSection);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(fileBytesWithoutChecksum);
        file.write(EncoderUtils.intToLittleEndian(checksum, 2));

        return file.toByteArray();
    }
}
