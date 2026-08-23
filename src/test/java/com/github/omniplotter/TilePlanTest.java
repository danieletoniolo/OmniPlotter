package com.github.omniplotter;

import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.Target;
import com.github.omniplotter.engine.data.TilePlan;
import com.github.omniplotter.engine.data.Tiling;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the pieces get called, and what the user is told before any of them are written.
 *
 * <p>The warning is the point of the type. Producing twenty-seven files and then discovering the
 * calculator holds three is a tool that has wasted someone's time; the same information a minute
 * earlier is a choice.
 */
class TilePlanTest {

    private static final List<Integer> ONE_PAGE = List.of(1);

    @Test
    void oneTileOfOnePageIsNamedAsItAlwaysWas() {
        // The path 1.1 already had, and it has to keep producing exactly what it produced then.
        TilePlan plan = TilePlan.of("photo.png", Format.CP_G3P, Target.CASIO_CG,
            ONE_PAGE, Tiling.NONE, 1);

        assertEquals(1, plan.count());
        assertEquals("photo", plan.tiles().get(0).onCalcName());
        assertEquals("photo.g3p", plan.tiles().get(0).fileName());
        assertTrue(plan.warnings().isEmpty());
    }

    @Test
    void theTwoNamesSayDifferentThings() {
        TilePlan plan = TilePlan.of("notes.pdf", Format.CP_G3P, Target.CASIO_CG,
            List.of(1, 2), Tiling.of(3, 2), 1);

        // On the calculator: short, numbered, unique across pages.
        var second = plan.at(1, 1, 2);
        assertEquals("notes02", second.onCalcName());
        // On disk: which page and which part of it.
        assertEquals("notes-p1-r1c2.g3p", second.fileName());

        var acrossThePageBreak = plan.at(2, 1, 1);
        assertEquals("notes07", acrossThePageBreak.onCalcName());
        assertEquals("notes-p2-r1c1.g3p", acrossThePageBreak.fileName());
    }

    @Test
    void everyPieceIsDistinctInBothPlaces() {
        TilePlan plan = TilePlan.of("notes.pdf", Format.CP_G3P, Target.CASIO_CG,
            List.of(1, 2, 3), Tiling.of(3, 2), 1);

        assertEquals(18, plan.count());
        assertEquals(18, distinct(plan, TilePlan.PlannedTile::fileName));
        assertEquals(18, distinct(plan, TilePlan.PlannedTile::onCalcName));
    }

    @Test
    void namesStayWithinTheEightCharactersCasioAllows() {
        TilePlan plan = TilePlan.of("appuntidianalisi.pdf", Format.CP_G3P, Target.CASIO_CG,
            ONE_PAGE, Tiling.of(4, 3), 1);

        for (var tile : plan.tiles()) {
            assertTrue(tile.onCalcName().length() <= 8,
                tile.onCalcName() + " is " + tile.onCalcName().length() + " characters");
        }
        assertEquals(12, distinct(plan, TilePlan.PlannedTile::onCalcName));
    }

    @Test
    void aPageWithMoreTilesThanSlotsSaysSoBeforehand() {
        // A TI-73 holds three pictures. Twelve tiles is not a failure — the files are all written
        // and all different — but it is something to know before, not after.
        TilePlan plan = TilePlan.of("notes.pdf", Format.TI_8XI, Target.TI_73,
            ONE_PAGE, Tiling.of(4, 3), 1);

        assertEquals(12, plan.count());
        assertEquals(1, plan.warnings().size());
        String warning = plan.warnings().get(0);
        assertTrue(warning.contains("12 tiles"), warning);
        assertTrue(warning.contains("3 picture slots"), warning);
        assertEquals(12, distinct(plan, TilePlan.PlannedTile::fileName), "the files stay distinct");
    }

    @Test
    void slotsRunThroughTheRangeAndComeBackRound() {
        TilePlan plan = TilePlan.of("notes.pdf", Format.TI_8XI, Target.TI_73,
            ONE_PAGE, Tiling.of(4, 1), 1);

        // A TI-73's slots are 1 to 3, so a fourth tile is back at the first.
        assertEquals(List.of(1, 2, 3, 1),
            plan.tiles().stream().map(TilePlan.PlannedTile::slot).toList());
    }

    @Test
    void aStartingSlotIsRespected() {
        TilePlan plan = TilePlan.of("notes.pdf", Format.TI_8XI, Target.TI_8X_COLOR,
            ONE_PAGE, Tiling.of(1, 3), 5);

        assertEquals(List.of(5, 6, 7),
            plan.tiles().stream().map(TilePlan.PlannedTile::slot).toList());
    }

    @Test
    void aFormatWithoutSlotsIsNotWarnedAboutThem() {
        // cp.g3p carries a name rather than a slot, so twenty-seven of them is just twenty-seven
        // files and nothing to say about it.
        TilePlan plan = TilePlan.of("notes.pdf", Format.CP_G3P, Target.CASIO_CG,
            ONE_PAGE, Tiling.of(9, 3), 1);

        assertEquals(27, plan.count());
        assertTrue(plan.warnings().isEmpty());
    }

    @Test
    void theCountIsAvailableBeforeAnythingIsWritten() {
        TilePlan plan = TilePlan.of("notes.pdf", Format.CP_G3P, Target.CASIO_CG,
            List.of(1, 2, 3, 4, 5), Tiling.of(4, 2), 1);

        assertEquals("40 files from 5 pages", plan.summary());
        assertFalse(plan.tiles().isEmpty());
    }

    private static long distinct(TilePlan plan,
                                 java.util.function.Function<TilePlan.PlannedTile, String> field) {
        Set<String> seen = plan.tiles().stream().map(field).collect(Collectors.toSet());
        return seen.size();
    }
}
