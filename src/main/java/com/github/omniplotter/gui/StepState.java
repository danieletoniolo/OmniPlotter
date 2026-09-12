package com.github.omniplotter.gui;

/**
 * Which of a step's parts are on screen, and why.
 *
 * <p>Three states, not two. A step is <b>waiting</b> when it does not apply yet — dimmed, with the
 * reason underneath — <b>collapsed</b> when it applies and something else is open, and
 * <b>expanded</b> when it is the one being worked in. Only one step is ever expanded, which is why
 * a step cannot decide that for itself: it is told.
 *
 * <p>A value with no JavaFX in it, so this can be checked without a running toolkit — the same
 * reason {@link Fit} is a value. It is not fussiness: the first version of this logic asked the
 * body node whether it was visible and negated the answer, which reads like a toggle and means
 * "close" when the thing is already closed. The one step that started closed could not be opened at
 * all, and nothing in the build could have noticed.
 */
public final class StepState {

    private boolean waiting;
    private boolean expanded;

    public StepState(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Whether this step applies yet.
     *
     * <p>A step that stops applying closes with it. Leaving it open would put a panel of settings
     * on screen for something the file can no longer be — the queue emptied under an open step 4.
     */
    public void setWaiting(boolean wait) {
        waiting = wait;
        if (wait) {
            expanded = false;
        }
    }

    /**
     * Opens or closes it, unless it is waiting.
     *
     * <p>Refused rather than ignored quietly: dimming has to mean unavailable, and a waiting step
     * that could still be opened by the accordion walking past it would mean the opposite.
     */
    public void setExpanded(boolean open) {
        if (open && waiting) {
            return;
        }
        expanded = open;
    }

    public boolean bodyShown() {
        return !waiting && expanded;
    }

    /** The line that says what an open step is for; it goes away with the body. */
    public boolean subtitleShown() {
        return !waiting && expanded;
    }

    /** The line that says why a step is waiting, which is the only time it is worth saying. */
    public boolean reasonShown() {
        return waiting;
    }

    /**
     * Every step that applies carries one.
     *
     * <p>A row of chevrons is what says these things open and close at all — one chevron on one
     * step said only that step 3 was odd.
     */
    public boolean chevronShown() {
        return !waiting;
    }

    /** Whether a click anywhere on the heading does something, which is what the cursor says. */
    public boolean clickable() {
        return !waiting;
    }

    public boolean dimmed() {
        return waiting;
    }
}
