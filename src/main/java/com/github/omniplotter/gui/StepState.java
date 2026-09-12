package com.github.omniplotter.gui;

/**
 * Which of a step's parts are on screen, and why.
 *
 * <p>A step has two ways of being closed and they are not the same thing. It is <b>waiting</b> when
 * it does not apply yet — dimmed, with the reason underneath — and <b>folded</b> when it applies
 * and the user has put it away. Both hide the body, which is exactly why they have to be told
 * apart.
 *
 * <p>A value with no JavaFX in it, so this can be checked without a running toolkit — the same
 * reason {@link Fit} is a value. It is not fussiness: the first version of this logic asked the
 * body node whether it was visible and negated the answer, which reads like a toggle and means
 * "fold" when the thing is already folded. The one step that starts folded could not be opened at
 * all, and nothing in the build could have noticed.
 */
public final class StepState {

    private boolean waiting;
    private boolean folded;

    public StepState(boolean folded) {
        this.folded = folded;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public boolean isFolded() {
        return folded;
    }

    /** Whether this step applies yet. */
    public void setWaiting(boolean wait) {
        waiting = wait;
    }

    /**
     * Folds and unfolds — and does nothing at all while the step is waiting.
     *
     * <p>A waiting step opened by hand was offered first and taken out again: with no file loaded,
     * clicking a dim heading put its controls on screen, which is a panel of settings for a
     * conversion that has no subject. The dimming has to mean the step is not available, not that
     * it is available and merely discouraged.
     */
    public void toggleFold() {
        if (waiting) {
            return;
        }
        folded = !folded;
    }

    public boolean bodyShown() {
        return !waiting && !folded;
    }

    /** The line that says what an open step is for; it goes away with the body. */
    public boolean subtitleShown() {
        return !waiting && !folded;
    }

    /** The line that says why a step is waiting, which is the only time it is worth saying. */
    public boolean reasonShown() {
        return waiting;
    }

    /** Nothing to fold away while the step is not applicable in the first place. */
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
