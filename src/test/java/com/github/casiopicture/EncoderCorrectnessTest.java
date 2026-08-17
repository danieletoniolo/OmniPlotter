package com.github.casiopicture;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.encoder.CasioPictureEncoder;
import com.github.casiopicture.engine.encoder.TIZ80Encoder;
import com.github.casiopicture.engine.encoder.ZeroEncoder;
import com.github.casiopicture.engine.util.EncoderUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EncoderCorrectnessTest {

    private static final int W = 12;
    private static final int H = 8;

    @BeforeAll
    public static void setupClass() throws IOException {
        Files.createDirectories(Paths.get("target"));
        
        // Dump real pal8ci palette to JSON
        List<Color> pal8ci = EncoderUtils.loadPalette("pal8ci.png");
        StringBuilder sb8ci = new StringBuilder("[");
        for (int i = 0; i < pal8ci.size(); i++) {
            Color c = pal8ci.get(i);
            sb8ci.append(String.format("%d,%d,%d,%d", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
            if (i < pal8ci.size() - 1) sb8ci.append(",");
        }
        sb8ci.append("]");
        Files.writeString(Paths.get("target/pal8ci.json"), sb8ci.toString());

        // Dump real palcp palette to JSON
        List<Color> palcp = EncoderUtils.loadPalette("palcp.png");
        StringBuilder sbcp = new StringBuilder("[");
        for (int i = 0; i < palcp.size(); i++) {
            Color c = palcp.get(i);
            sbcp.append(String.format("%d,%d,%d,%d", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()));
            if (i < palcp.size() - 1) sbcp.append(",");
        }
        sbcp.append("]");
        Files.writeString(Paths.get("target/palcp.json"), sbcp.toString());
    }

    private BufferedImage loadMockImage() throws IOException {
        byte[] rawBytes = Files.readAllBytes(Paths.get("src/test/resources/ref/mock_pixels.bin"));
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int idx = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int r = rawBytes[idx++] & 0xFF;
                int g = rawBytes[idx++] & 0xFF;
                int b = rawBytes[idx++] & 0xFF;
                int a = rawBytes[idx++] & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private void compareBytes(byte[] ref, byte[] java, String formatName) {
        if (ref.length != java.length) {
            System.err.printf("Length mismatch for %s: Reference has %d, Java has %d\n", formatName, ref.length, java.length);
        }
        int minLen = Math.min(ref.length, java.length);
        int diffs = 0;
        for (int i = 0; i < minLen; i++) {
            if (ref[i] != java[i]) {
                if (diffs < 10) {
                    System.err.printf("Diff at offset 0x%X (%d): Ref=0x%02X, Java=0x%02X\n", i, i, ref[i] & 0xFF, java[i] & 0xFF);
                }
                diffs++;
            }
        }
        if (diffs > 0) {
            System.err.printf("Total byte differences for %s: %d\n", formatName, diffs);
        }
        assertEquals(ref.length, java.length, "Lengths should match for " + formatName);
        assertArrayEquals(ref, java, "Bytes should match for " + formatName);
    }

    @Test
    public void test8ca() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_8ca.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 65536, false, false);
        ConversionResult res = TIZ80Encoder.getInstance().encode(image, Format.TI_8CA, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "8ca");
    }

    @Test
    public void test8ci() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_8ci.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 16, false, false);
        ConversionResult res = TIZ80Encoder.getInstance().encode(image, Format.TI_8CI, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "8ci");
    }

    @Test
    public void testIm8c() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_im8c.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 256, false, false);
        ConversionResult res = TIZ80Encoder.getInstance().encode(image, Format.TI_8XV, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "im8c");
    }

    @Test
    public void test8xi() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_8xi.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 2, false, false);
        ConversionResult res = TIZ80Encoder.getInstance().encode(image, Format.TI_8XI, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "8xi");
    }

    @Test
    public void testG3p() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_g3p.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 65536, false, false);
        ConversionResult res = CasioPictureEncoder.getInstance().encode(image, Format.CP_G3P, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "g3p");
    }

    @Test
    public void testCp01G3p() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_cp01_g3p.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 65536, false, false);
        ConversionResult res = CasioPictureEncoder.getInstance().encode(image, Format.CP01_G3P, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "cp01_g3p");
    }

    @Test
    public void testC2p() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_c2p.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 65536, false, false);
        ConversionResult res = CasioPictureEncoder.getInstance().encode(image, Format.C2P, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "c2p");
    }

    @Test
    public void testZpic() throws Exception {
        BufferedImage image = loadMockImage();
        byte[] ref = Files.readAllBytes(Paths.get("src/test/resources/ref/ref_zpic.bin"));

        ConversionOptions options = new ConversionOptions(W, H, 2, false, false);
        ConversionResult res = ZeroEncoder.getInstance().encode(image, Format.ZERO_BIN, "TESTVAR.png", options);

        compareBytes(ref, res.fileBytes(), "zpic");
    }
}
