package com.github.omniplotter.gui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

/**
 * The window's small vocabulary of motion.
 *
 * <p>JavaFX 21 has no CSS transitions — those arrive in 23 — so every one of these is built by hand
 * out of {@code javafx.animation}. They are deliberately few and deliberately short: the point is
 * to make a change legible, not to make the user wait for it.
 *
 * <p>Each running transition is parked in the node's property map. Every entry point stops whatever
 * it finds there before starting its own, because these are driven by user input that arrives far
 * faster than an animation finishes — dragging a spinner queues a new preview on every tick — and
 * animations that stack instead of replacing each other are exactly what makes an interface feel
 * gummy.
 */
public final class Animations {

    private Animations() {}

    private static final String RUNNING = "omniplotter.animation";

    private static final Duration RISE = Duration.millis(320);
    private static final Duration STAGGER = Duration.millis(55);
    private static final double RISE_BY = 12;

    /** Short enough that a burst of preview updates still reads as continuous. */
    private static final Duration SWAP_OUT = Duration.millis(60);
    private static final Duration SWAP_IN = Duration.millis(110);

    private static final Duration VEIL = Duration.millis(120);

    /**
     * The surfaces settling into place on first show: each fades in while rising the last few
     * pixels, one after the next from left to right. Seen once per launch.
     */
    public static void intro(Node... surfaces) {
        Duration delay = Duration.ZERO;
        for (Node surface : surfaces) {
            if (surface == null) {
                continue;
            }
            surface.setOpacity(0);
            surface.setTranslateY(RISE_BY);

            ParallelTransition step = new ParallelTransition(
                fade(surface, 0, 1, RISE),
                rise(surface, RISE_BY, 0, RISE));
            step.setDelay(delay);
            play(surface, step);
            delay = delay.add(STAGGER);
        }
    }

    /**
     * Replaces the image behind a dissolve rather than letting it jump.
     *
     * <p>Worth the two hundred milliseconds because the preview is the one thing in the window
     * being watched closely: changing the colour count should look like the picture changing, not
     * like the picture being swapped out.
     */
    public static void swap(ImageView view, Image next) {
        stop(view);
        if (next == null) {
            view.setImage(null);
            view.setOpacity(1);
            return;
        }
        if (view.getImage() == null) {
            view.setImage(next);
            play(view, fade(view, 0, 1, SWAP_IN));
            return;
        }
        FadeTransition out = fade(view, view.getOpacity(), 0, SWAP_OUT);
        out.setOnFinished(e -> view.setImage(next));
        play(view, new SequentialTransition(out, fade(view, 0, 1, SWAP_IN)));
    }

    /** Fades an overlay in or out, taking it out of the picking order when it is gone. */
    public static void reveal(Node node, boolean show) {
        stop(node);
        if (show) {
            node.setVisible(true);
        }
        FadeTransition fade = fade(node, node.getOpacity(), show ? 1 : 0, VEIL);
        if (!show) {
            fade.setOnFinished(e -> node.setVisible(false));
        }
        play(node, fade);
    }

    /** A notice arriving at the top of the window: down and in, using the same rise as the intro. */
    public static void dropIn(Node node) {
        stop(node);
        node.setOpacity(0);
        node.setTranslateY(-RISE_BY);
        play(node, new ParallelTransition(
            fade(node, 0, 1, RISE),
            rise(node, -RISE_BY, 0, RISE)));
    }

    /** The same notice leaving, with the caller's removal deferred until it has gone. */
    public static void dismiss(Node node, Runnable after) {
        stop(node);
        Duration out = Duration.millis(180);
        ParallelTransition leave = new ParallelTransition(
            fade(node, node.getOpacity(), 0, out),
            rise(node, node.getTranslateY(), -RISE_BY, out));
        leave.setOnFinished(e -> after.run());
        play(node, leave);
    }

    // --- plumbing ---------------------------------------------------------------------------

    private static FadeTransition fade(Node node, double from, double to, Duration over) {
        FadeTransition fade = new FadeTransition(over, node);
        fade.setFromValue(from);
        fade.setToValue(to);
        fade.setInterpolator(Interpolator.EASE_BOTH);
        return fade;
    }

    private static TranslateTransition rise(Node node, double from, double to, Duration over) {
        TranslateTransition move = new TranslateTransition(over, node);
        move.setFromY(from);
        move.setToY(to);
        move.setInterpolator(Interpolator.EASE_OUT);
        return move;
    }

    private static void play(Node node, Transition transition) {
        node.getProperties().put(RUNNING, transition);
        transition.play();
    }

    private static void stop(Node node) {
        if (node.getProperties().remove(RUNNING) instanceof Transition running) {
            running.stop();
        }
    }
}
