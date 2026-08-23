package com.github.omniplotter.gui;

import com.github.omniplotter.engine.data.Crop;
import org.junit.jupiter.api.Test;

import javafx.scene.image.ImageView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the overlay does and does not decide an image has been cropped.
 *
 * <p>The distinction is not cosmetic. Carrying a rectangle is what makes the tool re-arm when an
 * image comes back, so anything that hands an image a rectangle it was never given makes the tool
 * follow the selection around the queue.
 */
class CropOverlayStateTest {

    private static CropOverlay overlay() {
        return new CropOverlay(new ImageView());
    }

    @Test
    void openingTheToolDoesNotCropAnything() {
        CropOverlay overlay = overlay();
        int[] changes = {0};
        overlay.setOnChange(() -> changes[0]++);
        overlay.setImage(400, 300, null);

        overlay.setActive(true);

        assertNull(overlay.crop(), "opening the tool must not invent a rectangle");
        assertEquals(0, changes[0], "and must not tell anyone the image changed");
        assertTrue(overlay.isActive());
    }

    @Test
    void aRectangleComesBackWithItsImage() {
        CropOverlay overlay = overlay();
        overlay.setImage(400, 300, new Crop(10, 20, 100, 50));

        assertEquals(new Crop(10, 20, 100, 50), overlay.crop());
    }

    @Test
    void switchingToAnUncroppedImageLeavesNothingBehind() {
        CropOverlay overlay = overlay();
        overlay.setImage(400, 300, new Crop(10, 20, 100, 50));

        overlay.setImage(640, 480, null);

        assertNull(overlay.crop());
    }

    @Test
    void aRectangleTooBigForTheNewImageIsBroughtInside() {
        CropOverlay overlay = overlay();
        overlay.setImage(100, 100, new Crop(50, 50, 400, 400));

        assertEquals(new Crop(50, 50, 50, 50), overlay.crop());
    }

    @Test
    void aCropSurvivesTheToolBeingSwitchedOff() {
        // Switching the tool off stops the rectangle being editable, not being true: the image is
        // still cropped and still has to show it.
        CropOverlay overlay = overlay();
        overlay.setImage(400, 300, new Crop(10, 20, 100, 50));
        overlay.setActive(true);

        overlay.setActive(false);

        assertFalse(overlay.isActive());
        assertEquals(new Crop(10, 20, 100, 50), overlay.crop());
    }

    @Test
    void losingTheImageLeavesNothingDrawn() {
        // What happens when the image being cropped is deleted from the queue: the overlay used to
        // keep drawing the rectangle over an empty card.
        CropOverlay overlay = overlay();
        overlay.setImage(400, 300, new Crop(10, 20, 100, 50));
        overlay.setActive(true);

        overlay.setImage(0, 0, null);

        assertNull(overlay.crop());
    }

    @Test
    void clearingReportsTheChangeSoTheImageStopsBeingCropped() {
        CropOverlay overlay = overlay();
        int[] changes = {0};
        overlay.setImage(400, 300, new Crop(10, 20, 100, 50));
        overlay.setOnChange(() -> changes[0]++);

        overlay.clear();

        assertNull(overlay.crop());
        assertEquals(1, changes[0]);
    }
}
