package com.github.omniplotter.gui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
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
     * One segment of rail, and one step arriving behind it.
     *
     * <p>Both shorter than the window's other motion, because these run one after another: four of
     * them at the length of the intro would be two seconds before the last step could be read,
     * which is a cascade turning into a wait.
     */
    private static final Duration DRAW = Duration.millis(120);
    private static final Duration ARRIVE = Duration.millis(220);

    /** One step closing while another opens. Both run at once, so this is the whole movement. */
    private static final Duration OPEN = Duration.millis(240);

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
     * One stage of a cascade down a timeline.
     *
     * @param rail the segment to draw, or null to arrive with no line to travel first
     * @param content what fades in once the line gets there, or null for a stop being passed
     */
    public record Stage(javafx.scene.transform.Scale rail, Node content) {}

    /**
     * A line drawing itself down a panel, each stop's content arriving as it is reached.
     *
     * <p>One {@code SequentialTransition} rather than two timelines with matching delays, so the
     * line reaching a badge and that badge's content arriving cannot drift apart on a slow machine
     * — which is the one way this effect goes wrong.
     *
     * @param anchor the node the whole cascade is parked on, so a second run replaces it
     */
    public static void timeline(Node anchor, java.util.List<Stage> stages) {
        if (stages.isEmpty()) {
            return;
        }
        stop(anchor);

        SequentialTransition all = new SequentialTransition();
        for (Stage stage : stages) {
            SequentialTransition one = new SequentialTransition();
            if (stage.rail() != null) {
                stage.rail().setY(0);
                one.getChildren().add(new Timeline(new KeyFrame(DRAW,
                    new KeyValue(stage.rail().yProperty(), 1, Interpolator.EASE_OUT))));
            }
            if (stage.content() != null) {
                Node content = stage.content();
                content.setOpacity(0);
                content.setTranslateY(RISE_BY);
                one.getChildren().add(new ParallelTransition(
                    fade(content, 0, 1, ARRIVE),
                    rise(content, RISE_BY, 0, ARRIVE)));
            }
            all.getChildren().add(one);
        }
        play(anchor, all);
    }

    /**
     * Opens or closes a region by driving its preferred height.
     *
     * <p>The caller measures the target and hands it over: a region that fills what is left of its
     * parent has no height of its own to animate to, and one that has just been unhidden has not
     * been laid out yet. It is also the caller's job to release the preferred height afterwards —
     * left pinned at the measured value, a panel whose contents change later would be cut off at
     * the height it happened to have when it opened.
     */
    public static void height(Region region, double to, Runnable after) {
        stop(region);
        Timeline resize = new Timeline(new KeyFrame(OPEN,
            new KeyValue(region.prefHeightProperty(), to, Interpolator.EASE_BOTH)));
        if (after != null) {
            resize.setOnFinished(e -> after.run());
        }
        play(region, resize);
    }

    /**
     * A panel moving to where it has to look, rather than arriving there.
     *
     * <p>Setting the value outright is a jump in the middle of everything else that is easing, and
     * reads as the animation stuttering rather than as the panel scrolling.
     */
    public static void scrollTo(ScrollPane pane, double vvalue) {
        stop(pane);
        play(pane, new Timeline(new KeyFrame(OPEN,
            new KeyValue(pane.vvalueProperty(), vvalue, Interpolator.EASE_BOTH))));
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

    private static void play(Node node, Animation animation) {
        node.getProperties().put(RUNNING, animation);
        animation.play();
    }

    private static void stop(Node node) {
        if (node.getProperties().remove(RUNNING) instanceof Animation running) {
            running.stop();
        }
    }
}
