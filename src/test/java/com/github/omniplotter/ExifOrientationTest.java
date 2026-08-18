package com.github.omniplotter;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.util.Exif;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Orientation, on a JPEG assembled here rather than a fixture checked in.
 *
 * <p>Building the EXIF segment by hand is the point: it is the structure the parser walks, so a
 * test that writes one is testing the walk rather than one photograph that happened to work.
 */
class ExifOrientationTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 16;
    /** Large enough to survive chroma subsampling; a single marked pixel does not. */
    private static final int MARK = 8;

    /** A landscape image with a red block in its top-left corner and white everywhere else. */
    private static BufferedImage marked() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(Color.RED);
        g.fillRect(0, 0, MARK, MARK);
        g.dispose();
        return image;
    }

    /** A JPEG of {@code image} with an APP1 segment recording {@code orientation}. */
    private static byte[] jpegWithOrientation(BufferedImage image, int orientation) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);
        byte[] original = jpeg.toByteArray();

        byte[] app1 = {
            (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,                    // APP1, 34 bytes including this
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,            // big-endian TIFF, IFD0 at 8
            0x00, 0x01,                                              // one entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,          // orientation, SHORT, count 1
            (byte) 0x00, (byte) orientation, 0x00, 0x00,             // the value, then padding
            0x00, 0x00, 0x00, 0x00,                                  // no second IFD
        };

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(original, 0, 2);                                   // SOI
        out.write(app1);
        out.write(original, 2, original.length - 2);
        return out.toByteArray();
    }

    @Test
    void readsTheTag() throws IOException {
        assertEquals(6, Exif.orientation(jpegWithOrientation(marked(), 6)));
        assertEquals(3, Exif.orientation(jpegWithOrientation(marked(), 3)));
    }

    @Test
    void leavesAnImageWithoutOneAlone() throws IOException {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(marked(), "jpeg", plain);

        assertEquals(1, Exif.orientation(plain.toByteArray()));
        BufferedImage decoded = EngineApi.decode(plain.toByteArray(), "plain.jpg");
        assertEquals(WIDTH, decoded.getWidth());
        assertEquals(HEIGHT, decoded.getHeight());
    }

    @Test
    void turnsAQuarterTurnPhotographUpright() throws IOException {
        // Six is what a phone records when it was held on its side: the decoded image has to come
        // back with the axes exchanged, or every canvas decision after it is made on the wrong shape.
        BufferedImage decoded = EngineApi.decode(jpegWithOrientation(marked(), 6), "portrait.jpg");

        assertEquals(HEIGHT, decoded.getWidth());
        assertEquals(WIDTH, decoded.getHeight());
        // The corner that was top-left is now top-right.
        assertRed(decoded.getRGB(HEIGHT - MARK / 2, MARK / 2));
        assertNotEquals(decoded.getRGB(HEIGHT - MARK / 2, MARK / 2), decoded.getRGB(0, 0));
    }

    @Test
    void turnsAnUpsideDownPhotographBack() throws IOException {
        BufferedImage decoded = EngineApi.decode(jpegWithOrientation(marked(), 3), "flipped.jpg");

        assertEquals(WIDTH, decoded.getWidth());
        assertEquals(HEIGHT, decoded.getHeight());
        assertRed(decoded.getRGB(WIDTH - MARK / 2, HEIGHT - MARK / 2));
    }

    /** JPEG is lossy, so this asks for red dominance rather than an exact value. */
    private static void assertRed(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        org.junit.jupiter.api.Assertions.assertTrue(r > g + 60 && r > b + 60,
            "expected a red pixel, got r=" + r + " g=" + g + " b=" + b);
    }
}
