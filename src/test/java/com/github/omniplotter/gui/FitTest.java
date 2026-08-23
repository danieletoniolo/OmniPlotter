package com.github.omniplotter.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mapping between the preview on screen and the pixels of the image.
 *
 * <p>Everything the crop rectangle does passes through this, and none of it is visible when it is
 * wrong by a few pixels — the rectangle simply selects slightly the wrong part of the picture, and
 * the only symptom is an output that is off by a sliver. Worth pinning down away from the toolkit.
 */
class FitTest {

    @Test
    void aWideImageInATallBoxIsCentredVertically() {
        // 400x100 into 200x200: it fits by width, at half size, leaving 75 above and below.
        Fit fit = Fit.of(200, 200, 400, 100);

        assertEquals(0.5, fit.scale(), 1e-9);
        assertEquals(0, fit.offsetX(), 1e-9);
        assertEquals(75, fit.offsetY(), 1e-9);
    }

    @Test
    void aTallImageInAWideBoxIsCentredHorizontally() {
        Fit fit = Fit.of(400, 200, 100, 400);

        assertEquals(0.5, fit.scale(), 1e-9);
        assertEquals(175, fit.offsetX(), 1e-9);
        assertEquals(0, fit.offsetY(), 1e-9);
    }

    @Test
    void screenAndImageCoordinatesAreInverses() {
        Fit fit = Fit.of(640, 480, 300, 200);

        for (int x : new int[]{0, 1, 149, 299, 300}) {
            assertEquals(x, fit.toImageX(fit.toScreenX(x)), "x=" + x);
        }
        for (int y : new int[]{0, 1, 99, 199, 200}) {
            assertEquals(y, fit.toImageY(fit.toScreenY(y)), "y=" + y);
        }
    }

    @Test
    void theCornersOfTheImageLandOnTheCornersOfTheLetterbox() {
        Fit fit = Fit.of(400, 200, 100, 400);

        assertEquals(fit.offsetX(), fit.toScreenX(0), 1e-9);
        assertEquals(400 - fit.offsetX(), fit.toScreenX(100), 1e-9);
        assertEquals(0, fit.toScreenY(0), 1e-9);
        assertEquals(200, fit.toScreenY(400), 1e-9);
    }

    @Test
    void anEmptyImageDoesNotDivideByZero() {
        // The overlay is asked to draw before anything is loaded, and while a file that failed to
        // decode is selected.
        Fit fit = Fit.of(400, 200, 0, 0);

        assertEquals(1, fit.scale(), 1e-9);
        assertEquals(0, fit.toImageX(0));
    }
}
