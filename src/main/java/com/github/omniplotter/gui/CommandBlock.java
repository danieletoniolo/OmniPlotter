package com.github.omniplotter.gui;

import atlantafx.base.theme.Styles;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A command the window is proposing, in a form it can be taken out of.
 *
 * <p>These lines used to sit in a dialog's content text, which JavaFX renders as a {@code Label}:
 * no selection, no caret, nothing the clipboard can reach. A line with a home directory in it then
 * has to be retyped by hand, exactly, which is how one instruction becomes a chore and a typo.
 *
 * <p>A read-only {@code TextField} rather than a {@code Label} for that reason, and monospaced
 * because a command is read character by character. It never wraps: a path broken across two lines
 * is a path someone reassembles wrongly.
 */
public class CommandBlock extends HBox {

    private static final Duration CONFIRMED = Duration.seconds(1.6);

    private final String command;

    public CommandBlock(String command) {
        super(6);
        this.command = command;

        TextField field = new TextField(command);
        field.setEditable(false);
        field.getStyleClass().add("code");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button copy = new Button(null, new FontIcon(Feather.COPY));
        copy.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        copy.setTooltip(new Tooltip("Copy"));
        copy.setOnAction(e -> {
            copyToClipboard();
            confirm(copy);
        });

        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("code-block");
        getChildren().addAll(field, copy);
    }

    private void copyToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString(command);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Says it happened. Copying is otherwise completely silent, and so indistinguishable from a
     *  click that missed. */
    private void confirm(Button copy) {
        copy.setGraphic(new FontIcon(Feather.CHECK));
        copy.setTooltip(new Tooltip("Copied"));

        PauseTransition back = new PauseTransition(CONFIRMED);
        back.setOnFinished(e -> {
            copy.setGraphic(new FontIcon(Feather.COPY));
            copy.setTooltip(new Tooltip("Copy"));
        });
        back.play();
    }
}
