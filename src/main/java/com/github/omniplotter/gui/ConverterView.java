package com.github.omniplotter.gui;

import atlantafx.base.controls.RingProgressIndicator;
import atlantafx.base.theme.Styles;
import com.github.omniplotter.app.AppPaths;
import com.github.omniplotter.app.CommandSetup;
import com.github.omniplotter.app.Desktops;
import com.github.omniplotter.app.Settings;
import com.github.omniplotter.app.UpdateCheck;
import com.github.omniplotter.engine.data.Adjustments;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.Crop;
import com.github.omniplotter.engine.data.Dither;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.FormatConfig;
import com.github.omniplotter.engine.data.Framing;
import com.github.omniplotter.engine.data.Look;
import com.github.omniplotter.engine.data.PageSize;
import com.github.omniplotter.engine.data.Tile;
import com.github.omniplotter.engine.data.TileGrid;
import com.github.omniplotter.engine.data.Tiling;
import com.github.omniplotter.engine.data.Mode;
import com.github.omniplotter.engine.data.OnCalcName;
import com.github.omniplotter.engine.data.OutputLimits;
import com.github.omniplotter.engine.data.Target;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The single window: a queue on the left, source and preview in the middle, settings on the right.
 *
 * <p>The preview is the actual preprocessed image — the same pixels the encoder will consume — so
 * quantisation, palette remapping and dithering are visible before anything is written. Showing a
 * plain scaled-down source instead would hide exactly the part users need to judge.
 *
 * <p>The four regions float as separate translucent surfaces over a {@link Backdrop} rather than
 * being docked edge to edge, which is why this is a {@code StackPane} wrapping a {@code BorderPane}
 * instead of being one: the layout is still the border layout, but it now needs something painted
 * underneath it and gaps for that something to show through.
 */
public class ConverterView extends StackPane {

    /** Space between the floating surfaces, and between them and the window edge. */
    private static final double GAP = 14;

    private final Stage stage;

    private final Backdrop backdrop = new Backdrop();
    private final BorderPane content = new BorderPane();
    private final StackPane dropOverlay = buildDropOverlay();

    /** The floating surfaces, in the order they arrive on screen. */
    private Node[] surfaces = new Node[0];

    private Node sourcePane;
    private Node previewPane;

    private SidePanel queueSide;
    private SidePanel settingsSide;

    private final ObservableList<ConversionJob> jobs = FXCollections.observableArrayList();
    private final ListView<ConversionJob> queue = new ListView<>(jobs);

    private final ComboBox<Mode> modeBox = new ComboBox<>();
    private final ComboBox<Target> targetBox = new ComboBox<>();
    private final ComboBox<Format> formatBox = new ComboBox<>();
    private final Spinner<Integer> widthSpinner = new Spinner<>(1, 4096, 384);
    private final Spinner<Integer> heightSpinner = new Spinner<>(1, 4096, 192);
    private final Spinner<Integer> colorsSpinner = new Spinner<>(1, 1 << 24, 65536);
    private final FlowPane presetChips = new FlowPane(6, 6);
    private final CheckBox keepRatio = new CheckBox("Keep aspect ratio");
    private final CheckBox enlargeSmaller = new CheckBox("Enlarge smaller images");
    private final Slider brightness = new Slider(-100, 100, 0);
    private final Slider contrast = new Slider(-100, 100, 0);
    private final Slider gamma = new Slider(0.2, 3.0, 1.0);
    private final Slider saturation = new Slider(-100, 100, 0);
    private final Slider sharpen = new Slider(0, 3, 0);
    private final ComboBox<Dither> ditherBox = new ComboBox<>();
    private final FlowPane lookChips = new FlowPane(6, 6);
    private final CheckBox fill = new CheckBox("Fill the canvas");
    private final ToggleButton cropToggle = new ToggleButton("Crop");
    private CropOverlay cropOverlay;
    private GridOverlay gridOverlay;

    private final ComboBox<TileGrid> gridBox = new ComboBox<>();
    private final Label tileCount = new Label();
    private final Label tileLabel = new Label();
    private final Button previousTile = new Button("\u2039");
    private final Button nextTile = new Button("\u203a");
    private final Button excludeTile = new Button("Exclude this tile");
    private final Button includeAll = new Button("Include all");
    private VBox tileControls;
    private final Label pageLabel = new Label();
    private final Button previousPage = new Button("\u2039");
    private final Button nextPage = new Button("\u203a");
    /** The piece the preview is showing, so clicking a cell shows what is in it. */
    private Tile focusedTile;
    /**
     * The job the overlay is currently drawing on.
     *
     * <p>Held rather than read from the selection when the rectangle changes: the callback arrives
     * on mouse release, and reading the selection at that moment is a promise that it has not moved
     * since the drag began.
     */
    private ConversionJob croppingJob;

    /**
     * The image the crop tool was switched on for.
     *
     * <p>The tool follows this one image rather than the selection or the presence of a rectangle.
     * Following the selection makes it turn up on images nobody asked to crop; following the
     * rectangle makes it turn itself on again every time a finished crop is revisited. Following
     * the image it was opened on means coming back to what you were doing finds it as you left it,
     * and nothing else does.
     */
    private ConversionJob armedFor;

    /** True while the toggle is being set to match the selection rather than by the user. */
    private boolean restoringCropTool;

    private final TextField onCalcName = new TextField();
    private final Spinner<Integer> onCalcNumber = new Spinner<>(0, 9, 1);
    private final Label onCalcHint = new Label();

    private final ImageView sourceView = new ImageView();
    private final ImageView previewView = new ImageView();
    private final Label sourceCaption = new Label("No image selected");
    private final Label previewCaption = new Label();
    private final Label sizeWarning = new Label();

    private final Label outputLabel = new Label();
    private final RingProgressIndicator progress = new RingProgressIndicator(0);
    private final Label status = new Label("Drop images to begin.");
    private final Button convertButton = new Button("Convert");

    private Path outputDir =
        Settings.getDirectory(Settings.OUTPUT_DIR, Path.of(System.getProperty("user.home"), "Desktop"));
    /** Suppresses preview refreshes while the format cascade is being rebuilt. */
    private boolean updating;
    private Task<?> previewTask;
    /**
     * Generation counter for preview requests.
     *
     * <p>Changing the mode cascades into target and format, firing several requests in a row, and
     * cancelling a running task does not stop it delivering. Without this, a superseded task can
     * finish last and leave the caption describing a format that is no longer selected.
     */
    private long previewGeneration;

    public ConverterView(Stage stage) {
        this.stage = stage;
        getStyleClass().add("converter-view");

        Node queueBox = buildQueuePanel();
        Node previews = buildPreviewPanel();
        Node settingsBox = buildSettingsPanel();
        Node dock = buildActionBar();

        content.setLeft(queueBox);
        content.setCenter(previews);
        content.setRight(settingsBox);
        content.setBottom(dock);

        // The gaps are the only thing separating the surfaces now that the 1px rules are gone, so
        // they have to be even. Each region carries the space on the sides it owns, and it is the
        // bottom margins of the row above that hold the dock clear of it.
        BorderPane.setMargin(queueBox, new Insets(GAP, 0, GAP, GAP));
        BorderPane.setMargin(previews, new Insets(GAP, 0, GAP, GAP));
        BorderPane.setMargin(settingsBox, new Insets(GAP, GAP, GAP, GAP));
        BorderPane.setMargin(dock, new Insets(0, GAP, GAP, GAP));

        getChildren().addAll(backdrop, content, dropOverlay);
        surfaces = new Node[] { queueBox, sourcePane, previewPane, settingsBox, dock };

        // Restored without animation: a fold the user made last week is not news to play back.
        queueSide.setCollapsed(Settings.getBoolean(Settings.UI_QUEUE_FOLDED, false), false);
        settingsSide.setCollapsed(Settings.getBoolean(Settings.UI_SETTINGS_FOLDED, false), false);

        // The washes only travel while there is someone in front of the window to see them.
        stage.focusedProperty().addListener((o, was, now) -> backdrop.setAwake(now));
        stage.iconifiedProperty().addListener((o, was, now) -> backdrop.setAwake(!now));

        wireCascade();
        wireDragAndDrop();

        restoreSelection();
        updateOutputLabel();
        checkForUpdate();
    }

    // --- queue ------------------------------------------------------------------------------

    private Node buildQueuePanel() {
        Node title = sectionTitle("Images", Feather.LAYERS, Feather.CHEVRON_LEFT,
            () -> queueSide.setCollapsed(true, true));

        // Named so the stylesheet can clear this list without also clearing the one inside every
        // ComboBox dropdown, which shares its style class and inherits from the same parent chain.
        queue.getStyleClass().add("queue-list");
        queue.setPlaceholder(dropHint());
        queue.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ConversionJob job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(job.name());
                setGraphic(new FontIcon(job.error() == null ? Feather.IMAGE : Feather.ALERT_TRIANGLE));
                setTooltip(job.error() == null ? null : new Tooltip(job.error()));
            }
        });
        queue.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> showSelection());
        VBox.setVgrow(queue, Priority.ALWAYS);

        queue.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        HBox buttons = new HBox(6,
            iconButton(Feather.PLUS, "Add images", this::chooseFiles),
            iconButton(Feather.MINUS, "Remove selected", this::removeSelected),
            iconButton(Feather.TRASH_2, "Remove all", this::clearQueue));

        VBox panel = new VBox(10, title, queue, buttons);
        panel.setPadding(new Insets(16));

        queueSide = new SidePanel(panel, queueRail(), 260, Pos.TOP_LEFT);
        return queueSide;
    }

    private void removeSelected() {
        jobs.removeAll(List.copyOf(queue.getSelectionModel().getSelectedItems()));
        refreshStatus();
    }

    private void clearQueue() {
        jobs.clear();
        refreshStatus();
    }

    /**
     * What is left of the queue when it is folded away.
     *
     * <p>The three buttons come along because they are the part still worth reaching once the
     * images are loaded — which is exactly when someone folds the list of their names away.
     */
    private Node queueRail() {
        VBox rail = new VBox(10,
            railTab(Feather.LAYERS, "Show images", () -> queueSide.setCollapsed(false, true)),
            new Region(),
            iconButton(Feather.PLUS, "Add images", this::chooseFiles),
            iconButton(Feather.MINUS, "Remove selected", this::removeSelected),
            iconButton(Feather.TRASH_2, "Remove all", this::clearQueue));
        VBox.setVgrow(rail.getChildren().get(1), Priority.ALWAYS);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setPadding(new Insets(14, 0, 16, 0));
        rail.getStyleClass().add("rail");
        return rail;
    }

    private Node settingsRail() {
        VBox rail = new VBox(10,
            railTab(Feather.SLIDERS, "Show output settings",
                () -> settingsSide.setCollapsed(false, true)));
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setPadding(new Insets(14, 0, 16, 0));
        rail.getStyleClass().add("rail");
        return rail;
    }

    /** The icon that both names a folded panel and opens it again. */
    private Button railTab(Feather icon, String tooltip, Runnable action) {
        Button tab = iconButton(icon, tooltip, action);
        tab.getStyleClass().addAll(Styles.FLAT, "rail-tab");
        return tab;
    }

    private Node dropHint() {
        // Sized from the stylesheet, not here: see the note beside .drop-hint in omniplotter.css.
        FontIcon icon = new FontIcon(Feather.UPLOAD_CLOUD);
        Label text = new Label("Drop images here");
        text.getStyleClass().add(Styles.TEXT_MUTED);
        VBox box = new VBox(8, icon, text);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("drop-hint");
        return box;
    }

    // --- previews ---------------------------------------------------------------------------

    private Node buildPreviewPanel() {
        sourceView.setPreserveRatio(true);
        previewView.setPreserveRatio(true);
        // Nearest-neighbour: at these sizes the preview is magnified, and smoothing would blur away
        // the dithering and palette banding the user is here to judge.
        previewView.setSmooth(false);
        sourceView.setSmooth(true);

        sizeWarning.getStyleClass().add(Styles.DANGER);
        sizeWarning.setWrapText(true);
        sizeWarning.setVisible(false);
        sizeWarning.setManaged(false);
        sizeWarning.setGraphic(new FontIcon(Feather.ALERT_TRIANGLE));

        cropOverlay = new CropOverlay(sourceView);
        cropOverlay.setOnChange(this::cropChanged);
        gridOverlay = new GridOverlay(sourceView);
        gridOverlay.setOnTileClicked(this::tileClicked);
        gridOverlay.setOnChange(this::gridChanged);

        // Both draw over the same picture and only one is ever live, but the pane holding them
        // must not swallow what neither of them wants: picking on bounds would put a transparent
        // lid over the card the window accepts drops on.
        StackPane overlays = new StackPane(gridOverlay, cropOverlay);
        overlays.setPickOnBounds(false);

        sourcePane = previewCard("Source", sourceView, overlays, sourceCaption);
        previewPane = previewCard("Preview", previewView, null, previewCaption, sizeWarning);
        // The two cards were identical, which left nothing saying which of the images is the one
        // being produced. An accent edge is enough; the caption underneath already names the format.
        previewPane.getStyleClass().add("result");
        HBox.setHgrow(sourcePane, Priority.ALWAYS);
        HBox.setHgrow(previewPane, Priority.ALWAYS);

        HBox row = new HBox(GAP, sourcePane, previewPane);
        return row;
    }

    private Node previewCard(String title, ImageView view, Node overlay, Label... captions) {
        Label heading = new Label(title);
        heading.getStyleClass().add(Styles.TEXT_CAPTION);

        StackPane frame = overlay == null ? new StackPane(view) : new StackPane(view, overlay);
        frame.getStyleClass().add("preview-frame");
        VBox.setVgrow(frame, Priority.ALWAYS);
        // An ImageView reports the image's own size as its preferred size, which would make the
        // frame demand as much width as the source has pixels and push the settings panel off the
        // window. Pinning min and pref to zero lets the HBox hand out the space instead, and the
        // fit bindings then scale the image into whatever it gets.
        frame.setMinSize(0, 220);
        frame.setPrefSize(0, 220);
        view.fitWidthProperty().bind(frame.widthProperty().subtract(24));
        view.fitHeightProperty().bind(frame.heightProperty().subtract(24));

        captions[0].getStyleClass().add(Styles.TEXT_MUTED);
        captions[0].setWrapText(true);

        VBox card = new VBox(8, heading, frame);
        card.getChildren().addAll(captions);
        card.getStyleClass().addAll("glass", "liftable");
        card.setPadding(new Insets(16));
        card.setMinWidth(0);
        card.setPrefWidth(0);
        return card;
    }

    /**
     * What a dragged file lands on.
     *
     * <p>Until now the window answered a drag with nothing at all: the pointer crossed it, the
     * files went in, and in between there was no way to tell the window had noticed. Mouse
     * transparent, because the handlers that drive it sit on the root and everything has to reach
     * them.
     */
    private StackPane buildDropOverlay() {
        FontIcon icon = new FontIcon(Feather.UPLOAD_CLOUD);

        Label text = new Label("Drop to add");
        text.getStyleClass().add(Styles.TITLE_3);

        VBox target = new VBox(14, icon, text);
        target.setAlignment(Pos.CENTER);
        target.setPadding(new Insets(44, 76, 44, 76));
        target.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        target.getStyleClass().add("drop-target");

        StackPane overlay = new StackPane(target);
        overlay.getStyleClass().add("drop-overlay");
        overlay.setMouseTransparent(true);
        overlay.setVisible(false);
        overlay.setOpacity(0);
        return overlay;
    }

    // --- settings ---------------------------------------------------------------------------

    private Node buildSettingsPanel() {
        Node title = sectionTitle("Output", Feather.SLIDERS, Feather.CHEVRON_RIGHT,
            () -> settingsSide.setCollapsed(true, true));

        modeBox.setItems(FXCollections.observableArrayList(Mode.values()));
        modeBox.setConverter(labeller(m -> m == Mode.VAR ? "Picture file" : "Python script"));
        modeBox.setMaxWidth(Double.MAX_VALUE);

        targetBox.setConverter(labeller(t -> t.getBrand().label() + " — " + t.getDisplayName()));
        targetBox.setMaxWidth(Double.MAX_VALUE);

        formatBox.setConverter(labeller(Format::id));
        formatBox.setMaxWidth(Double.MAX_VALUE);

        widthSpinner.setEditable(true);
        heightSpinner.setEditable(true);
        colorsSpinner.setEditable(true);
        widthSpinner.setPrefWidth(90);
        heightSpinner.setPrefWidth(90);
        colorsSpinner.setMaxWidth(Double.MAX_VALUE);

        HBox size = new HBox(8, widthSpinner, new Label("×"), heightSpinner);
        size.setAlignment(Pos.CENTER_LEFT);

        keepRatio.setSelected(true);
        onCalcName.setPromptText("PICT1");
        onCalcName.textProperty().addListener((o, was, now) -> validateOnCalcName());
        onCalcHint.getStyleClass().add(Styles.TEXT_MUTED);
        onCalcHint.setWrapText(true);

        VBox panel = new VBox(12,
            title,
            field("Mode", modeBox),
            field("Calculator", targetBox),
            field("Format", formatBox),
            new Separator(),
            field("Canvas", size),
            presetChips,
            field("Colours", colorsSpinner),
            keepRatio,
            enlargeSmaller,
            new Separator(),
            buildImageControls(),
            new Separator(),
            field("On-calculator name", onCalcName),
            field("Slot number", onCalcNumber),
            onCalcHint);
        panel.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(panel);
        scroll.getStyleClass().add("settings-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        settingsSide = new SidePanel(scroll, settingsRail(), 300, Pos.TOP_RIGHT);
        return settingsSide;
    }

    /**
     * The controls the reference does not have.
     *
     * <p>Folded away by default. They are the ones most conversions never need, and the panel is
     * already long enough that putting five more sliders permanently at eye level would bury the
     * format picker that every conversion does need.
     */
    private Node buildImageControls() {
        Label heading = new Label("Image");
        heading.getStyleClass().add(Styles.TEXT_CAPTION);

        VBox body = new VBox(10);
        body.setVisible(false);
        body.setManaged(false);

        // Built by hand rather than through iconButton: the handler has to swap the button's own
        // graphic, which it cannot do from a Runnable created before the button exists.
        Button fold = new Button(null, new FontIcon(Feather.CHEVRON_DOWN));
        fold.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        fold.setTooltip(new Tooltip("Show the image controls"));
        fold.setOnAction(e -> {
            boolean showing = !body.isVisible();
            body.setVisible(showing);
            body.setManaged(showing);
            fold.setGraphic(new FontIcon(showing ? Feather.CHEVRON_UP : Feather.CHEVRON_DOWN));
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, heading, spacer, fold);
        header.setAlignment(Pos.CENTER_LEFT);

        // A look writes its numbers into the sliders and is then forgotten, so what the panel shows
        // is always what will actually be applied.
        for (Look look : Look.values()) {
            Button chip = new Button(capitalise(look.id()));
            chip.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
            chip.setTooltip(new Tooltip(look == Look.DOCUMENT
                ? "Text and screenshots: harder contrast, no dithering"
                : "Photographs: a little more contrast and bite"));
            chip.setOnAction(e -> applyLook(look));
            lookChips.getChildren().add(chip);
        }
        Button reset = new Button("Reset");
        reset.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        reset.setOnAction(e -> applyAdjustments(Adjustments.NONE, Dither.AUTO));
        lookChips.getChildren().add(reset);

        ditherBox.setItems(FXCollections.observableArrayList(Dither.values()));
        ditherBox.setConverter(labeller(d -> switch (d) {
            case AUTO -> "Automatic";
            case ON -> "On";
            case OFF -> "Off";
        }));
        ditherBox.getSelectionModel().select(Dither.AUTO);
        ditherBox.setMaxWidth(Double.MAX_VALUE);
        ditherBox.valueProperty().addListener((o, was, now) -> schedulePreview());

        onSettled(brightness);
        onSettled(contrast);
        onSettled(gamma);
        onSettled(saturation);
        onSettled(sharpen);

        fill.setTooltip(new Tooltip(
            "Crop the source to the canvas proportions instead of padding it with white"));
        fill.selectedProperty().addListener((o, was, now) -> {
            updateCropAspect();
            schedulePreview();
        });

        cropToggle.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        cropToggle.setTooltip(new Tooltip("Draw the part of the image to convert, on the source"));
        cropToggle.selectedProperty().addListener((o, was, now) -> {
            if (!restoringCropTool) {
                armedFor = now ? croppingJob : null;
            }
            updateCropAspect();
            cropOverlay.setActive(now);
        });

        Button clearCrop = new Button("Clear");
        clearCrop.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        clearCrop.setOnAction(e -> {
            cropOverlay.clear();
            cropToggle.setSelected(false);
        });

        HBox cropRow = new HBox(6, cropToggle, clearCrop);
        cropRow.setAlignment(Pos.CENTER_LEFT);

        gridBox.setMaxWidth(Double.MAX_VALUE);
        // The resolution is a property of the grid; how tall a line of type ends up is only
        // meaningful next to the size it was worked out for, so the label says which size that is
        // rather than pronouncing on whether the result is readable.
        gridBox.setConverter(labeller(grid -> grid == null ? "Whole page, one file"
            : grid + "  —  " + grid.count() + " tiles, " + grid.dpi() + " dpi ("
                + String.format("%.0f", TileGrid.REFERENCE_POINTS) + " pt \u2248 "
                + String.format("%.0f", grid.lineHeight()) + " px)"));
        gridBox.valueProperty().addListener((o, was, now) -> gridSelected(now));

        // Stepping through the pieces, because seeing one of twelve and having to guess at the
        // rest is most of the way to not being able to judge the grid at all.
        previousTile.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        nextTile.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        previousTile.setOnAction(e -> stepTile(-1));
        nextTile.setOnAction(e -> stepTile(1));
        tileLabel.getStyleClass().add(Styles.TEXT_MUTED);
        HBox tileNav = new HBox(8, previousTile, tileLabel, nextTile);
        tileNav.setAlignment(Pos.CENTER_LEFT);

        excludeTile.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        excludeTile.setOnAction(e -> {
            gridOverlay.toggleExcluded(focusedTile);
            updateTileCount();
        });
        includeAll.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        includeAll.setOnAction(e -> {
            gridOverlay.includeEverything();
            updateTileCount();
        });
        HBox tileButtons = new HBox(8, excludeTile, includeAll);
        tileButtons.setAlignment(Pos.CENTER_LEFT);

        tileCount.getStyleClass().add(Styles.TEXT_MUTED);
        tileControls = new VBox(8, tileNav, tileButtons, tileCount);

        previousPage.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        nextPage.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        previousPage.setOnAction(e -> turnPage(-1));
        nextPage.setOnAction(e -> turnPage(1));
        pageLabel.getStyleClass().add(Styles.TEXT_MUTED);
        HBox pageRow = new HBox(8, previousPage, pageLabel, nextPage);
        pageRow.setAlignment(Pos.CENTER_LEFT);

        body.getChildren().addAll(
            lookChips,
            field("Dithering", ditherBox),
            slider("Brightness", brightness),
            slider("Contrast", contrast),
            slider("Gamma", gamma),
            slider("Saturation", saturation),
            slider("Sharpen", sharpen),
            fill,
            cropRow,
            new Separator(),
            field("Cut the page into", gridBox),
            tileControls,
            pageRow);

        return new VBox(10, header, body);
    }

    /**
     * A slider that reports when it has stopped moving.
     *
     * <p>Not on every value: preprocessing a large image is felt, and re-running it for each pixel
     * of a drag leaves the preview chasing the handle. The two listeners between them cover a drag,
     * a click on the track and the arrow keys.
     */
    private void onSettled(Slider slider) {
        slider.valueProperty().addListener((o, was, now) -> {
            if (!slider.isValueChanging()) {
                schedulePreview();
            }
        });
        slider.valueChangingProperty().addListener((o, was, changing) -> {
            if (!changing) {
                schedulePreview();
            }
        });
    }

    /** A slider with its name and its current value, which a bare handle does not tell you. */
    private Node slider(String label, Slider control) {
        Label caption = new Label(label);
        caption.getStyleClass().add(Styles.TEXT_CAPTION);

        Label value = new Label();
        value.getStyleClass().add(Styles.TEXT_MUTED);
        value.textProperty().bind(control.valueProperty().map(
            v -> control == gamma || control == sharpen
                ? String.format("%.1f", v.doubleValue())
                : String.valueOf(v.intValue())));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, caption, spacer, value);
        top.setAlignment(Pos.CENTER_LEFT);

        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(2, top, control);
    }

    private void applyLook(Look look) {
        applyAdjustments(look.adjustments(), look.dither());
    }

    private void applyAdjustments(Adjustments values, Dither dither) {
        updating = true;
        brightness.setValue(values.brightness());
        contrast.setValue(values.contrast());
        gamma.setValue(values.gamma());
        saturation.setValue(values.saturation());
        sharpen.setValue(values.sharpen());
        ditherBox.getSelectionModel().select(dither);
        updating = false;
        schedulePreview();
    }

    private Adjustments currentAdjustments() {
        return new Adjustments((int) Math.round(brightness.getValue()),
            (int) Math.round(contrast.getValue()), gamma.getValue(),
            (int) Math.round(saturation.getValue()), sharpen.getValue());
    }

    /** Locks the rectangle to the canvas while filling, since that is the shape filling produces. */
    private void updateCropAspect() {
        if (cropOverlay == null) {
            return;
        }
        cropOverlay.setAspect(fill.isSelected() && heightSpinner.getValue() > 0
            ? (double) widthSpinner.getValue() / heightSpinner.getValue()
            : 0);
    }

    /**
     * Offers the grids that suit the screen this format draws on.
     *
     * <p>Rebuilt whenever the canvas or the page changes, because both move the arithmetic: the
     * same document cut for a 2:1 screen and for a 4:3 one wants different grids.
     */
    private void refreshGrids() {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        PageSize page = job == null ? null : job.pageSize();
        if (page == null) {
            page = PageSize.A4;
        }

        TileGrid current = gridBox.getValue();
        List<TileGrid> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.addAll(TileGrid.candidatesFor(page, widthSpinner.getValue(), heightSpinner.getValue()));

        updating = true;
        gridBox.setItems(FXCollections.observableArrayList(candidates));
        gridBox.getSelectionModel().select(
            current != null && candidates.contains(current) ? current : null);
        updating = false;
    }

    /** A grid and a crop are two ways of saying which part of the image matters; one at a time. */
    private void gridSelected(TileGrid grid) {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        Tiling tiling = grid == null ? Tiling.NONE : grid.tiling();

        if (grid != null && cropToggle.isSelected()) {
            cropToggle.setSelected(false);
        }
        cropToggle.setDisable(grid != null);

        gridOverlay.setTiling(tiling);
        gridOverlay.setActive(grid != null);

        // The job has to know the grid before anyone asks it what the pieces are, and the
        // resolution has to be set before the page is rendered to answer that.
        if (job != null) {
            job.setTiling(tiling);
            applyRenderDpi(job, tiling);
        }

        focusPiece(0);
        updateTileCount();
        if (!updating) {
            showSelection();
        }
    }

    /**
     * Tells the job how finely to render its page.
     *
     * <p>Derived from what the output needs rather than fixed: three columns of A4 onto a 384-pixel
     * screen is 139 dots per inch, and rendering below that and enlarging afterwards is what makes
     * a document converter look cheap.
     */
    private void applyRenderDpi(ConversionJob job, Tiling tiling) {
        PageSize page = job.pageSize();
        if (page == null) {
            return;
        }
        double needed = (double) widthSpinner.getValue() * tiling.columns() / page.widthInches();
        job.setRenderDpi((int) Math.ceil(needed * 2));
    }

    private void tileClicked(Tile tile) {
        focusedTile = tile;
        updateTileCount();
        schedulePreview();
    }

    private void gridChanged() {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        if (job != null) {
            job.setExcludedTiles(gridOverlay.excluded());
        }
        updateTileCount();
        schedulePreview();
    }

    /**
     * Everything about the pieces, shown only when there are pieces.
     *
     * <p>The whole block hides with the grid. A button offering to put back cells nobody has taken
     * out, sitting under a setting that is switched off, is a question rather than a control.
     */
    private void updateTileCount() {
        Tiling tiling = gridOverlay.tiling();
        boolean gridded = !tiling.isWhole();
        tileControls.setVisible(gridded);
        tileControls.setManaged(gridded);
        if (!gridded) {
            return;
        }

        List<Tile> tiles = currentTiles();
        int position = indexOfFocused(tiles);
        tileLabel.setText(focusedTile == null || position < 0
            ? "Click a cell to look at it"
            : "Tile " + (position + 1) + " of " + tiles.size() + "  (" + focusedTile.label() + ")");
        previousTile.setDisable(tiles.isEmpty() || position <= 0);
        nextTile.setDisable(tiles.isEmpty() || position < 0 || position >= tiles.size() - 1);

        boolean out = focusedTile != null && gridOverlay.excluded().contains(focusedTile.label());
        excludeTile.setDisable(focusedTile == null);
        excludeTile.setText(out ? "Put this tile back" : "Exclude this tile");

        int included = gridOverlay.includedCount();
        boolean anyExcluded = included != tiling.count();
        includeAll.setVisible(anyExcluded);
        includeAll.setManaged(anyExcluded);
        tileCount.setText(included + " of " + tiling.count() + " tiles will be converted");
    }

    /** The pieces of the page now on screen, in reading order. */
    private List<Tile> currentTiles() {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        if (job == null || job.tiling().isWhole()) {
            return List.of();
        }
        BufferedImage src = job.source();
        if (src == null || !job.tiling().fits(src.getWidth(), src.getHeight())) {
            return List.of();
        }
        return job.tiling().tilesOf(src.getWidth(), src.getHeight());
    }

    private int indexOfFocused(List<Tile> tiles) {
        if (focusedTile == null) {
            return -1;
        }
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).label().equals(focusedTile.label())) {
                return i;
            }
        }
        return -1;
    }

    /** Puts the eye on one of the pieces, so a new grid has something to show without hunting. */
    private void focusPiece(int index) {
        List<Tile> tiles = currentTiles();
        focusedTile = tiles.isEmpty() ? null : tiles.get(Math.min(index, tiles.size() - 1));
        gridOverlay.setFocused(focusedTile);
    }

    private void stepTile(int by) {
        List<Tile> tiles = currentTiles();
        if (tiles.isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(tiles.size() - 1, indexOfFocused(tiles) + by));
        focusedTile = tiles.get(next);
        gridOverlay.setFocused(focusedTile);
        updateTileCount();
        schedulePreview();
    }

    private void turnPage(int by) {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        if (job == null) {
            return;
        }
        job.setPage(job.page() + by);
        showSelection();
    }

    private void updatePageControls(ConversionJob job) {
        boolean document = job != null && job.isDocument();
        pageRowVisible(document);
        if (document) {
            pageLabel.setText("Page " + job.page() + " of " + job.pageCount());
            previousPage.setDisable(job.page() <= 1);
            nextPage.setDisable(job.page() >= job.pageCount());
        }
    }

    private void pageRowVisible(boolean visible) {
        for (Node node : new Node[]{previousPage, nextPage, pageLabel}) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    /** The crop belongs to the image, not to the settings: each queued file keeps its own. */
    private void cropChanged() {
        if (croppingJob != null) {
            croppingJob.setCrop(cropOverlay.crop());
        }
        schedulePreview();
    }

    private static String capitalise(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    /** A panel heading: the icon gives the two side panels a shared rhythm, the chevron folds. */
    private Node sectionTitle(String text, Feather icon, Feather chevron, Runnable fold) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TITLE_4);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button collapse = iconButton(chevron, "Collapse this panel", fold);
        collapse.getStyleClass().add(Styles.FLAT);

        HBox box = new HBox(8, new FontIcon(icon), label, spacer, collapse);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("section-title");
        return box;
    }

    private Node field(String label, Node control) {
        Label caption = new Label(label);
        caption.getStyleClass().add(Styles.TEXT_CAPTION);
        VBox box = new VBox(4, caption, control);
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    // --- action bar -------------------------------------------------------------------------

    private Node buildActionBar() {
        Button chooseOutput = new Button("Output folder…", new FontIcon(Feather.FOLDER));
        chooseOutput.getStyleClass().add(Styles.FLAT);
        chooseOutput.setOnAction(e -> chooseOutputDir());

        outputLabel.getStyleClass().add(Styles.TEXT_MUTED);

        Button theme = iconButton(Feather.MOON, "Light / dark — right-click for more",
            OmniPlotterApp.Theme::toggle);
        theme.getStyleClass().add(Styles.FLAT);

        // A moving background is a running repaint, which not everyone wants on a laptop. It is
        // parked on the theme button because that is already where the look of the window is set.
        CheckMenuItem animated = new CheckMenuItem("Animated background");
        animated.setSelected(Settings.getBoolean(Settings.UI_AURORA, true));
        animated.setOnAction(e -> {
            Settings.setBoolean(Settings.UI_AURORA, animated.isSelected());
            Settings.save();
            backdrop.setEnabled(animated.isSelected());
        });
        theme.setContextMenu(new ContextMenu(animated));

        // The window logs where its user cannot see it, so the way to that file has to be in the
        // window itself — otherwise a bug report can only say that something did not work.
        Button logs = iconButton(Feather.FILE_TEXT, "Open log folder",
            () -> Desktops.openFolder(AppPaths.logs()));
        logs.getStyleClass().add(Styles.FLAT);

        // Whoever installed a .dmg and never opens a terminal is exactly the person who would not
        // find out from anywhere else that this application is also a command line.
        Button terminal = iconButton(Feather.TERMINAL, "Set up the terminal command", this::setUpCommand);
        terminal.getStyleClass().add(Styles.FLAT);

        // A ring rather than a 160px bar: in a dock this narrow the bar was most of the width,
        // and the ring says the same thing in the space of a button while also reading the figure
        // out. Kept in the layout when hidden, so pressing Convert does not shuffle the row.
        progress.setVisible(false);
        progress.setMinSize(38, 38);
        progress.setPrefSize(38, 38);
        progress.setMaxSize(38, 38);

        convertButton.setDefaultButton(true);
        convertButton.getStyleClass().addAll(Styles.ACCENT, "hero");
        convertButton.setGraphic(new FontIcon(Feather.DOWNLOAD));
        convertButton.setOnAction(e -> convertAll());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, chooseOutput, outputLabel, spacer, status, progress,
            terminal, logs, theme, convertButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 16));
        bar.getStyleClass().add("dock");
        return bar;
    }

    // --- behaviour --------------------------------------------------------------------------

    /** Mode narrows the target list, target narrows the format list, format sets the limits. */
    private void wireCascade() {
        modeBox.valueProperty().addListener((o, was, now) -> onModeChanged());
        targetBox.valueProperty().addListener((o, was, now) -> onTargetChanged());
        formatBox.valueProperty().addListener((o, was, now) -> onFormatChanged());

        widthSpinner.valueProperty().addListener((o, was, now) -> {
            updateCropAspect();
            refreshGrids();
            schedulePreview();
        });
        heightSpinner.valueProperty().addListener((o, was, now) -> {
            updateCropAspect();
            schedulePreview();
        });
        colorsSpinner.valueProperty().addListener((o, was, now) -> schedulePreview());
        keepRatio.selectedProperty().addListener((o, was, now) -> schedulePreview());
        enlargeSmaller.selectedProperty().addListener((o, was, now) -> schedulePreview());
    }

    private void onModeChanged() {
        Mode mode = modeBox.getValue();
        if (mode == null) {
            return;
        }
        updating = true;
        Target previous = targetBox.getValue();
        targetBox.setItems(FXCollections.observableArrayList(Target.getByMode(mode)));
        targetBox.getSelectionModel().select(
            previous != null && previous.supportsMode(mode) ? previous : targetBox.getItems().get(0));
        updating = false;
        onTargetChanged();
    }

    private void onTargetChanged() {
        Target target = targetBox.getValue();
        Mode mode = modeBox.getValue();
        if (target == null || mode == null) {
            return;
        }
        updating = true;
        Format previous = formatBox.getValue();
        formatBox.setItems(FXCollections.observableArrayList(target.getSupportedFormats(mode)));
        formatBox.getSelectionModel().select(
            previous != null && formatBox.getItems().contains(previous)
                ? previous : target.getDefaultFormat(mode));
        updating = false;
        onFormatChanged();
    }

    private void onFormatChanged() {
        Format format = formatBox.getValue();
        if (format == null) {
            return;
        }
        FormatConfig config = FormatConfig.of(format);

        updating = true;
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1, config.maxWidth(), config.defaultWidth()));
        heightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1, config.maxHeight(), config.defaultHeight()));
        colorsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1, config.maxColors(), config.maxColors()));

        // A fixed-canvas format has nothing to choose; disabling says so more clearly than
        // silently ignoring what was typed.
        boolean editable = config.editableSize();
        widthSpinner.setDisable(!editable);
        heightSpinner.setDisable(!editable);
        presetChips.setDisable(!editable);

        presetChips.getChildren().clear();
        for (FormatConfig.SizePreset preset : config.presets()) {
            Button chip = new Button(preset.label());
            chip.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED, "preset-chip");
            chip.setTooltip(new Tooltip(preset.width() + " × " + preset.height()));
            chip.setOnAction(e -> {
                widthSpinner.getValueFactory().setValue(preset.width());
                heightSpinner.getValueFactory().setValue(preset.height());
            });
            presetChips.getChildren().add(chip);
        }

        OnCalcName.Rule rule = OnCalcName.ruleFor(format);
        boolean slotted = rule == OnCalcName.Rule.SLOT;
        onCalcNumber.setDisable(!slotted);
        onCalcName.setDisable(slotted || rule == OnCalcName.Rule.NONE);

        // The number of picture slots is a property of the model, not the format, so the spinner
        // has to follow the target: a TI-73 has three, numbered from one.
        Target.SlotRange slots = Target.slotRange(targetBox.getValue());
        int current = onCalcNumber.getValue() == null ? slots.min() : onCalcNumber.getValue();
        onCalcNumber.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
            slots.min(), slots.max(), slots.contains(current) ? current : slots.min()));

        onCalcHint.setText(switch (rule) {
            case SLOT -> "Stored on the calculator as "
                + (format == Format.ZPIC ? "pic" : format == Format.TI_8CA ? "Image" : "Pic")
                + onCalcNumber.getValue() + ". Slots " + slots.min() + "-" + slots.max()
                + " on this model.";
            case NONE -> "Saved as an ordinary file; the name is up to you.";
            default -> "Written into the file: " + OnCalcName.describe(format, targetBox.getValue())
                + ". Defaults to the image's own name.";
        });

        updating = false;
        validateOnCalcName();
        schedulePreview();
    }

    private ConversionOptions currentOptions(ConversionJob job) {
        Format format = formatBox.getValue();
        String name = onCalcName.getText() == null || onCalcName.getText().isBlank()
            ? (job != null ? job.baseName() : "IMAGE")
            : onCalcName.getText();
        return ConversionOptions.defaults(targetBox.getValue(), format)
            .withSize(widthSpinner.getValue(), heightSpinner.getValue())
            .withColors(colorsSpinner.getValue())
            .withKeepRatio(keepRatio.isSelected())
            .withEnlargeSmaller(enlargeSmaller.isSelected())
            .withOnCalc(name, onCalcNumber.getValue())
            .withAdjustments(currentAdjustments())
            .withDither(ditherBox.getValue() == null ? Dither.AUTO : ditherBox.getValue())
            // The crop comes off the job and everything else off the panel: one rectangle shared
            // by ten different photographs would be a rectangle that suits none of them.
            .withFraming(new Framing(fill.isSelected(), previewCrop(job)))
            .clampedTo(format);
    }

    /**
     * Which rectangle the preview should show.
     *
     * <p>With a grid up that is a tile — the one last clicked, or the first that will actually be
     * converted — because a preview of the whole page while producing twenty-four pieces of it
     * would be showing something nobody asked for.
     */
    private Crop previewCrop(ConversionJob job) {
        if (job == null) {
            return null;
        }
        Tiling tiling = job.tiling();
        if (tiling.isWhole()) {
            return job.crop();
        }
        BufferedImage src = job.source();
        if (src == null || !tiling.fits(src.getWidth(), src.getHeight())) {
            return job.crop();
        }
        List<Tile> tiles = tiling.tilesOf(src.getWidth(), src.getHeight());
        if (focusedTile != null) {
            for (Tile tile : tiles) {
                if (tile.label().equals(focusedTile.label())) {
                    return tile.crop();
                }
            }
        }
        for (Tile tile : tiles) {
            if (!job.excludedTiles().contains(tile.label())) {
                return tile.crop();
            }
        }
        return tiles.get(0).crop();
    }

    private void showSelection() {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        croppingJob = job;
        updatePageControls(job);
        if (job != null) {
            applyRenderDpi(job, job.tiling());
        }
        refreshGrids();
        updateTileCount();

        if (job == null) {
            // Nothing selected, so there is nothing to draw a rectangle on — including when the
            // image being cropped is the one that was just deleted.
            cropOverlay.setImage(0, 0, null);
            gridOverlay.setImage(0, 0);
            restoreCropTool(null);
            Animations.swap(sourceView, null);
            Animations.swap(previewView, null);
            sourceCaption.setText("No image selected");
            previewCaption.setText("");
            return;
        }
        BufferedImage src = job.source();
        if (src == null) {
            Animations.swap(sourceView, null);
            cropOverlay.setImage(0, 0, null);
            restoreCropTool(null);
            sourceCaption.setText("Could not read this file: " + job.error());
        } else {
            Animations.swap(sourceView, SwingFXUtils.toFXImage(src, null));
            sourceCaption.setText(job.name() + " — " + src.getWidth() + " × " + src.getHeight());
            // Whatever was drawn on this image last time, back where it was. The rectangle shows
            // on any cropped image, so one says so without being asked; the tool itself comes back
            // only on the image it was switched on for.
            cropOverlay.setImage(src.getWidth(), src.getHeight(), job.crop());
            restoreCropTool(job);

            gridOverlay.setImage(src.getWidth(), src.getHeight());
            gridOverlay.setTiling(job.tiling());
            gridOverlay.setExcluded(job.excludedTiles());
            gridOverlay.setActive(!job.tiling().isWhole());
            if (!job.tiling().isWhole() && focusedTile == null) {
                focusPiece(0);
            } else {
                gridOverlay.setFocused(focusedTile);
            }
            updateTileCount();
        }
        schedulePreview();
    }

    /**
     * Puts the crop tool back the way this image left it.
     *
     * <p>Marked as a restore rather than a choice, so the listener does not read it as the user
     * switching the tool off for the image they are leaving — which would forget the very thing
     * being remembered.
     */
    private void restoreCropTool(ConversionJob job) {
        if (armedFor != null && !jobs.contains(armedFor)) {
            armedFor = null;
        }
        restoringCropTool = true;
        cropToggle.setSelected(job != null && job == armedFor);
        restoringCropTool = false;
    }

    /**
     * Recomputes the preview off the UI thread.
     *
     * <p>Preprocessing a large image takes long enough to be felt, and settings change while
     * dragging a spinner, so any in-flight run is cancelled rather than queued behind.
     */
    private void schedulePreview() {
        if (updating) {
            return;
        }
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        Format format = formatBox.getValue();
        if (job == null || format == null || job.source() == null) {
            Animations.swap(previewView, null);
            previewCaption.setText("");
            sizeWarning.setVisible(false);
            sizeWarning.setManaged(false);
            return;
        }

        if (previewTask != null && previewTask.isRunning()) {
            previewTask.cancel();
        }

        long generation = ++previewGeneration;
        ConversionOptions options = currentOptions(job);
        Task<ConversionJob.Preview> task = new Task<>() {
            @Override
            protected ConversionJob.Preview call() throws Exception {
                return job.preview(format, options);
            }
        };
        task.setOnSucceeded(e -> {
            if (generation != previewGeneration) {
                return;   // superseded by a later request
            }
            ConversionJob.Preview preview = task.getValue();
            BufferedImage out = preview.image();
            Animations.swap(previewView, SwingFXUtils.toFXImage(out, null));
            previewCaption.setText(String.format(java.util.Locale.ROOT, "%s — %d × %d, %d colours, %s",
                format.id(), out.getWidth(), out.getHeight(), countColors(out),
                humanSize(preview.encodedSize())));

            // Say up front whether the result will actually fit on the calculator, rather than
            // letting the user find out at transfer time.
            var warning = OutputLimits.check(options.target(), format, preview.encodedSize());
            sizeWarning.setText(warning.orElse(""));
            sizeWarning.setVisible(warning.isPresent());
            sizeWarning.setManaged(warning.isPresent());
        });
        task.setOnFailed(e -> {
            if (generation != previewGeneration) {
                return;
            }
            Animations.swap(previewView, null);
            previewCaption.setText("Preview failed: " + task.getException().getMessage());
            sizeWarning.setVisible(false);
            sizeWarning.setManaged(false);
        });
        previewTask = task;
        Thread thread = new Thread(task, "preview");
        thread.setDaemon(true);
        thread.start();
    }

    /** Marks the name field when what is typed would not be accepted by the calculator. */
    private void validateOnCalcName() {
        Format format = formatBox.getValue();
        if (format == null) {
            return;
        }
        String typed = onCalcName.getText();
        boolean bad = typed != null && !typed.isBlank()
            && OnCalcName.validateName(format, typed).isPresent();
        onCalcName.pseudoClassStateChanged(
            javafx.css.PseudoClass.getPseudoClass("danger"), bad);
        convertButton.setDisable(bad);
    }

    private static String humanSize(int bytes) {
        return bytes < 1024 ? bytes + " B"
            : String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
    }

    private static int countColors(BufferedImage img) {
        var seen = new java.util.HashSet<Integer>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                seen.add((p >>> 24) == 0 ? 0 : p);
                if (seen.size() > 1 << 16) {
                    return seen.size();
                }
            }
        }
        return seen.size();
    }

    private void convertAll() {
        if (jobs.isEmpty()) {
            status.setText("Nothing to convert.");
            return;
        }
        Format format = formatBox.getValue();
        List<ConversionJob> pending = new ArrayList<>(jobs);
        Path destination = outputDir;

        convertButton.setDisable(true);
        progress.setVisible(true);
        progress.setProgress(0);

        // Counted rather than assumed: with a grid up, one queued page is twenty-four files, and
        // reporting the number of images converted would be answering a question nobody asked.
        int[] written = {0};
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                List<String> failures = new ArrayList<>();
                for (int i = 0; i < pending.size(); i++) {
                    ConversionJob job = pending.get(i);
                    try {
                        written[0] += job.convert(format, currentOptionsFor(job), destination).size();
                    } catch (Exception e) {
                        failures.add(job.name() + ": " + e.getMessage());
                    }
                    updateProgress(i + 1, pending.size());
                }
                return failures;
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            convertButton.setDisable(false);
            List<String> failures = task.getValue();
            int ok = written[0];
            status.setText(failures.isEmpty()
                ? ok + (ok == 1 ? " file written to " : " files written to ") + destination.getFileName()
                : ok + " written, " + failures.size() + " failed — " + failures.get(0));
        });
        task.setOnFailed(e -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            convertButton.setDisable(false);
            status.setText("Conversion failed: " + task.getException().getMessage());
        });
        Thread thread = new Thread(task, "convert");
        thread.setDaemon(true);
        thread.start();
    }

    /** Snapshot of the settings for one job; read on the UI thread before the worker starts. */
    private ConversionOptions currentOptionsFor(ConversionJob job) {
        final ConversionOptions[] holder = new ConversionOptions[1];
        if (Platform.isFxApplicationThread()) {
            return currentOptions(job);
        }
        try {
            javafx.application.Platform.runLater(() -> {
                synchronized (holder) {
                    holder[0] = currentOptions(job);
                    holder.notifyAll();
                }
            });
            synchronized (holder) {
                while (holder[0] == null) {
                    holder.wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return holder[0];
    }

    // --- input ------------------------------------------------------------------------------

    /**
     * Handlers on the root rather than on the panels.
     *
     * <p>Entering a child does not exit its parent, so the overlay put up on entering the window
     * stays up while the pointer crosses the panels inside it — which is what the same handlers
     * spread over the four surfaces would flicker on.
     */
    private void wireDragAndDrop() {
        setOnDragEntered((DragEvent e) -> {
            if (e.getDragboard().hasFiles()) {
                Animations.reveal(dropOverlay, true);
            }
            e.consume();
        });
        setOnDragExited((DragEvent e) -> {
            Animations.reveal(dropOverlay, false);
            e.consume();
        });
        setOnDragOver((DragEvent e) -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        setOnDragDropped((DragEvent e) -> {
            Animations.reveal(dropOverlay, false);
            if (e.getDragboard().hasFiles()) {
                addFiles(e.getDragboard().getFiles());
                e.setDropCompleted(true);
            }
            e.consume();
        });
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add images");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        List<File> chosen = chooser.showOpenMultipleDialog(stage);
        if (chosen != null) {
            addFiles(chosen);
        }
    }

    private void addFiles(List<File> files) {
        boolean wasEmpty = jobs.isEmpty();
        for (File file : files) {
            if (file.isFile()) {
                jobs.add(new ConversionJob(file));
            }
        }
        if (wasEmpty && !jobs.isEmpty()) {
            queue.getSelectionModel().select(0);
        }
        refreshStatus();
    }

    private void chooseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Output folder");
        if (outputDir.toFile().isDirectory()) {
            chooser.setInitialDirectory(outputDir.toFile());
        }
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            outputDir = chosen.toPath();
            // Saved on the spot rather than at exit. It is the setting users notice losing, and
            // choosing it is deliberate enough to be worth a write.
            Settings.set(Settings.OUTPUT_DIR, outputDir.toString());
            Settings.save();
            updateOutputLabel();
        }
    }

    /**
     * Reopens on whatever was selected last, defaulting to a Casio picture file rather than
     * whichever target happens to sort first.
     *
     * <p>Each step is attempted separately: a settings file naming a format that no longer exists,
     * or one that the restored target does not support, should cost that one step and not the rest.
     */
    private void restoreSelection() {
        modeBox.getSelectionModel().select(
            lookup(Settings.get(Settings.LAST_MODE, null), Mode::fromString, Mode.VAR));
        onModeChanged();

        Target target = lookup(Settings.get(Settings.LAST_TARGET, null), Target::fromString, Target.CASIO_CG);
        targetBox.getSelectionModel().select(
            targetBox.getItems().contains(target) ? target : targetBox.getItems().get(0));

        Format format = lookup(Settings.get(Settings.LAST_FORMAT, null), Format::fromString, null);
        if (format != null && formatBox.getItems().contains(format)) {
            formatBox.getSelectionModel().select(format);
        }
    }

    /** The window's half of {@code omniplotter setup}: same work, reported in a dialog. */
    private void setUpCommand() {
        var launcher = CommandSetup.launcher();
        if (launcher.isEmpty()) {
            report(Alert.AlertType.INFORMATION, "Nothing to link to",
                "This copy is running as a plain jar rather than an installed application, so there "
                    + "is no launcher to put on your PATH.");
            return;
        }
        try {
            CommandSetup.Outcome outcome = CommandSetup.install(CommandSetup.consoleLauncher(launcher.get()));
            if (outcome.onPath()) {
                report(Alert.AlertType.INFORMATION, "Ready",
                    "Open a terminal and run:\n\n    omniplotter --help");
            } else {
                // The same refusal the command line makes: the shell's configuration is the user's,
                // and a window is the worst place to edit it without being asked.
                report(Alert.AlertType.INFORMATION, "One step left",
                    outcome.link() + " was created, but " + outcome.link().getParent()
                        + " is not on your PATH.\n\nAdd this line to " + outcome.shellFile()
                        + ":\n\n    " + outcome.shellLine());
            }
        } catch (IOException e) {
            report(Alert.AlertType.ERROR, "Could not set up the command", String.valueOf(e.getMessage()));
        }
    }

    private void report(Alert.AlertType type, String header, String detail) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle("Terminal command");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.getDialogPane().setMinWidth(520);
        alert.showAndWait();
    }

    // --- updates ----------------------------------------------------------------------------

    /**
     * Asks whether a newer release exists, off the interface thread and without blocking startup.
     *
     * <p>Silent unless there is something to say: no spinner, no "you are up to date", nothing at
     * all when the machine is offline.
     */
    private void checkForUpdate() {
        UpdateCheck.inBackground(result -> Platform.runLater(() -> {
            Node banner = updateBanner(result);
            BorderPane.setMargin(banner, new Insets(GAP, GAP, 0, GAP));
            content.setTop(banner);
            Animations.dropIn(banner);
        }));
    }

    private Node updateBanner(UpdateCheck.Result result) {
        Label text = new Label("OmniPlotter " + result.latest() + " is available.");

        Button notes = new Button("Release notes");
        notes.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
        notes.setOnAction(e -> Desktops.openUrl(result.url()));

        // Skipping is remembered, so the same version does not come back tomorrow. Dismissing is
        // not: closing a banner is not the same as saying no.
        Button skip = new Button("Skip this version");
        skip.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        skip.setOnAction(e -> {
            Settings.set(Settings.UPDATE_SKIPPED, result.latest().toString());
            Settings.save();
            hideBanner();
        });

        Button dismiss = iconButton(Feather.X, "Dismiss", this::hideBanner);
        dismiss.getStyleClass().add(Styles.FLAT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox banner = new HBox(12, new FontIcon(Feather.DOWNLOAD_CLOUD), text, spacer, notes, skip, dismiss);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(10, 12, 10, 16));
        banner.getStyleClass().add("update-banner");
        return banner;
    }

    private void hideBanner() {
        Node banner = content.getTop();
        if (banner != null) {
            Animations.dismiss(banner, () -> content.setTop(null));
        }
    }

    /**
     * The surfaces settling into place, played once the window is on screen.
     *
     * <p>Called by {@link OmniPlotterApp} rather than from the constructor: before the stage is
     * shown there is no layout, and a rise from an unlaid-out position is a jump.
     */
    public void playIntro() {
        Animations.intro(surfaces);
    }

    /** Writes the current selection back. Called once, when the window is closing. */
    public void rememberState() {
        Settings.set(Settings.OUTPUT_DIR, outputDir.toString());
        Settings.setBoolean(Settings.UI_QUEUE_FOLDED, queueSide.isCollapsed());
        Settings.setBoolean(Settings.UI_SETTINGS_FOLDED, settingsSide.isCollapsed());
        if (modeBox.getValue() != null) {
            Settings.set(Settings.LAST_MODE, modeBox.getValue().toString());
        }
        if (targetBox.getValue() != null) {
            Settings.set(Settings.LAST_TARGET, targetBox.getValue().getId());
        }
        if (formatBox.getValue() != null) {
            Settings.set(Settings.LAST_FORMAT, formatBox.getValue().id());
        }
    }

    /** The three identifier lookups throw on anything they do not know; a stale setting is not fatal. */
    private static <T> T lookup(String id, java.util.function.Function<String, T> parse, T fallback) {
        if (id == null) {
            return fallback;
        }
        try {
            return parse.apply(id);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void updateOutputLabel() {
        outputLabel.setText(outputDir.toString().replace(System.getProperty("user.home"), "~"));
    }

    private void refreshStatus() {
        status.setText(jobs.isEmpty()
            ? "Drop images to begin."
            : jobs.size() + (jobs.size() == 1 ? " image queued." : " images queued."));
    }

    // --- small helpers ----------------------------------------------------------------------

    private Button iconButton(Feather icon, String tooltip, Runnable action) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().add(Styles.BUTTON_ICON);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private static <T> javafx.util.StringConverter<T> labeller(java.util.function.Function<T, String> toText) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : toText.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }
}
