package com.github.omniplotter;

import com.github.omniplotter.engine.converter.ImagePreprocessor;
import com.github.omniplotter.engine.data.Adjustments;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.Crop;
import com.github.omniplotter.engine.data.Dither;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.Framing;
import com.github.omniplotter.engine.data.Mode;
import com.github.omniplotter.engine.data.Target;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The new controls where they meet the pipeline.
 *
 * <p>Two questions. Does a setting left alone leave the conversion exactly as it was — which is
 * what {@code PreprocessingParityTest} and {@code ReferenceVectorTest} are still measuring the
 * reference against, and which stops being true the moment a default drifts. And does a setting
 * turned on actually reach the pixels.
 */
class PipelineControlsTest {

    private static BufferedImage filled(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    /** A source with enough going on that dithering and tone have something to act on. */
    private static BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF000000 | (x * 255 / width) << 16
                    | (y * 255 / height) << 8 | ((x + y) * 255 / (width + height)));
            }
        }
        return image;
    }

    private static boolean same(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ConversionOptions defaults() {
        return ConversionOptions.defaults(Target.CASIO_CG, Format.CP_G3P);
    }

    /**
     * For every format, AUTO has to mean exactly what the reference passes for it.
     *
     * <p>Asserted by elimination rather than by reading the table back: AUTO must be
     * indistinguishable from ON or from OFF, and never from neither. That tests the resolution
     * without a test that is a copy of the thing it is testing.
     */
    @TestFactory
    List<DynamicTest> autoDitherMatchesOneOfTheTwo() {
        BufferedImage source = gradient(64, 48);
        List<DynamicTest> tests = new ArrayList<>();

        for (Format format : Format.values()) {
            tests.add(DynamicTest.dynamicTest(format.id(), () -> {
                Mode mode = format.isScript() ? Mode.SCRIPT : Mode.VAR;
                Target target = Target.supporting(mode, format).get(0);
                ConversionOptions options = ConversionOptions.defaults(target, format);

                BufferedImage auto = ImagePreprocessor.preprocess(source, format, options);
                BufferedImage on = ImagePreprocessor.preprocess(source, format, options.withDither(Dither.ON));
                BufferedImage off = ImagePreprocessor.preprocess(source, format, options.withDither(Dither.OFF));

                assertTrue(same(auto, on) || same(auto, off),
                    format.id() + ": AUTO matched neither ON nor OFF");
            }));
        }
        return tests;
    }

    @Test
    void aCropCoveringEverythingIsNotACrop() throws IOException {
        // The skip matters: cropping to the whole image would still copy it, and on the default
        // path nothing should be copied at all.
        BufferedImage source = gradient(200, 100);
        ConversionOptions whole = defaults()
            .withFraming(new Framing(false, new Crop(0, 0, 200, 100)));

        assertTrue(same(ImagePreprocessor.preprocess(source, Format.CP_G3P, defaults()),
            ImagePreprocessor.preprocess(source, Format.CP_G3P, whole)));
    }

    @Test
    void fillingIsNotACropWhenTheShapesAlreadyAgree() throws IOException {
        // 768x384 is exactly the 384x192 canvas doubled.
        BufferedImage source = gradient(768, 384);
        ConversionOptions filling = defaults().withFraming(Framing.DEFAULT.withFill(true));

        assertTrue(same(ImagePreprocessor.preprocess(source, Format.CP_G3P, defaults()),
            ImagePreprocessor.preprocess(source, Format.CP_G3P, filling)));
    }

    @Test
    void fillingReachesTheEdgesThatLetterboxingLeavesWhite() throws IOException {
        // 8ca has a canvas the user cannot change, so a source of the wrong shape is padded out to
        // it with opaque white. That padding is the complaint this release exists to answer.
        BufferedImage tall = filled(100, 400, Color.RED);
        ConversionOptions options = ConversionOptions.defaults(
            Target.supporting(Mode.VAR, Format.TI_8CA).get(0), Format.TI_8CA);

        // Enlarging as well, since filling only decides the shape: cropping a tall source to a
        // wide canvas leaves it smaller than that canvas, and scaling up is a separate question.
        BufferedImage letterboxed = ImagePreprocessor.preprocess(tall, Format.TI_8CA,
            options.withEnlargeSmaller(true));
        BufferedImage filled = ImagePreprocessor.preprocess(tall, Format.TI_8CA,
            options.withEnlargeSmaller(true).withFraming(Framing.DEFAULT.withFill(true)));

        assertTrue(whiteAcrossTheMiddle(letterboxed) > letterboxed.getWidth() / 2,
            "expected most of the row to be padding");
        // Not zero: the crop is a whole number of pixels and cannot always hit the canvas ratio
        // exactly, so a column of padding can survive at one edge.
        assertTrue(whiteAcrossTheMiddle(filled) <= 2,
            "expected the padding to be gone, " + whiteAcrossTheMiddle(filled) + " pixels left");
    }

    @Test
    void fillingUsesTheWholeCanvasOnAnEditableFormat() throws IOException {
        // Where the canvas can be resized there is no padding to complain about: the image simply
        // comes out smaller than the screen it was meant for. Filling is what makes it fit.
        BufferedImage tall = filled(100, 400, Color.RED);

        ConversionOptions enlarging = defaults().withEnlargeSmaller(true);

        assertEquals(48, ImagePreprocessor.preprocess(tall, Format.CP_G3P, enlarging).getWidth());
        assertEquals(384, ImagePreprocessor.preprocess(tall, Format.CP_G3P,
            enlarging.withFraming(Framing.DEFAULT.withFill(true))).getWidth());
    }

    @Test
    void toneReachesThePixels() throws IOException {
        BufferedImage grey = filled(400, 200, new Color(0x80, 0x80, 0x80));

        BufferedImage plain = ImagePreprocessor.preprocess(grey, Format.CP_G3P, defaults());
        BufferedImage brighter = ImagePreprocessor.preprocess(grey, Format.CP_G3P,
            defaults().withAdjustments(Adjustments.NONE.withBrightness(40)));

        int before = (plain.getRGB(192, 96) >> 16) & 0xFF;
        int after = (brighter.getRGB(192, 96) >> 16) & 0xFF;
        assertTrue(after > before + 40, "expected a clearly brighter result, got " + before + " then " + after);
    }

    @Test
    void sharpeningRunsAfterTheResizeAndNotOverThePadding() throws IOException {
        // A portrait source on a fixed canvas sits in a field of white. If the mask ran after the
        // padding it would find that border and ring the picture with a dark halo.
        BufferedImage tall = filled(100, 400, new Color(0x40, 0x40, 0x40));
        ConversionOptions options = ConversionOptions.defaults(
            Target.supporting(Mode.VAR, Format.TI_8CA).get(0), Format.TI_8CA);

        BufferedImage sharpened = ImagePreprocessor.preprocess(tall, Format.TI_8CA,
            options.withAdjustments(Adjustments.NONE.withSharpen(3)));

        // Somewhere on the middle row the picture is still its own flat grey, undarkened.
        int y = sharpened.getHeight() / 2;
        boolean intact = false;
        for (int x = 0; x < sharpened.getWidth(); x++) {
            int red = (sharpened.getRGB(x, y) >> 16) & 0xFF;
            intact |= Math.abs(red - 0x40) <= 12;
        }
        assertTrue(intact, "the picture itself should have survived the sharpening");
    }

    /** How much of the middle row is padding. */
    private static int whiteAcrossTheMiddle(BufferedImage image) {
        int y = image.getHeight() / 2;
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            if (isWhite(image.getRGB(x, y))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isWhite(int argb) {
        return ((argb >> 16) & 0xFF) > 240 && ((argb >> 8) & 0xFF) > 240 && (argb & 0xFF) > 240;
    }
}
