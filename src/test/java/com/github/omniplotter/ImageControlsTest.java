package com.github.omniplotter;

import com.github.omniplotter.engine.data.Adjustments;
import com.github.omniplotter.engine.data.Crop;
import com.github.omniplotter.engine.data.Dither;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.Framing;
import com.github.omniplotter.engine.data.Look;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings that decide what the pipeline is asked to do, before it does any of it.
 *
 * <p>Geometry and defaults rather than pixels: what these types get wrong is arithmetic and the
 * meaning of "leave it alone", and both are cheaper to pin down here than through an image.
 */
class ImageControlsTest {

    @Test
    void autoDeferstoTheFormatAndTheOthersDoNot() {
        // AUTO exists so that a caller does not have to know what the reference does per format.
        assertTrue(Dither.AUTO.resolve(true));
        assertFalse(Dither.AUTO.resolve(false));
        assertTrue(Dither.ON.resolve(false));
        assertFalse(Dither.OFF.resolve(true));
    }

    @Test
    void adjustmentsClampInsteadOfRefusing() {
        Adjustments extreme = new Adjustments(999, -999, 99.0, 500, -3);

        assertEquals(100, extreme.brightness());
        assertEquals(-100, extreme.contrast());
        assertEquals(5.0, extreme.gamma());
        assertEquals(100, extreme.saturation());
        assertEquals(0.0, extreme.sharpen());
    }

    @Test
    void neutralIsTheOnlyNeutral() {
        assertTrue(Adjustments.NONE.isNeutral());
        assertFalse(Adjustments.NONE.withContrast(1).isNeutral());
        assertFalse(Adjustments.NONE.withGamma(1.1).isNeutral());
        // A look has to actually change something, or it is a no-op with a name.
        assertFalse(Look.DOCUMENT.adjustments().isNeutral());
        assertFalse(Look.PHOTO.adjustments().isNeutral());
    }

    @Test
    void coverTakesTheLargestCentredRectangleOfTheCanvasShape() {
        // A portrait photograph onto a 2:1 screen: full width, a centred band of the height.
        Crop wide = Crop.cover(600, 900, 384, 192);
        assertEquals(600, wide.width());
        assertEquals(300, wide.height());
        assertEquals(0, wide.x());
        assertEquals(300, wide.y());

        // And the other way round: a panorama onto a squarer canvas.
        Crop tall = Crop.cover(1000, 200, 320, 240);
        assertEquals(266, tall.width());
        assertEquals(200, tall.height());
        assertEquals(367, tall.x());
        assertEquals(0, tall.y());
    }

    @Test
    void coverIsAnIdentityWhenTheShapesAlreadyAgree() {
        Crop same = Crop.cover(768, 384, 384, 192);

        assertTrue(same.isWhole(768, 384));
        // Which is what lets the pipeline skip cropping entirely rather than copy the image.
        assertNull(new Framing(true, null).rectangleFor(768, 384, 384, 192));
    }

    @Test
    void aRectangleSurvivesBeingHandedASmallerImage() {
        // The queue keeps one of these per job and the settings outlive the file they came from.
        Crop confined = new Crop(50, 50, 400, 400).clampedTo(100, 200);

        assertEquals(new Crop(50, 50, 50, 150), confined);
        assertNull(new Crop(500, 500, 10, 10).clampedTo(100, 100));
    }

    @Test
    void anExplicitRectangleWinsOverFill() {
        Framing both = new Framing(true, new Crop(10, 10, 100, 100));

        assertEquals(new Crop(10, 10, 100, 100), both.rectangleFor(400, 400, 384, 192));
    }

    @Test
    void cropParsingRejectsWhatItCannotUse() {
        assertEquals(new Crop(1, 2, 3, 4), Crop.parse("1,2,3,4"));
        assertEquals(new Crop(1, 2, 3, 4), Crop.parse(" 1, 2 ,3,4 "));

        assertThrows(IllegalArgumentException.class, () -> Crop.parse("1,2,3"));
        assertThrows(IllegalArgumentException.class, () -> Crop.parse("1,2,3,x"));
        assertThrows(IllegalArgumentException.class, () -> Crop.parse("0,0,0,10"));
        assertThrows(IllegalArgumentException.class, () -> new Crop(0, 0, -1, 10));
    }

    @Test
    void defaultsAreTheReferencePath() {
        // The whole release rests on this: anything that changes here changes every conversion
        // that nobody asked to change.
        var options = com.github.omniplotter.engine.data.ConversionOptions
            .defaults(com.github.omniplotter.engine.data.Target.CASIO_CG, Format.CP_G3P);

        assertTrue(options.adjustments().isNeutral());
        assertEquals(Dither.AUTO, options.dither());
        assertEquals(Framing.DEFAULT, options.framing());
        assertNull(options.framing().rectangleFor(1000, 1000, 384, 192));
    }
}
