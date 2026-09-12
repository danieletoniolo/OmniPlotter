package com.github.omniplotter.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two ways a step can be closed, which are not the same thing.
 *
 * <p>The rule that matters most here is the dullest: a folded step has to be openable. The first
 * version derived the answer from the body node's visibility and negated it, so the one step that
 * starts folded could not be opened at all — and with the logic inside the nodes, no test could
 * have said so.
 */
class StepStateTest {

    @Test
    void aFoldedStepOpensAndClosesAgain() {
        StepState state = new StepState(true);
        assertTrue(state.isFolded());
        assertFalse(state.bodyShown());

        state.toggleFold();
        assertFalse(state.isFolded());
        assertTrue(state.bodyShown(), "a folded step has to be openable");

        state.toggleFold();
        assertFalse(state.bodyShown());
    }

    @Test
    void waitingAndFoldedAreDifferentThings() {
        StepState state = new StepState(false);
        state.setWaiting(true);

        assertTrue(state.isWaiting());
        assertFalse(state.isFolded(), "waiting is not folding");

        // Both hide the body, which is why they have to look different on screen: the reason line
        // and the dimming belong to waiting alone, and a chevron makes no sense on it.
        assertFalse(state.bodyShown());
        assertTrue(state.reasonShown());
        assertFalse(state.subtitleShown());
        assertTrue(state.dimmed());
        assertFalse(state.chevronShown());
    }

    @Test
    void anOpenStepSaysWhatItIsForAndOffersItsChevron() {
        StepState state = new StepState(false);

        assertTrue(state.bodyShown());
        assertTrue(state.subtitleShown());
        assertFalse(state.reasonShown());
        assertFalse(state.dimmed());
        assertTrue(state.chevronShown());
    }

    @Test
    void aWaitingStepCannotBeOpenedByClickingIt() {
        StepState state = new StepState(false);
        state.setWaiting(true);

        state.toggleFold();

        // Offered first and taken out again: with no file loaded, clicking a dim heading put a
        // panel of settings on screen for a conversion with no subject. Dimming has to mean the
        // step is unavailable, not that it is available and merely discouraged.
        assertTrue(state.isWaiting());
        assertFalse(state.bodyShown());
        assertFalse(state.clickable());
    }

    @Test
    void foldingIsStillTheUsersToChooseAfterWaiting() {
        // A step that waited, arrived, and was then folded: the fold is not undone by the next
        // recompute either, since nothing in here touches it.
        StepState state = new StepState(false);
        state.setWaiting(true);
        state.setWaiting(false);
        state.toggleFold();

        state.setWaiting(false);
        assertTrue(state.isFolded());
        assertFalse(state.bodyShown());
    }
}
