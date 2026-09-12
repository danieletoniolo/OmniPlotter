package com.github.omniplotter.gui;

import atlantafx.base.theme.Styles;
import com.github.omniplotter.app.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * One stop on the output panel's timeline.
 *
 * <p>The panel was a single column of labelled controls in the order the code happened to build
 * them, with the whole document side of the application folded away inside a section called
 * "Image". Numbering it helped and left one fault behind: step 4 was the only one that came and
 * went, and a hole at 4 makes the rest look like five unrelated things with one missing.
 *
 * <p>So nothing is ever removed. Every step keeps its place on a rail, and what changes is whether
 * it is <b>open</b> or <b>waiting</b> — waiting being an outlined badge and a dimmed heading, with
 * the reason underneath where there is one to give. The rail runs down to the last open step,
 * through any dim badge on the way, because a stop you are skipping is still on the line.
 *
 * <p>Each step draws the segment from its own badge to the next one, so the segments meet without
 * anything having to know how many steps there are. That also means the vertical gap between steps
 * is carried by the content column rather than by the parent's spacing: spacing would break the
 * line into pieces with a gap at every boundary.
 */
public class StepSection extends HBox {

    /** Wide enough for the badge, with the rail down its middle. */
    private static final double GUTTER = 26;

    /** Between one step and the next, carried inside the step so the rail can span it. */
    private static final double BELOW = 18;

    private static final double DIMMED = 0.5;

    private final Label badge = new Label();
    private final Label title = new Label();
    private final HBox heading;
    private final VBox content = new VBox(6);
    private final VBox body = new VBox(10);

    /** Everything under the heading: what opens and closes, as one thing with one height. */
    private final VBox collapsible = new VBox(6);

    /** The segment from this badge down to the next one. */
    private final Region rail = new Region();

    /** Pivoted at the top, so driving its {@code y} draws the segment downward. */
    private final Scale draw = new Scale(1, 1, 0, 0);

    /** The line shown when the step is open, and the one shown while it waits. */
    private Label says;
    private Label because;

    /** Every step that applies carries one, which is what says they open and close at all. */
    private final Button chevron = new Button(null, new FontIcon(Feather.CHEVRON_DOWN));

    /** Which of the parts above are on screen. Kept out of the nodes so it can be tested. */
    private final StepState state = new StepState(false);

    /** Told to the panel, which closes whatever else is open. One step decides for none. */
    private Runnable onOpen = () -> { };

    public StepSection(int number, String titleText, String subtitle) {
        super(10);

        badge.setText(String.valueOf(number));
        badge.getStyleClass().add("step-number");

        rail.getStyleClass().add("step-rail");
        rail.getTransforms().add(draw);
        VBox.setVgrow(rail, Priority.ALWAYS);

        VBox gutter = new VBox(4, badge, rail);
        gutter.setAlignment(Pos.TOP_CENTER);
        gutter.setMinWidth(GUTTER);
        gutter.setPrefWidth(GUTTER);
        gutter.setMaxWidth(GUTTER);

        title.setText(titleText);
        title.getStyleClass().add("step-title");

        chevron.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        // Through the action, so the keyboard reaches it too. The click is then stopped here:
        // Button does not consume MOUSE_CLICKED, so it would go on to the heading below and
        // toggle a second time, which looks exactly like nothing happening.
        // Asking rather than doing: the panel is what knows which step is open.
        chevron.setOnAction(e -> askToOpen());
        chevron.setOnMouseClicked(MouseEvent::consume);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        heading = new HBox(8, title, spacer, chevron);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("step-heading");
        // The whole heading, not just the chevron: a 20-pixel glyph is a small thing to find and
        // a smaller one to hit. The cursor follows what a click would actually do.
        heading.setOnMouseClicked(e -> askToOpen());

        if (subtitle != null) {
            says = new Label(subtitle);
            says.getStyleClass().add("step-subtitle");
            says.setWrapText(true);
            collapsible.getChildren().add(says);
        }
        collapsible.getChildren().add(body);

        // Clipped, or the contents spill out of it on the way down. The clip goes on the box whose
        // height is being driven, which is the same reason SidePanel puts its own on a wrapper.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(collapsible.widthProperty());
        clip.heightProperty().bind(collapsible.heightProperty());
        collapsible.setClip(clip);

        content.getChildren().addAll(heading, collapsible);
        content.setPadding(new Insets(0, 0, BELOW, 0));
        HBox.setHgrow(content, Priority.ALWAYS);

        getStyleClass().add("step");
        getChildren().addAll(gutter, content);

        apply();
        showCollapsible(false, false);
    }

    /** What to do when a click on this step asks for it to be the open one. */
    public void setOnOpen(Runnable action) {
        onOpen = action;
    }

    public StepSection with(Node... controls) {
        body.getChildren().addAll(controls);
        return this;
    }

    /** The line to show while this step is waiting, for the one where the reason is not obvious. */
    public StepSection because(String text) {
        because = new Label(text);
        because.getStyleClass().add("step-subtitle");
        because.setWrapText(true);
        // Above the part that opens and closes, not inside it: this line is what a step says
        // while it is shut. Located by the collapsible, since the body is no longer a child here.
        content.getChildren().add(content.getChildren().indexOf(collapsible), because);
        apply();
        return this;
    }

    /** A step that is already open stays open; there is no gesture here that closes all five. */
    private void askToOpen() {
        if (!state.isExpanded() && state.clickable()) {
            onOpen.run();
        }
    }

    public void setWaiting(boolean wait) {
        if (state.isWaiting() == wait) {
            return;
        }
        boolean wasOpen = state.isExpanded();
        state.setWaiting(wait);
        apply();
        if (wasOpen && !state.isExpanded()) {
            showCollapsible(false, true);
        }
    }

    public boolean isWaiting() {
        return state.isWaiting();
    }

    /** Whether a step that does not apply has anything to explain itself against. */
    public void setExplaining(boolean owed) {
        state.setExplaining(owed);
        apply();
    }

    public boolean isExpanded() {
        return state.isExpanded();
    }

    public void setExpanded(boolean open, boolean animate) {
        if (state.isExpanded() == open) {
            return;
        }
        state.setExpanded(open);
        apply();
        showCollapsible(state.isExpanded(), animate);
    }

    /**
     * Opens or closes the part under the heading.
     *
     * <p><b>A box will not go below the sum of its children's minimums.</b> That was the whole
     * fault: a column of sliders and a wrapped label reports a minimum of its own full height, so
     * driving the preferred height down to zero did nothing at all. It sat at full size for most
     * of the animation and then moved in the last few frames, which is what a jump is. Clearing
     * the minimum for the duration is what lets it close at all.
     *
     * <p>The target is what the box asks for at the width it is going to get, which is the height
     * the layout then settles on — checked against a real layout pass rather than assumed, because
     * a target that is out by even a few pixels snaps when the height is handed back.
     */
    private void showCollapsible(boolean open, boolean animate) {
        if (!animate) {
            release();
            show(collapsible, open);
            return;
        }
        if (open) {
            show(collapsible, true);
            double target = collapsible.prefHeight(width());

            collapsible.setMinHeight(0);
            collapsible.setPrefHeight(0);
            Animations.height(collapsible, target, this::release);
        } else {
            // From where it actually is: USE_COMPUTED_SIZE is -1, and animating from that would
            // take the whole thing away in the first frame.
            collapsible.setMinHeight(0);
            collapsible.setPrefHeight(collapsible.getHeight());
            Animations.height(collapsible, 0, () -> {
                show(collapsible, false);
                release();
            });
        }
    }

    /**
     * Hands the height back to the layout.
     *
     * <p>Left pinned at what it measured, a step whose contents change later — the line about the
     * crop appearing under a grid, the tile controls arriving in step 4 — would be cut off at the
     * height it had when it opened.
     */
    private void release() {
        collapsible.setMinHeight(Region.USE_COMPUTED_SIZE);
        collapsible.setPrefHeight(Region.USE_COMPUTED_SIZE);
    }

    /**
     * The width to measure the height against.
     *
     * <p>Its own, once it has been laid out at least once, and the column it sits in before that —
     * they are the same number, since nothing between them has horizontal padding.
     */
    private double width() {
        double laid = collapsible.getWidth() > 0 ? collapsible.getWidth() : content.getWidth();
        return laid > 0 ? laid : -1;
    }

    /** One place where the state decides what is on screen. */
    private void apply() {
        // The body and the line above it are inside the collapsible, which carries their
        // visibility as its height. The reason sits outside it, since it is shown when it is shut.
        show(because, state.reasonShown());

        // In Java rather than in the stylesheet: the arrival animation drives this same property,
        // and a CSS opacity would win back over it on the next style pass.
        //
        // Putting the offset back matters as much. The arrival rises the last few pixels, and a
        // cascade interrupted part-way — another file landing while it runs — would otherwise
        // leave a step sitting twelve pixels off for good. Every recompute passes through here
        // before anything is animated again, so this is where it gets undone.
        content.setOpacity(state.dimmed() ? DIMMED : 1);
        content.setTranslateY(0);

        badge.getStyleClass().remove("waiting");
        if (state.isWaiting()) {
            badge.getStyleClass().add("waiting");
        }

        heading.setCursor(state.clickable() ? Cursor.HAND : Cursor.DEFAULT);

        show(chevron, state.chevronShown());
        boolean open = state.isExpanded();
        chevron.setGraphic(new FontIcon(open ? Feather.CHEVRON_DOWN : Feather.CHEVRON_RIGHT));
        chevron.setTooltip(new Tooltip(Messages.get(open ? "step.hide" : "step.show")));
    }

    private static void show(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    // --- the rail ---------------------------------------------------------------------------

    /** The segment from this badge to the next, for {@link Animations#timeline}. */
    public Scale railScale() {
        return draw;
    }

    /** Where the rail stands when there is nothing to animate. */
    public void setRailDrawn(boolean drawn) {
        draw.setY(drawn ? 1 : 0);
    }

    /** There is nothing below the last step, so it carries no segment. */
    public void endRail() {
        show(rail, false);
    }

    /** What the arrival animation fades in: everything but the badge and the rail. */
    public Node arriving() {
        return content;
    }
}
