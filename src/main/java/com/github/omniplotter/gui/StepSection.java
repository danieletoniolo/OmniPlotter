package com.github.omniplotter.gui;

import atlantafx.base.theme.Styles;
import com.github.omniplotter.app.Messages;
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
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * One numbered step of the output panel.
 *
 * <p>The panel was a single column of labelled controls in the order the code happened to build
 * them, with the whole document side of the application folded away inside a section called
 * "Image". Which of them a conversion needs, and in what order, was something to work out by
 * reading all of it.
 *
 * <p>The number is the point. It says there is an order, that this is a place in it, and — for the
 * steps that appear and disappear with the file being converted — that nothing is missing when a
 * number is absent.
 */
public class StepSection extends VBox {

    private final VBox body = new VBox(10);

    /**
     * Whether the body is away.
     *
     * <p>Held rather than read back off {@code body.isVisible()}. Asking the node meant the
     * handler said {@code !isVisible()} — which reads like "toggle" and means "fold" when it is
     * already folded, so a closed step could not be opened at all.
     */
    private boolean folded;

    public StepSection(int number, String title, String subtitle) {
        super(8);

        Label badge = new Label(String.valueOf(number));
        badge.getStyleClass().add("step-number");

        Label name = new Label(title);
        name.getStyleClass().add("step-title");

        HBox heading = new HBox(8, badge, name);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("step-heading");

        getStyleClass().add("step");
        getChildren().add(heading);

        // A step whose one-line reason is obvious does not need the line.
        if (subtitle != null) {
            Label says = new Label(subtitle);
            says.getStyleClass().add("step-subtitle");
            says.setWrapText(true);
            getChildren().add(says);
        }

        getChildren().add(body);
    }

    public StepSection with(Node... controls) {
        body.getChildren().addAll(controls);
        return this;
    }

    /**
     * Adds the chevron that folds this step away.
     *
     * <p>Only for a step nothing breaks without: the picture adjustments, which most conversions
     * leave alone. A step that has to be filled in is not one to hide.
     */
    public StepSection foldable(boolean startFolded) {
        Button fold = new Button(null, new FontIcon(Feather.CHEVRON_DOWN));
        fold.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = (HBox) getChildren().get(0);
        heading.getChildren().addAll(spacer, fold);

        // Through the action, so the keyboard reaches it too. The click is then stopped here:
        // Button does not consume MOUSE_CLICKED, so it would go on to the heading below and
        // toggle a second time, which looks exactly like nothing happening.
        fold.setOnAction(e -> setFolded(!folded, fold));
        fold.setOnMouseClicked(MouseEvent::consume);

        // The whole heading, not just the chevron. A 20-pixel glyph is a small thing to find and
        // a smaller one to hit, and the title beside it looks like it should work.
        heading.setCursor(Cursor.HAND);
        heading.setOnMouseClicked(e -> setFolded(!folded, fold));

        setFolded(startFolded, fold);
        return this;
    }

    private void setFolded(boolean folded, Button fold) {
        this.folded = folded;
        body.setVisible(!folded);
        body.setManaged(!folded);
        // Everything between the heading and the body goes with it, which is the subtitle.
        for (int i = 1; i < getChildren().size() - 1; i++) {
            getChildren().get(i).setVisible(!folded);
            getChildren().get(i).setManaged(!folded);
        }
        fold.setGraphic(new FontIcon(folded ? Feather.CHEVRON_DOWN : Feather.CHEVRON_UP));
        fold.setTooltip(new Tooltip(Messages.get(folded ? "step.show" : "step.hide")));
    }

    /** Whether this step is in the panel at all, for the ones that depend on the file. */
    public void setShown(boolean shown) {
        setVisible(shown);
        setManaged(shown);
    }
}
