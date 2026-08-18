package com.github.omniplotter.engine.encoder;

import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.OnCalcName;
import com.github.omniplotter.engine.util.ByteSeq;
import com.github.omniplotter.engine.util.EncoderUtils;
import com.github.omniplotter.engine.util.Palette;
import com.github.omniplotter.engine.util.PixelBuffer;

import java.awt.image.BufferedImage;
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
        // im8c/85i/86i carry a user-chosen variable name; the other formats are slot-numbered
        // (Pic1..Pic9 / Image1..Image9) and their on-calc name is derived from that number. Both
        // come from the request: the slot in particular is written into the file, so ignoring it
        // would silently install the picture in the wrong place.
        int num = options.onCalcNumber();
        String requested = options.onCalcName() == null || options.onCalcName().isBlank()
            ? OnCalcName.suggestFrom(format, originalFileName)
            : options.onCalcName();

        byte[] encodedData;
        String calcName = switch (format) {
            case TI_8XV, TI_85I, TI_86I -> requested;
            case TI_8CA -> "Image" + num;
            default -> "Pic" + num;
        };

        encodedData = switch (format) {
            case TI_8XV -> encodeIm8c(image, format, calcName, num);
            case TI_8CA -> encode8ca(image, format, calcName, num);
            case TI_8CI -> encode8ci(image, format, calcName, num);
            case TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> encodeMonochrome(image, format, calcName, num);
            default -> throw new IllegalArgumentException("Unsupported format for TIZ80Encoder: " + format);
        };

        String fileName = calcName + "." + format.getFileExtension();
        if (format == Format.TI_8XV) {
            // The variable on its own does not draw anything, so the reference also hands over the
            // two lines that put it on screen. Upstream now calls this format im8c_rle.8xv and has
            // added .8xv2 variants for the TI-84 Evo, which this port does not cover yet.
            String sample = "from ti_graphics import drawImage\n"
                + "from ti_system import disp_wait\n\n"
                + "drawImage(\"" + calcName + "\", 0, 30)\n"
                + "disp_wait()";
            return new ConversionResult(encodedData, fileName, List.of(
                new ConversionResult.OutputFile(calcName + ".py",
                    sample.getBytes(StandardCharsets.US_ASCII))));
        }
        return new ConversionResult(encodedData, fileName);
    }

    private byte[] encodeIm8c(BufferedImage image, Format format, String calcName, int num) throws IOException {
        PixelBuffer px = PixelBuffer.of(image);
        int ialpha = -1;

        // 17-bit packed colours: alpha << 16 | red << 11 | green << 5 | blue.
        List<Integer> packedPixels = new ArrayList<>(px.size());
        for (int i = 0; i < px.size(); i++) {
            packedPixels.add(px.packed(i, 5, 6, 5, 1));
        }

        // Palette entries in first-seen order; the first fully transparent one becomes the
        // transparent index recorded in the header.
        List<Integer> palettePacked = new ArrayList<>();
        for (int packed : packedPixels) {
            if (!palettePacked.contains(packed)) {
                int icolor = palettePacked.size();
                palettePacked.add(packed);
                if (ialpha < 0 && (packed >> 16) == 0) {
                    ialpha = icolor;
                }
            }
        }

        ByteSeq paletteHeader = ialpha < 0
            ? ByteSeq.of(1, 0, 0, palettePacked.size() & 0xFF)
            : ByteSeq.of(1, 1, ialpha & 0xFF, palettePacked.size() & 0xFF);

        ByteSeq paletteRGB = new ByteSeq();
        for (int packed : palettePacked) {
            int color16 = packed & 0xFFFF;
            paletteRGB.add(color16 & 0xFF, color16 >> 8);
        }

        // RLE: runs of two or more become a marked pair, single pixels accumulate in a literal
        // buffer that is flushed in blocks of at most 128 when the next run starts.
        //
        // Palette indices are written untruncated. Above 256 colours they exceed a byte, and the
        // file keeps only the low bits while the checksum counts the whole value — see ByteSeq.
        ByteSeq rleData = new ByteSeq();
        List<Integer> pixBuffer = new ArrayList<>();

        for (int i = 0; i < px.size(); i++) {
            int curcolor = packedPixels.get(i);
            int icolor = palettePacked.indexOf(curcolor);
            int ncurcolor = 1;
            while (ncurcolor < 128 && i < px.size() - 1 && curcolor == packedPixels.get(i + 1)) {
                ncurcolor++;
                i++;
            }
            if (ncurcolor > 1) {
                while (!pixBuffer.isEmpty()) {
                    int n = Math.min(pixBuffer.size(), 128);
                    rleData.add(n - 1);
                    for (int j = 0; j < n; j++) {
                        rleData.add(pixBuffer.get(j));
                    }
                    pixBuffer = pixBuffer.subList(n, pixBuffer.size());
                }
                rleData.add(0x80 + ncurcolor - 2, icolor);
            } else {
                pixBuffer.add(icolor);
            }
        }
        // Literal pixels still buffered when the image ends are dropped, because the reference drops
        // them. Matching img2calc byte for byte is the goal here, not improving on it.

        ByteSeq payload = new ByteSeq()
            .add("IM8C".getBytes(StandardCharsets.US_ASCII))
            .add(EncoderUtils.intToLittleEndian(px.width(), 3))
            .add(EncoderUtils.intToLittleEndian(px.height(), 3))
            .add(paletteHeader)
            .add(paletteRGB)
            .add(rleData);

        return buildTiFile(payload, format, calcName, num);
    }

    private byte[] encode8ca(BufferedImage image, Format format, String calcName, int num) throws IOException {
        PixelBuffer px = PixelBuffer.of(image);
        ByteSeq payload = new ByteSeq().add(0x81);

        // Rows are emitted bottom-up: the calculator stores this format with the origin at the
        // bottom-left.
        for (int i = 0; i < px.size(); i++) {
            int source = px.width() * (px.height() - (i / px.width()) - 1) + (i % px.width());
            int color = px.packed(source, 5, 6, 5, 0);
            payload.add(color & 0xFF, color >> 8);
        }

        return buildTiFile(payload, format, calcName, num);
    }

    private byte[] encode8ci(BufferedImage image, Format format, String calcName, int num) throws IOException {
        PixelBuffer px = PixelBuffer.of(image);
        Palette palette = Palette.load("pal8ci.png");
        ByteSeq payload = new ByteSeq();

        // Two pixels per byte over a flat run; see CasioPictureEncoder#extractIndexedPixels for why
        // the tail past the last pixel is meaningful. On an odd pixel count the last value is -1,
        // which the file stores as 0xFF but the checksum counts as -1 — see ByteSeq.
        for (int i = 0; i < px.size(); i += 2) {
            int icolor = 0;
            for (int j = 0; j < 2; j++) {
                icolor = (icolor << 4) | palette.nearest(px.raw(i + j));
            }
            payload.add(icolor);
        }

        return buildTiFile(payload, format, calcName, num);
    }

    private byte[] encodeMonochrome(BufferedImage image, Format format, String calcName, int num) throws IOException {
        PixelBuffer px = PixelBuffer.of(image);
        ByteSeq payload = new ByteSeq();

        // One bit per pixel, taken from alpha alone: by this point the preprocessing has turned the
        // image into opaque-black-on-transparent, so alpha carries the ink. Bit set when alpha >=
        // 128, and pixels past the end of the last partial byte read as 0.
        for (int i = 0; i < px.size(); i += 8) {
            int icolor = 0;
            for (int j = 0; j < 8; j++) {
                icolor = (icolor << 1) | px.packed(i + j, 0, 0, 0, 1);
            }
            payload.add(icolor);
        }

        return buildTiFile(payload, format, calcName, num);
    }

    /**
     * Wraps an encoded payload in the TI-Z80 variable-transfer container.
     *
     * <p>Built outward from the payload, mirroring the reference's series of prepends: the variable
     * data gets a length prefix, then a second one, then optional type bytes, the on-calc name, the
     * type marker, the header size, and finally the file signature and checksum.
     */
    private byte[] buildTiFile(ByteSeq payload, Format format, String calcName, int num) {
        String formatStr = format.toString();
        boolean isIm8c = formatStr.equals("im8c.8xv");
        boolean is8ca = formatStr.equals("8ca");
        boolean is8ci = formatStr.equals("8ci");
        boolean is8xi = formatStr.equals("8xi");
        boolean isNamed = isIm8c || formatStr.equals("85i") || formatStr.equals("86i");
        boolean is8586 = formatStr.equals("85i") || formatStr.equals("86i");

        ByteSeq varData = new ByteSeq()
            .add(EncoderUtils.intToLittleEndian(payload.size(), 2))
            .add(payload);
        int size2 = varData.size();

        ByteSeq body = new ByteSeq()
            .add(EncoderUtils.intToLittleEndian(size2, 2))
            .add(varData);

        if (isIm8c || is8ca || is8ci || is8xi) {
            body = new ByteSeq()
                .add((is8ca || is8ci) ? 0x0A : 0x00, is8xi ? 0x00 : 0x80)
                .add(body);
        }

        // The on-file variable name. Named formats reuse calcName; slot formats encode the slot as
        // two bytes, a type marker plus the zero-based slot index.
        String name;
        if (isNamed) {
            name = calcName;
        } else {
            int slot = num == 0 ? 10 : num;
            name = "" + (char) (is8ca ? 0x3C : 0x60) + (char) (slot - (formatStr.equals("73i") ? 0 : 1));
        }
        body = new ByteSeq().addPadded(name, 8, is8586 ? 0x20 : 0x00).add(body);

        if (is8586) {
            body = new ByteSeq().add(calcName.length()).add(body);
        }

        int typeByte = isIm8c ? 0x15 : is8ca ? 0x1A : is8586 ? 0x11 : 0x07;
        body = new ByteSeq()
            .add(EncoderUtils.intToLittleEndian(size2, 2))
            .add(typeByte)
            .add(body);

        ByteSeq varSection = new ByteSeq()
            .add(EncoderUtils.intToLittleEndian(body.size() - size2 - 2, 2))
            .add(body);

        String signature = formatStr.equals("82i") ? "**TI82**"
                         : formatStr.equals("85i") ? "**TI85**"
                         : formatStr.equals("86i") ? "**TI86**"
                         : formatStr.equals("73i") ? "**TI73**"
                         : formatStr.equals("83i") ? "**TI83**"
                         : "**TI83F*";

        ByteSeq file = new ByteSeq()
            .add(signature.getBytes(StandardCharsets.US_ASCII))
            .add(0x1A,
                 formatStr.equals("85i") ? 0x0C : 0x0A,
                 isIm8c ? 0x0A : is8ci ? 0x0F : is8xi ? 0x0B : 0x00)
            .addPadded("Created on TI-Planet.org by img2calc", 42, 0x00)
            .add(EncoderUtils.intToLittleEndian(varSection.size(), 2))
            .add(varSection)
            // Summed untruncated, so a payload holding -1 or an index above 255 checksums
            // differently from the bytes actually written. See ByteSeq.
            .add(EncoderUtils.intToLittleEndian(varSection.rawSum(), 2));

        return file.toBytes();
    }
}
