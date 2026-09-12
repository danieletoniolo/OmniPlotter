package com.github.omniplotter.gui;

/**
 * Which step the panel moves to, given which ones apply.
 *
 * <p>The part of an accordion with an off-by-one in it: it has to step over the ones that do not
 * apply, stop at both ends rather than wrap around, and cope with there being nothing left in the
 * direction asked for. A value with no JavaFX in it, so all of that can be checked without a
 * running toolkit.
 */
public final class StepSequence {

    private StepSequence() {}

    /**
     * The nearest step that applies, in one direction.
     *
     * @param direction +1 to move down the panel, -1 to move back up
     * @return that step, or {@code from} unchanged when there is none that way — the ends of the
     *     list are walls rather than a way round to the other side
     */
    public static int next(boolean[] available, int from, int direction) {
        for (int i = from + direction; i >= 0 && i < available.length; i += direction) {
            if (available[i]) {
                return i;
            }
        }
        return from;
    }

    /**
     * Somewhere sensible to be when the open step stops applying.
     *
     * <p>Searched outwards from where it was, earlier before later at the same distance: a step
     * that has just become irrelevant leaves you closer to what came before it than to what comes
     * after.
     *
     * @return the index to open, or -1 when nothing applies at all
     */
    public static int nearest(boolean[] available, int from) {
        if (from >= 0 && from < available.length && available[from]) {
            return from;
        }
        for (int distance = 1; distance < available.length; distance++) {
            int back = from - distance;
            if (back >= 0 && back < available.length && available[back]) {
                return back;
            }
            int forward = from + distance;
            if (forward >= 0 && forward < available.length && available[forward]) {
                return forward;
            }
        }
        return -1;
    }
}
