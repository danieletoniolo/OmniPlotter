package com.github.omniplotter.gui;

import atlantafx.base.theme.Styles;
import com.github.omniplotter.app.Messages;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

import java.util.List;

/**
 * What the window is, said over the window itself.
 *
 * <p>Two things behind one button, because they answer different questions. The tour answers "what
 * am I looking at", and it has to be said on top of the real thing rather than in a picture of it:
 * a panel described in prose is a panel you then have to go and find. The concepts answer "what is
 * a slot, what is a tile, why is a format called cp.g3p" — which no callout carries in one line.
 *
 * <p>The light is a hole cut in the veil rather than a ring drawn around the region, so what is
 * being talked about is the only thing at full brightness. Positions are read from the live bounds
 * on every layout pass, so folding a panel or resizing the window while the tour is open moves the
 * light with it instead of leaving it behind.
 */
public class HelpOverlay extends Pane {

    /** Space between a highlighted region and the card talking about it. */
    private static final double GAP = 16;

    private static final double CARD_WIDTH = 340;

    /** One stop on the tour: a region of the window, and the key for what it is for. */
    public record Spot(Node target, String key) {}

    private final List<Spot> spots;

    /** Covers everything; a hole is cut in it by clipping, which keeps it one node. */
    private final Rectangle veil = new Rectangle();

    private final Label cardNumber = new Label();
    private final Label cardTitle = new Label();
    private final Label cardBody = new Label();
    private final Button back = new Button(Messages.get("help.back"));
    private final Button next = new Button(Messages.get("help.next"));
    private final VBox card = new VBox(10);

    private final ScrollPane concepts;

    private int at = -1;

    /** The hole last cut, so a layout pass that changed nothing does not cut it again. */
    private Bounds cut;

    public HelpOverlay(List<Spot> spots, Node footer) {
        this.spots = List.copyOf(spots);

        veil.getStyleClass().add("help-veil");
        buildCard();
        concepts = buildConcepts(footer);

        // Positioned by hand in layoutChildren, so the Pane must not also have an opinion.
        for (Node child : new Node[] {veil, card, concepts}) {
            child.setManaged(false);
        }
        getChildren().addAll(veil, card, concepts);

        setVisible(false);
        // The overlay swallows everything while it is up: the window behind it is being explained,
        // not operated. A click anywhere off the card is a way out.
        setPickOnBounds(true);
        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (!hits(card, e) && !hits(concepts, e)) {
                hide();
            }
        });

        // Not focusable, so Escape has to be caught where the keys actually arrive.
        sceneProperty().addListener((o, was, now) -> {
            if (now != null) {
                now.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (isVisible() && e.getCode() == KeyCode.ESCAPE) {
                        hide();
                        e.consume();
                    }
                });
            }
        });
    }

    // --- the tour ---------------------------------------------------------------------------

    private void buildCard() {
        cardNumber.getStyleClass().add("step-number");
        cardTitle.getStyleClass().add("step-title");
        cardBody.setWrapText(true);

        HBox heading = new HBox(8, cardNumber, cardTitle);
        heading.setAlignment(Pos.CENTER_LEFT);

        Button skip = new Button(Messages.get("help.skip"));
        skip.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
        skip.setOnAction(e -> hide());

        back.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
        back.setOnAction(e -> go(at - 1));

        next.getStyleClass().addAll(Styles.ACCENT, Styles.SMALL);
        next.setOnAction(e -> go(at + 1));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(6, skip, spacer, back, next);
        buttons.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(heading, cardBody, buttons);
        card.getStyleClass().addAll("glass", "help-card");
        card.setPadding(new Insets(16));
        card.setVisible(false);
    }

    public void startTour() {
        setVisible(true);
        concepts.setVisible(false);
        go(0);
    }

    private void go(int index) {
        if (index < 0) {
            return;
        }
        if (index >= spots.size()) {
            hide();
            return;
        }
        at = index;
        Spot spot = spots.get(index);

        cardNumber.setText(String.valueOf(index + 1));
        cardTitle.setText(Messages.get("help.tour." + spot.key()));
        cardBody.setText(Messages.get("help.tour." + spot.key() + ".says"));
        back.setDisable(index == 0);
        next.setText(Messages.get(index == spots.size() - 1 ? "help.done" : "help.next"));

        card.setVisible(true);
        requestLayout();
        place();
    }

    // --- the concepts -----------------------------------------------------------------------

    private ScrollPane buildConcepts(Node footer) {
        Label title = new Label(Messages.get("help.concepts"));
        title.getStyleClass().add(Styles.TITLE_4);

        Button tour = new Button(Messages.get("help.tour.start"));
        tour.getStyleClass().addAll(Styles.ACCENT, Styles.SMALL);
        tour.setOnAction(e -> startTour());

        VBox body = new VBox(16, title, tour);
        body.setPadding(new Insets(20));

        for (String key : List.of("what", "mode", "format", "canvas", "name", "tiles", "logs")) {
            Label heading = new Label(Messages.get("help.concept." + key));
            heading.getStyleClass().add("step-title");

            Label says = new Label(Messages.get("help.concept." + key + ".says"));
            says.setWrapText(true);
            says.getStyleClass().add(Styles.TEXT_MUTED);

            body.getChildren().add(new VBox(4, heading, says));
        }

        Button close = new Button(Messages.get("help.close"));
        close.getStyleClass().add(Styles.FLAT);
        close.setOnAction(e -> hide());
        body.getChildren().addAll(footer, close);

        ScrollPane scroll = new ScrollPane(body);
        scroll.getStyleClass().addAll("glass", "help-panel");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVisible(false);
        return scroll;
    }

    public void showConcepts() {
        setVisible(true);
        card.setVisible(false);
        concepts.setVisible(true);
        requestLayout();
        place();
    }

    public void hide() {
        setVisible(false);
        card.setVisible(false);
        concepts.setVisible(false);
        at = -1;
    }

    // --- placing it -------------------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        place();
    }

    private void place() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        boolean resized = veil.getWidth() != width || veil.getHeight() != height;
        veil.setWidth(width);
        veil.setHeight(height);
        if (resized) {
            cut = null;   // the hole was subtracted from a rectangle that is no longer this size
        }

        Bounds lit = card.isVisible() && at >= 0 ? boundsOf(spots.get(at).target()) : null;
        if (lit == null) {
            veil.setClip(null);
            cut = null;
            if (concepts.isVisible()) {
                placeConcepts(width, height);
            }
            return;
        }

        // Folding a panel animates its width, so this runs every frame for as long as that lasts.
        // Cutting the same hole again on each of them is work with nothing to show for it.
        if (!lit.equals(cut)) {
            // A clip of "everything except the region" rather than a second node over the top: the
            // veil keeps its style class and there is only ever one thing to click on.
            Rectangle hole = new Rectangle(lit.getMinX(), lit.getMinY(),
                lit.getWidth(), lit.getHeight());
            hole.setArcWidth(32);
            hole.setArcHeight(32);
            veil.setClip(Shape.subtract(new Rectangle(width, height), hole));
            cut = lit;
        }

        placeCard(lit, width, height);
    }

    private void placeCard(Bounds lit, double width, double height) {
        double cardHeight = card.prefHeight(CARD_WIDTH);

        double x;
        if (width - lit.getMaxX() >= CARD_WIDTH + 2 * GAP) {
            x = lit.getMaxX() + GAP;                       // room to the right of it
        } else if (lit.getMinX() >= CARD_WIDTH + 2 * GAP) {
            x = lit.getMinX() - CARD_WIDTH - GAP;          // room to the left
        } else {
            x = (width - CARD_WIDTH) / 2;                  // neither, so over the middle of it
        }

        double y = lit.getMinY();
        if (y + cardHeight > height - GAP) {
            // Above rather than hanging off the bottom, which is where the action bar is.
            y = lit.getMinY() - cardHeight - GAP;
        }
        card.resizeRelocate(clamp(x, GAP, width - CARD_WIDTH - GAP),
            clamp(y, GAP, Math.max(GAP, height - cardHeight - GAP)), CARD_WIDTH, cardHeight);
    }

    private void placeConcepts(double width, double height) {
        double w = Math.min(540, width - 4 * GAP);
        double h = Math.min(600, height - 4 * GAP);
        concepts.resizeRelocate((width - w) / 2, (height - h) / 2, w, h);
    }

    /** The region's rectangle in this overlay's own coordinates. */
    private Bounds boundsOf(Node target) {
        if (target == null || target.getScene() == null) {
            return null;
        }
        return sceneToLocal(target.localToScene(target.getBoundsInLocal()));
    }

    private static boolean hits(Node node, MouseEvent event) {
        return node.isVisible()
            && node.localToScene(node.getBoundsInLocal())
                .contains(event.getSceneX(), event.getSceneY());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
