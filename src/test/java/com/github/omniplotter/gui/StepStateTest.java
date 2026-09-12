package com.github.omniplotter.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three states a step can be in, and which of them a click may reach.
 *
 * <p>The rule that matters most here is the dullest: a closed step has to be openable. The first
 * version derived the answer from the body node's visibility and negated it, so the one step that
 * started closed could not be opened at all — and with the logic inside the nodes, no test could
 * have said so.
 */
class StepStateTest {

    @Test
    void aClosedStepOpensAndClosesAgain() {
        StepState state = new StepState(false);
        assertFalse(state.bodyShown());

        state.setExpanded(true);
        assertTrue(state.isExpanded());
        assertTrue(state.bodyShown(), "a closed step has to be openable");

        state.setExpanded(false);
        assertFalse(state.bodyShown());
    }

    @Test
    void waitingAndClosedAreDifferentThings() {
        StepState state = new StepState(false);
        state.setWaiting(true);
        state.setExplaining(true);

        // Both hide the body, which is why they have to look different on screen: the reason line
        // and the dimming belong to waiting alone, and a chevron makes no sense on it.
        assertFalse(state.bodyShown());
        assertTrue(state.reasonShown());
        assertFalse(state.subtitleShown());
        assertTrue(state.dimmed());
        assertFalse(state.chevronShown());
        assertFalse(state.clickable());
    }

    @Test
    void aWaitingStepExplainsItselfOnlyWhenItIsTheOddOneOut() {
        StepState state = new StepState(false);
        state.setWaiting(true);

        // With nothing to convert, every step past the first is dim and that is the whole story.
        // One of them singling itself out to say why reads as a fault among four that keep quiet.
        assertFalse(state.reasonShown());

        state.setExplaining(true);
        assertTrue(state.reasonShown(), "with a file loaded, a step still dim owes a reason");
    }

    @Test
    void aWaitingStepCannotBeOpened() {
        StepState state = new StepState(false);
        state.setWaiting(true);

        state.setExpanded(true);

        // Dimming has to mean unavailable. Opened anyway, it would put a panel of settings on
        // screen for a conversion with no subject.
        assertFalse(state.isExpanded());
        assertFalse(state.bodyShown());
    }

    @Test
    void aStepThatStopsApplyingClosesWithIt() {
        StepState state = new StepState(true);
        state.setExplaining(true);
        assertTrue(state.bodyShown());

        state.setWaiting(true);

        assertFalse(state.isExpanded(), "an open step that no longer applies cannot stay open");
        assertTrue(state.reasonShown());
    }

    @Test
    void anOpenStepSaysWhatItIsForAndCarriesItsChevron() {
        StepState state = new StepState(true);

        assertTrue(state.bodyShown());
        assertTrue(state.subtitleShown());
        assertFalse(state.reasonShown());
        assertFalse(state.dimmed());
        assertTrue(state.chevronShown());
        assertTrue(state.clickable());
    }
}
