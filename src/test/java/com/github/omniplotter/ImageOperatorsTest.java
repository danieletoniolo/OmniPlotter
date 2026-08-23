package com.github.omniplotter;

import com.github.omniplotter.engine.converter.ImageOps;
import com.github.omniplotter.engine.data.Adjustments;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operators the reference does not have.
 *
 * <p>Two things matter about each of them: that neutral settings are not merely equivalent to doing
 * nothing but return the image untouched, and that the direction of the effect is the one the name
 * promises. Exact values are not asserted — they are a judgement about what looks right, and a test
 * that pins them down would only have to be rewritten the first time that judgement improves.
 */
class ImageOperatorsTest {

    private static BufferedImage flat(int rgb, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static int red(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >> 16) & 0xFF;
    }

    @Test
    void neutralAdjustmentsReturnTheSameImage() {
        // Identity by reference, not by value: the default path has to stay the reference path
        // rather than a copy of it that happens to agree.
        BufferedImage source = flat(0xFF808080, 4, 4);

        assertSame(source, ImageOps.adjust(source, Adjustments.NONE));
        assertSame(source, ImageOps.sharpen(source, 0));
    }

    @Test
    void brightnessMovesTowardsBlackAndWhite() {
        BufferedImage grey = flat(0xFF808080, 2, 2);

        assertTrue(red(ImageOps.adjust(grey, Adjustments.NONE.withBrightness(40)), 0, 0) > 128);
        assertTrue(red(ImageOps.adjust(grey, Adjustments.NONE.withBrightness(-40)), 0, 0) < 128);
        assertEquals(255, red(ImageOps.adjust(grey, Adjustments.NONE.withBrightness(100)), 0, 0));
        assertEquals(0, red(ImageOps.adjust(grey, Adjustments.NONE.withBrightness(-100)), 0, 0));
    }

    @Test
    void contrastPushesAwayFromMidGrey() {
        BufferedImage dark = flat(0xFF404040, 2, 2);
        BufferedImage light = flat(0xFFC0C0C0, 2, 2);
        Adjustments harder = Adjustments.NONE.withContrast(50);

        assertTrue(red(ImageOps.adjust(dark, harder), 0, 0) < 0x40);
        assertTrue(red(ImageOps.adjust(light, harder), 0, 0) > 0xC0);
        // And the other way, which is what makes it a slider rather than a button.
        assertTrue(red(ImageOps.adjust(dark, Adjustments.NONE.withContrast(-50)), 0, 0) > 0x40);
    }

    @Test
    void gammaAboveOneLightensTheMidtones() {
        // ImageMagick's convention, which this project follows everywhere else too.
        BufferedImage grey = flat(0xFF808080, 2, 2);

        assertTrue(red(ImageOps.adjust(grey, Adjustments.NONE.withGamma(2.0)), 0, 0) > 128);
        assertTrue(red(ImageOps.adjust(grey, Adjustments.NONE.withGamma(0.5)), 0, 0) < 128);
    }

    @Test
    void fullDesaturationLeavesGrey() {
        BufferedImage red = flat(0xFFFF0000, 2, 2);

        int grey = ImageOps.adjust(red, Adjustments.NONE.withSaturation(-100)).getRGB(0, 0);
        int r = (grey >> 16) & 0xFF;
        int g = (grey >> 8) & 0xFF;
        int b = grey & 0xFF;

        assertEquals(r, g);
        assertEquals(g, b);
        // Rec.709 luma, so pure red lands low rather than at a third of full.
        assertTrue(r > 40 && r < 70, "expected the luma of red, got " + r);
    }

    @Test
    void adjustmentsLeaveAlphaAlone() {
        BufferedImage translucent = flat(0x80804020, 2, 2);

        int adjusted = ImageOps.adjust(translucent, Adjustments.NONE.withContrast(80).withBrightness(30))
            .getRGB(0, 0);

        assertEquals(0x80, (adjusted >>> 24) & 0xFF);
    }

    @Test
    void sharpeningAFlatImageChangesNothing() {
        // The blur of a constant is that constant, so the mask it subtracts is zero everywhere.
        BufferedImage grey = flat(0xFF808080, 8, 8);

        BufferedImage sharpened = ImageOps.sharpen(grey, 2.0);

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(grey.getRGB(x, y), sharpened.getRGB(x, y), "at " + x + "," + y);
            }
        }
    }

    @Test
    void sharpeningStepsUpAnEdge() {
        // A soft step, so the overshoot has somewhere to go without clipping.
        BufferedImage step = flat(0xFF646464, 8, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 4; x < 8; x++) {
                step.setRGB(x, y, 0xFF9B9B9B);
            }
        }

        BufferedImage sharpened = ImageOps.sharpen(step, 1.5);

        assertTrue(red(sharpened, 3, 2) < 0x64, "the dark side of the edge should darken");
        assertTrue(red(sharpened, 4, 2) > 0x9B, "the light side of the edge should lighten");
        // Away from the edge nothing should have moved.
        assertEquals(0x64, red(sharpened, 0, 2));
        assertEquals(0x9B, red(sharpened, 7, 2));
    }

    @Test
    void cropCopiesRatherThanSharingTheRaster() {
        BufferedImage source = flat(0xFF102030, 10, 10);
        source.setRGB(3, 4, 0xFFAABBCC);

        BufferedImage cropped = ImageOps.crop(source, 2, 3, 4, 5);

        assertEquals(4, cropped.getWidth());
        assertEquals(5, cropped.getHeight());
        assertEquals(0xFFAABBCC, cropped.getRGB(1, 1));

        // Writing into the crop must not reach back into the image the queue keeps and reuses.
        cropped.setRGB(0, 0, 0xFF000000);
        assertEquals(0xFF102030, source.getRGB(2, 3));
    }
}
