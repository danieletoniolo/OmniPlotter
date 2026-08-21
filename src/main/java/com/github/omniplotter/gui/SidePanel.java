package com.github.omniplotter.gui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * A side panel that folds down to a rail.
 *
 * <p>{@code BorderPane} gives its sides their preferred width and the centre whatever is left, so
 * making the window smaller does not touch these panels at all — it takes the space out of the
 * previews, which are the one thing the window exists to show. At the 900px minimum that leaves
 * about eighty pixels of actual image per card. Letting the panels shrink to their own minimum only
 * gets that to a hundred and thirty; folding them away is what makes the difference.
 *
 * <p>It folds to a rail rather than to nothing. The rail costs 44px and keeps two things worth
 * keeping: the icon that says which panel this is, and — for the queue — the buttons you carry on
 * using after the images are loaded. It also means reopening is a click on something already on
 * screen, instead of a tab that has to be invented and floated against the window edge.
 */
public class SidePanel extends StackPane {

    /** One icon button and its padding. */
    public static final double RAIL_WIDTH = 44;

    private static final Duration FOLD = Duration.millis(220);

    /**
     * Carries the clip. It cannot go on the panel itself: a clip applies to the drop shadow too,
     * and would cut off the very thing that makes the surface look like it is floating.
     */
    private final StackPane viewport = new StackPane();

    private final Node content;
    private final Node rail;
    private final double openWidth;

    private Timeline folding;
    private boolean collapsed;

    public SidePanel(Node content, Node rail, double openWidth, Pos anchor) {
        this.content = content;
        this.rail = rail;
        this.openWidth = openWidth;

        getStyleClass().add("glass");

        // Both children keep their own width while the panel around them narrows, so what happens
        // is the panel closing over them rather than the controls inside being squashed.
        pin(content, openWidth);
        pin(rail, RAIL_WIDTH);

        StackPane.setAlignment(content, anchor);
        StackPane.setAlignment(rail, anchor);
        viewport.getChildren().addAll(content, rail);
        viewport.setMinWidth(0);
        getChildren().add(viewport);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);

        rail.setVisible(false);
        rail.setOpacity(0);

        // The preferred width is what BorderPane reads, and it is clamped by the minimum: without
        // this the panel would refuse to fold past whatever its contents want to be.
        setMinWidth(0);
        setPrefWidth(openWidth);
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void toggle() {
        setCollapsed(!collapsed, true);
    }

    /**
     * @param animate false when restoring the state from a previous run, where the fold is not
     *                something the user just did and should not be played back at them
     */
    public void setCollapsed(boolean collapse, boolean animate) {
        collapsed = collapse;
        double target = collapse ? RAIL_WIDTH : openWidth;

        if (folding != null) {
            folding.stop();
        }

        if (!animate) {
            show(content, !collapse);
            show(rail, collapse);
            setPrefWidth(target);
            return;
        }

        Animations.reveal(content, !collapse);
        Animations.reveal(rail, collapse);

        folding = new Timeline(new KeyFrame(FOLD,
            new KeyValue(prefWidthProperty(), target, Interpolator.EASE_BOTH)));
        folding.play();
    }

    private static void pin(Node node, double width) {
        if (node instanceof Region region) {
            region.setMinWidth(width);
            region.setPrefWidth(width);
            region.setMaxWidth(width);
        }
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setOpacity(visible ? 1 : 0);
    }
}
