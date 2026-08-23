package com.github.omniplotter.gui;

import com.github.omniplotter.engine.data.Tiling;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which cells of the grid are switched off, and when that judgement stops applying.
 *
 * <p>An exclusion describes a cell of a particular grid. Carried across a change of grid it would
 * describe a different piece of the page, and a cell nobody switched off would come back excluded
 * because something in the same position once was.
 */
class GridOverlayTest {

    private static GridOverlay overlay() {
        GridOverlay overlay = new GridOverlay(new ImageView());
        overlay.setImage(600, 400);
        return overlay;
    }

    @Test
    void everyCellIsIncludedToBeginWith() {
        GridOverlay overlay = overlay();
        overlay.setTiling(Tiling.of(3, 2));

        assertEquals(6, overlay.includedCount());
        assertTrue(overlay.excluded().isEmpty());
    }

    @Test
    void switchingCellsOffIsCounted() {
        GridOverlay overlay = overlay();
        overlay.setTiling(Tiling.of(3, 2));

        overlay.setExcluded(Set.of("r1c1", "r1c2"));

        assertEquals(4, overlay.includedCount());
    }

    @Test
    void changingTheGridForgetsWhatWasSwitchedOff() {
        GridOverlay overlay = overlay();
        overlay.setTiling(Tiling.of(3, 2));
        overlay.setExcluded(Set.of("r1c1"));

        overlay.setTiling(Tiling.of(6, 3));

        assertTrue(overlay.excluded().isEmpty(), "an exclusion describes a cell of the old grid");
        assertEquals(18, overlay.includedCount());
    }

    @Test
    void reselectingTheSameGridKeepsThem() {
        // Coming back to an image must not undo the choices made on it.
        GridOverlay overlay = overlay();
        overlay.setTiling(Tiling.of(3, 2));
        overlay.setExcluded(Set.of("r1c1"));

        overlay.setTiling(Tiling.of(3, 2));

        assertEquals(Set.of("r1c1"), overlay.excluded());
    }

    @Test
    void includingEverythingPutsThemAllBack() {
        GridOverlay overlay = overlay();
        overlay.setTiling(Tiling.of(2, 2));
        overlay.setExcluded(Set.of("r1c1", "r2c2"));

        overlay.includeEverything();

        assertEquals(4, overlay.includedCount());
    }

    @Test
    void withoutAGridThereIsNothingToCount() {
        GridOverlay overlay = overlay();

        assertEquals(1, overlay.includedCount());
        assertTrue(overlay.tiling().isWhole());
    }
}
