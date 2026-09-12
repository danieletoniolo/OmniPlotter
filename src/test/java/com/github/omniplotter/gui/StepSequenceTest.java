package com.github.omniplotter.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the wheel lands, which is the part of an accordion that is all edge cases.
 *
 * <p>The five steps of the output panel, as they actually occur: the first always applies, the
 * middle ones apply once there is a file, and the fourth only when that file is one that can be cut
 * into tiles.
 */
class StepSequenceTest {

    private static final boolean[] NOTHING_YET = {true, false, false, false, false};
    private static final boolean[] A_PHOTOGRAPH = {true, true, true, false, true};
    private static final boolean[] A_DOCUMENT = {true, true, true, true, true};

    @Test
    void walksDownAndBackUp() {
        assertEquals(1, StepSequence.next(A_DOCUMENT, 0, 1));
        assertEquals(2, StepSequence.next(A_DOCUMENT, 1, 1));
        assertEquals(1, StepSequence.next(A_DOCUMENT, 2, -1));
    }

    @Test
    void theEndsAreWallsAndNotAWayRound() {
        assertEquals(4, StepSequence.next(A_DOCUMENT, 4, 1), "past the last step is the last step");
        assertEquals(0, StepSequence.next(A_DOCUMENT, 0, -1), "and above the first is the first");
    }

    @Test
    void stepsOverOneThatDoesNotApply() {
        // A photograph is not something to cut into tiles, so 4 is dim: the wheel goes 3 to 5.
        assertEquals(4, StepSequence.next(A_PHOTOGRAPH, 2, 1));
        assertEquals(2, StepSequence.next(A_PHOTOGRAPH, 4, -1));
    }

    @Test
    void goesNowhereWhenNothingElseApplies() {
        assertEquals(0, StepSequence.next(NOTHING_YET, 0, 1), "with no file, there is nowhere to go");
        assertEquals(0, StepSequence.next(NOTHING_YET, 0, -1));
    }

    @Test
    void anOpenStepThatStopsApplyingFallsBack() {
        // The queue emptied while step 4 was open. Everything after it went too, so the nearest
        // one left is the first.
        assertEquals(0, StepSequence.nearest(NOTHING_YET, 3));
        // And one that still applies is left where it is.
        assertEquals(3, StepSequence.nearest(A_DOCUMENT, 3));
    }

    @Test
    void fallsBackToWhatCameBeforeRatherThanAfter() {
        boolean[] onlyTheEnds = {true, false, false, false, true};

        // Equally far either way, and the step before is the one already worked through.
        assertEquals(0, StepSequence.nearest(onlyTheEnds, 2));
    }
}
