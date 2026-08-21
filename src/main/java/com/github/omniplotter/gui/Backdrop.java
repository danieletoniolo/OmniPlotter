package com.github.omniplotter.gui;

import com.github.omniplotter.app.Settings;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * The soft ground the glass surfaces sit on.
 *
 * <p>This is not decoration. JavaFX has no {@code backdrop-filter}, so a translucent panel cannot
 * blur what is behind it; the only way a semi-transparent fill reads as glass rather than as a
 * hole is if whatever shows through is already smooth. Two wide colour washes give exactly that,
 * and cost nothing to look at.
 *
 * <p>They are plain {@link Circle}s filled from the stylesheet with a radial gradient that fades to
 * transparent — not a blurred shape. A gradient is already perfectly soft, while {@code
 * GaussianBlur} stops at radius 63 and is recomposited on every frame it appears in. Keeping the
 * colour in CSS also means the blobs follow the light/dark switch along with everything else.
 */
public class Backdrop extends Pane {

    private final Circle a = new Circle();
    private final Circle b = new Circle();
    private final Timeline drift = buildDrift();

    /** The user's preference, and whether there is anyone in front of the window to see it. */
    private boolean enabled = Settings.getBoolean(Settings.UI_AURORA, true);
    private boolean awake = true;

    public Backdrop() {
        getStyleClass().add("backdrop");
        a.getStyleClass().addAll("aurora", "aurora-a");
        b.getStyleClass().addAll("aurora", "aurora-b");
        getChildren().addAll(a, b);

        // Nothing here is interactive, and the panels above must receive every event.
        setMouseTransparent(true);

        // The washes are deliberately larger than the window so their edges never enter it. Without
        // a clip they would still be painted in full and thrown away by the window, which is work
        // for no pixels.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        sync();
    }

    /** The setting, which survives a restart. */
    public void setEnabled(boolean on) {
        enabled = on;
        sync();
    }

    /**
     * Whether the window is in front of someone.
     *
     * <p>An animation that never ends is a repaint that never ends. Ninety seconds of travel is
     * cheap to watch and pointless to compute behind another window or in the dock.
     */
    public void setAwake(boolean on) {
        awake = on;
        sync();
    }

    private void sync() {
        if (!enabled) {
            // Back to the composition the static layout was designed around, not wherever the
            // drift had got to when it was switched off.
            drift.stop();
            a.setTranslateX(0);
            a.setTranslateY(0);
            b.setTranslateX(0);
            b.setTranslateY(0);
        } else if (awake) {
            drift.play();
        } else {
            drift.pause();
        }
    }

    /**
     * Slow enough to be noticed only by not being noticed. The two washes are given different
     * distances and opposite directions, because equal ones read as the whole background sliding
     * rather than as two things drifting.
     */
    private Timeline buildDrift() {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(a.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(a.translateYProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(b.translateXProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(b.translateYProperty(), 0, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.seconds(46),
                new KeyValue(a.translateXProperty(), 92, Interpolator.EASE_BOTH),
                new KeyValue(a.translateYProperty(), 56, Interpolator.EASE_BOTH),
                new KeyValue(b.translateXProperty(), -74, Interpolator.EASE_BOTH),
                new KeyValue(b.translateYProperty(), -98, Interpolator.EASE_BOTH)));
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    /**
     * Positions the washes in fractions of the pane, so they follow a resize instead of drifting
     * into a corner. Sized off the larger dimension: on a wide window a wash scaled to the height
     * would read as a spot rather than a wash.
     */
    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        double base = Math.max(w, h);

        a.setRadius(base * 0.55);
        a.setCenterX(w * 0.16);
        a.setCenterY(h * 0.08);

        b.setRadius(base * 0.48);
        b.setCenterX(w * 0.90);
        b.setCenterY(h * 0.94);
    }
}
