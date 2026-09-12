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

    /** Set once the user has opened a waiting step by hand, after which nothing closes it again. */
    private boolean asked;

    public StepState(boolean folded) {
        this.folded = folded;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public boolean isFolded() {
        return folded;
    }

    /**
     * Whether this step applies yet.
     *
     * <p>Ignored when it would put away a step the user has asked to see: the sequence is a
     * suggestion about what is worth filling in next, never a lock on a control.
     */
    public void setWaiting(boolean wait) {
        if (wait && asked) {
            return;
        }
        waiting = wait;
    }

    public void toggleFold() {
        folded = !folded;
    }

    /** Opens a waiting step for good. */
    public void openByHand() {
        asked = true;
        waiting = false;
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

    public boolean dimmed() {
        return waiting;
    }
}
