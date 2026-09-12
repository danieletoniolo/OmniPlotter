package com.github.omniplotter.gui;

import atlantafx.base.controls.RingProgressIndicator;
import atlantafx.base.theme.Styles;
import com.github.omniplotter.app.AppPaths;
import com.github.omniplotter.app.CommandSetup;
import com.github.omniplotter.app.Desktops;
import com.github.omniplotter.app.Messages;
import com.github.omniplotter.app.Settings;
import com.github.omniplotter.app.UpdateCheck;
import com.github.omniplotter.app.Version;
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
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
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
import javafx.util.Duration;
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

    /** What to type first, once the command is on PATH. */
    private static final String HELP = CommandSetup.COMMAND + " --help";

    private final Stage stage;

    private final Backdrop backdrop = new Backdrop();
    private final BorderPane content = new BorderPane();
    private final StackPane dropOverlay = buildDropOverlay();
    private HelpOverlay help;

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
    private final CheckBox keepRatio = new CheckBox(Messages.get("settings.keepRatio"));
    private final CheckBox enlargeSmaller = new CheckBox(Messages.get("settings.enlarge"));
    private final Slider brightness = new Slider(-100, 100, 0);
    private final Slider contrast = new Slider(-100, 100, 0);
    private final Slider gamma = new Slider(0.2, 3.0, 1.0);
    private final Slider saturation = new Slider(-100, 100, 0);
    private final Slider sharpen = new Slider(0, 3, 0);
    private final ComboBox<Dither> ditherBox = new ComboBox<>();
    private final FlowPane lookChips = new FlowPane(6, 6);
    private final CheckBox fill = new CheckBox(Messages.get("image.fill"));
    private final ToggleButton cropToggle = new ToggleButton(Messages.get("image.crop"));
    /** Why Crop is greyed out, which used to be something to work out. */
    private final Label cropBlocked = new Label();
    private CropOverlay cropOverlay;
    private GridOverlay gridOverlay;

    private final ComboBox<TileGrid> gridBox = new ComboBox<>();
    private final Label tileCount = new Label();
    private final Label tileLabel = new Label();
    private final Button previousTile = new Button("\u2039");
    private final Button nextTile = new Button("\u203a");
    private final Button excludeTile = new Button(Messages.get("tiles.exclude"));
    private final Button includeAll = new Button(Messages.get("tiles.includeAll"));
    private final Button applyToPages = new Button(Messages.get("tiles.applyToPages"));
    private VBox tileControls;
    /** The step that appears with a file to cut up, so it can be hidden when there is none. */
    private StepSection tilesSection;
    /** Where you are in the document and in the grid, under the picture both are about. */
    private VBox previewToolbar;
    private HBox pageRow;
    private HBox tileRow;
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

    /** The same, for the grid picker: rebuilding its list must not read as choosing from it. */
    private boolean syncingGrid;

    private final TextField onCalcName = new TextField();
    private final Spinner<Integer> onCalcNumber = new Spinner<>(0, 9, 1);
    private final Label onCalcHint = new Label();

    private final ImageView sourceView = new ImageView();
    private final ImageView previewView = new ImageView();
    private final Label sourceCaption = new Label(Messages.get("preview.none"));
    private final Label previewCaption = new Label();
    private final Label sizeWarning = new Label();

    private final Label outputLabel = new Label();
    private final RingProgressIndicator progress = new RingProgressIndicator(0);
    private final Label status = new Label(Messages.get("queue.begin"));
    private final Button convertButton = new Button(Messages.get("dock.convert"));
    /**
     * The other way to convert, kept beside the button rather than in it.
     *
     * <p>A SplitMenuButton would read better and cannot be the window's default button, so Enter
     * would stop converting. This keeps that and puts the arrow next to it.
     */
    private final Button convertMore = new Button();
    /** The other way, kept as a field because what it offers says how many pages. */
    private final Button everyPage = new Button();
    /** Puts the queue state back after a result has had its moment. */
    private PauseTransition statusRevert;
    /**
     * The convert menu, as a panel inside this window rather than a popup.
     *
     * <p>A {@code ContextMenu} is a window of its own, and every attempt to make a second click on
     * the arrow close it came down to which of two windows sees that click first — which is not
     * something to build on, and twice was not what it appeared to be. In the scene there is one
     * event path, and this code is the only thing on it.
     */
    private final VBox convertMenu = new VBox();

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

        // Above the content and below the drop target: it belongs to the action bar, and a file
        // being dragged in should still cover everything.
        // Over the content and under the drop target: a file being dragged in still covers
        // everything, and the tour covers everything else.
        help = buildHelp(queueBox, settingsBox, dock);
        getChildren().addAll(backdrop, content, convertMenu, help, dropOverlay);
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
        updatePageControls(null);
        updateOutputLabel();
        // The queue starts empty, so the arrow starts hidden and Convert starts round.
        updateConvertMenu();
        checkForUpdate();
        firstRun();
    }

    // --- queue ------------------------------------------------------------------------------

    private Node buildQueuePanel() {
        Node title = sectionTitle(Messages.get("queue.title"), Feather.LAYERS, Feather.CHEVRON_LEFT,
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
        // Whatever changes the queue, the second way to convert appears and disappears with it.
        jobs.addListener((javafx.collections.ListChangeListener<ConversionJob>) change -> updateConvertMenu());
        VBox.setVgrow(queue, Priority.ALWAYS);

        queue.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        HBox buttons = new HBox(6,
            iconButton(Feather.PLUS, Messages.get("queue.add"), this::chooseFiles),
            iconButton(Feather.MINUS, Messages.get("queue.remove"), this::removeSelected),
            iconButton(Feather.TRASH_2, Messages.get("queue.removeAll"), this::clearQueue));

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
            railTab(Feather.LAYERS, Messages.get("queue.show"), () -> queueSide.setCollapsed(false, true)),
            new Region(),
            iconButton(Feather.PLUS, Messages.get("queue.add"), this::chooseFiles),
            iconButton(Feather.MINUS, Messages.get("queue.remove"), this::removeSelected),
            iconButton(Feather.TRASH_2, Messages.get("queue.removeAll"), this::clearQueue));
        VBox.setVgrow(rail.getChildren().get(1), Priority.ALWAYS);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setPadding(new Insets(14, 0, 16, 0));
        rail.getStyleClass().add("rail");
        return rail;
    }

    private Node settingsRail() {
        VBox rail = new VBox(10,
            railTab(Feather.SLIDERS, Messages.get("settings.show"),
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

    /**
     * The empty list, which is also the way to fill it.
     *
     * <p>It looked like a target and behaved like a label: the only way to a file chooser was the
     * small {@code +} above it. The placeholder already covers exactly the empty area and already
     * receives the clicks, so it only had to be told what they mean.
     *
     * <p>Only while the list is empty. On a filled one, clicking the space below the rows is how a
     * selection is cleared, and taking that over to open a dialog would be worse than the gap.
     */
    private Node dropHint() {
        // Sized from the stylesheet, not here: see the note beside .drop-hint in omniplotter.css.
        FontIcon icon = new FontIcon(Feather.UPLOAD_CLOUD);
        Label text = new Label(Messages.get("queue.empty"));
        text.getStyleClass().add(Styles.TEXT_MUTED);

        Label click = new Label(Messages.get("queue.empty.click"));
        click.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        VBox box = new VBox(8, icon, text, click);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("drop-hint");
        box.setCursor(Cursor.HAND);
        box.setOnMouseClicked(e -> chooseFiles());
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

        sourcePane = previewCard(Messages.get("preview.source"), sourceView, overlays,
            buildPreviewToolbar(), sourceCaption);
        previewPane = previewCard(Messages.get("preview.output"), previewView, null, null,
            previewCaption, sizeWarning);
        // The two cards were identical, which left nothing saying which of the images is the one
        // being produced. An accent edge is enough; the caption underneath already names the format.
        previewPane.getStyleClass().add("result");
        HBox.setHgrow(sourcePane, Priority.ALWAYS);
        HBox.setHgrow(previewPane, Priority.ALWAYS);

        HBox row = new HBox(GAP, sourcePane, previewPane);
        return row;
    }

    private Node previewCard(String title, ImageView view, Node overlay, Node toolbar,
                             Label... captions) {
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
        // Under the picture rather than over it: it says what is on screen, which is something
        // read after looking rather than before.
        if (toolbar != null) {
            card.getChildren().add(toolbar);
        }
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

        Label text = new Label(Messages.get("drop.here"));
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
        Node title = sectionTitle(Messages.get("settings.title"), Feather.SLIDERS, Feather.CHEVRON_RIGHT,
            () -> settingsSide.setCollapsed(true, true));

        modeBox.setItems(FXCollections.observableArrayList(Mode.values()));
        modeBox.setConverter(labeller(m -> Messages.get(m == Mode.VAR ? "settings.mode.picture" : "settings.mode.script")));
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

        // Numbered, and in the order a conversion actually happens: which calculator, how big,
        // how it should look, how to cut it up, what to call it. The old panel was one column of
        // labelled controls with no order stated and the whole document half folded away inside a
        // section called "Image".
        VBox panel = new VBox(16,
            title,
            new StepSection(1, Messages.get("step.calculator"), Messages.get("step.calculator.says"))
                .with(field(Messages.get("settings.mode"), modeBox),
                    field(Messages.get("settings.calculator"), targetBox),
                    field(Messages.get("settings.format"), formatBox)),
            new StepSection(2, Messages.get("step.canvas"), Messages.get("step.canvas.says"))
                .with(field(Messages.get("settings.canvas"), size),
                    presetChips,
                    field(Messages.get("settings.colours"), colorsSpinner),
                    keepRatio,
                    enlargeSmaller),
            pictureStep(),
            tilesStep(),
            new StepSection(5, Messages.get("step.name"), Messages.get("step.name.says"))
                .with(field(Messages.get("settings.name"), onCalcName),
                    field(Messages.get("settings.slot"), onCalcNumber),
                    onCalcHint));
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
     * <p>Folded away by default, and the one step that is: they are what most conversions leave
     * alone, and five sliders permanently at eye level bury the format picker that every
     * conversion needs. What is no longer folded in with them is the page grid, which was the
     * whole document feature hidden behind a chevron labelled "Image".
     */
    private StepSection pictureStep() {
        // A look writes its numbers into the sliders and is then forgotten, so what the panel shows
        // is always what will actually be applied.
        for (Look look : Look.values()) {
            Button chip = new Button(capitalise(look.id()));
            chip.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
            chip.setTooltip(new Tooltip(Messages.get(
                look == Look.DOCUMENT ? "image.look.document" : "image.look.photo")));
            chip.setOnAction(e -> applyLook(look));
            lookChips.getChildren().add(chip);
        }
        Button reset = new Button(Messages.get("image.reset"));
        reset.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        reset.setOnAction(e -> applyAdjustments(Adjustments.NONE, Dither.AUTO));
        lookChips.getChildren().add(reset);

        ditherBox.setItems(FXCollections.observableArrayList(Dither.values()));
        ditherBox.setConverter(labeller(d -> Messages.get(switch (d) {
            case AUTO -> "image.dither.auto";
            case ON -> "image.dither.on";
            case OFF -> "image.dither.off";
        })));
        ditherBox.getSelectionModel().select(Dither.AUTO);
        ditherBox.setMaxWidth(Double.MAX_VALUE);
        ditherBox.valueProperty().addListener((o, was, now) -> schedulePreview());

        onSettled(brightness);
        onSettled(contrast);
        onSettled(gamma);
        onSettled(saturation);
        onSettled(sharpen);

        fill.setTooltip(new Tooltip(Messages.get("image.fill.tip")));
        fill.selectedProperty().addListener((o, was, now) -> {
            updateCropAspect();
            schedulePreview();
        });

        cropToggle.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        cropToggle.setTooltip(new Tooltip(Messages.get("image.crop.tip")));
        cropToggle.selectedProperty().addListener((o, was, now) -> {
            if (!restoringCropTool) {
                armedFor = now ? croppingJob : null;
            }
            updateCropAspect();
            cropOverlay.setActive(now);
        });

        Button clearCrop = new Button(Messages.get("image.crop.clear"));
        clearCrop.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        clearCrop.setOnAction(e -> {
            cropOverlay.clear();
            cropToggle.setSelected(false);
        });

        HBox cropRow = new HBox(6, cropToggle, clearCrop);
        cropRow.setAlignment(Pos.CENTER_LEFT);

        // Crop greys out under a grid, and used to do it in silence. Being told which of the two
        // controls is in the way is the difference between a rule and a broken button.
        cropBlocked.getStyleClass().add("step-subtitle");
        cropBlocked.setWrapText(true);
        cropBlocked.setText(Messages.get("image.crop.blocked"));
        cropBlocked.setVisible(false);
        cropBlocked.setManaged(false);

        return new StepSection(3, Messages.get("step.picture"), Messages.get("step.picture.says"))
            .with(lookChips,
                field(Messages.get("image.dither"), ditherBox),
                slider(Messages.get("image.brightness"), brightness),
                slider(Messages.get("image.contrast"), contrast),
                slider(Messages.get("image.gamma"), gamma),
                slider(Messages.get("image.saturation"), saturation),
                slider(Messages.get("image.sharpen"), sharpen),
                fill,
                cropRow,
                cropBlocked)
            .foldable(true);
    }

    /**
     * Cutting a page into pieces that each fill the screen.
     *
     * <p>A step of its own, shown whenever there is a file to cut up. Stepping through the pieces
     * and switching one off are not here: those are about the piece on screen, so they are on the
     * picture — {@link #buildPreviewToolbar()}. What is left is what applies to the whole grid.
     */
    private StepSection tilesStep() {
        gridBox.setMaxWidth(Double.MAX_VALUE);
        // The resolution is a property of the grid; how tall a line of type ends up is only
        // meaningful next to the size it was worked out for, so the label says which size that is
        // rather than pronouncing on whether the result is readable.
        gridBox.setConverter(labeller(grid -> grid == null
            ? Messages.get("tiles.grid.whole")
            : Messages.get("tiles.grid.option", grid, grid.count(), grid.dpi(),
                String.format("%.0f", TileGrid.REFERENCE_POINTS),
                String.format("%.0f", grid.lineHeight()))));
        gridBox.valueProperty().addListener((o, was, now) -> gridSelected(now));

        includeAll.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        includeAll.setOnAction(e -> {
            gridOverlay.includeEverything();
            updateTileCount();
        });
        applyToPages.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        applyToPages.setTooltip(new Tooltip(Messages.get("tiles.applyToPages.tip")));
        applyToPages.setOnAction(e -> applyExclusionsEverywhere());

        HBox bulk = new HBox(8, includeAll, applyToPages);
        bulk.setAlignment(Pos.CENTER_LEFT);

        tileCount.getStyleClass().add(Styles.TEXT_MUTED);
        tileControls = new VBox(8, bulk, tileCount);

        tilesSection = new StepSection(4, Messages.get("step.tiles"), Messages.get("step.tiles.says"))
            .with(field(Messages.get("tiles.grid"), gridBox), tileControls);
        return tilesSection;
    }

    /**
     * Where you are in the document, and in the grid, on the picture it is about.
     *
     * <p>Both of these steppers were in the settings panel, three hundred pixels from the image
     * they page: the arrows were next to the setting that produced the grid rather than next to
     * the thing they moved. Excluding comes with them, because it is about the piece on screen.
     */
    private Node buildPreviewToolbar() {
        previousPage.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        nextPage.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        previousPage.setOnAction(e -> turnPage(-1));
        nextPage.setOnAction(e -> turnPage(1));
        pageLabel.getStyleClass().add(Styles.TEXT_MUTED);

        pageRow = new HBox(6, previousPage, pageLabel, nextPage);
        pageRow.setAlignment(Pos.CENTER_LEFT);
        pageRow.setVisible(false);
        pageRow.setManaged(false);

        // Stepping through the pieces, because seeing one of twelve and having to guess at the
        // rest is most of the way to not being able to judge the grid at all.
        previousTile.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        nextTile.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        previousTile.setOnAction(e -> stepTile(-1));
        nextTile.setOnAction(e -> stepTile(1));
        tileLabel.getStyleClass().add(Styles.TEXT_MUTED);

        excludeTile.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        excludeTile.setOnAction(e -> {
            gridOverlay.toggleExcluded(focusedTile);
            updateTileCount();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        tileRow = new HBox(6, previousTile, tileLabel, nextTile, spacer, excludeTile);
        tileRow.setAlignment(Pos.CENTER_LEFT);
        tileRow.setVisible(false);
        tileRow.setManaged(false);
        HBox.setHgrow(tileRow, Priority.ALWAYS);

        previewToolbar = new VBox(6, pageRow, tileRow);
        previewToolbar.getStyleClass().add("preview-toolbar");
        previewToolbar.setVisible(false);
        previewToolbar.setManaged(false);
        return previewToolbar;
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

        // Turning a page does not change which grids fit it. Replacing the list anyway would empty
        // the picker for an instant, and an empty picker is a grid of none.
        if (candidates.equals(gridBox.getItems())) {
            return;
        }

        syncingGrid = true;
        gridBox.setItems(FXCollections.observableArrayList(candidates));
        gridBox.getSelectionModel().select(
            current != null && candidates.contains(current) ? current : null);
        syncingGrid = false;
    }

    /** A grid and a crop are two ways of saying which part of the image matters; one at a time. */
    private void gridSelected(TileGrid grid) {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        Tiling tiling = grid == null ? Tiling.NONE : grid.tiling();

        if (grid != null && cropToggle.isSelected()) {
            cropToggle.setSelected(false);
        }
        cropToggle.setDisable(grid != null);
        cropBlocked.setVisible(grid != null);
        cropBlocked.setManaged(grid != null);

        gridOverlay.setTiling(tiling);
        gridOverlay.setActive(grid != null);

        // The job has to know the grid before anyone asks it what the pieces are, and the
        // resolution has to be set before the page is rendered to answer that.
        //
        // Not while the picker is only being rebuilt, though. Setting its items empties it for an
        // instant, which arrives here as a choice of no grid — and a job told it has no grid
        // forgets which cells were switched off, which is how turning a page used to lose them.
        if (job != null && !syncingGrid) {
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
        tileRow.setVisible(gridded);
        tileRow.setManaged(gridded);
        updateToolbar();
        if (!gridded) {
            return;
        }

        List<Tile> tiles = currentTiles();
        int position = indexOfFocused(tiles);
        tileLabel.setText(focusedTile == null || position < 0
            ? Messages.get("tiles.pick")
            : Messages.get("tiles.position", position + 1, tiles.size(), focusedTile.label()));
        previousTile.setDisable(tiles.isEmpty() || position <= 0);
        nextTile.setDisable(tiles.isEmpty() || position < 0 || position >= tiles.size() - 1);

        boolean out = focusedTile != null && gridOverlay.excluded().contains(focusedTile.label());
        excludeTile.setDisable(focusedTile == null);
        excludeTile.setText(Messages.get(out ? "tiles.include" : "tiles.exclude"));

        int included = gridOverlay.includedCount();
        boolean anyExcluded = included != tiling.count();
        includeAll.setVisible(anyExcluded);
        includeAll.setManaged(anyExcluded);

        // Offered whenever there is more than one page, and not only when something is switched
        // off: "every tile, everywhere" is as much a thing to say as "not this one, anywhere", and
        // putting them all back on one page is how you take back a decision applied to the lot.
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        boolean manyPages = job != null && job.pageCount() > 1;
        applyToPages.setVisible(manyPages);
        applyToPages.setManaged(manyPages);

        tileCount.setText(Messages.get("tiles.count", included, tiling.count()));
    }

    private void applyExclusionsEverywhere() {
        ConversionJob job = queue.getSelectionModel().getSelectedItem();
        if (job == null) {
            return;
        }
        int cells = job.excludedTiles().size();
        int pages = job.applyExclusionsToAllPages();
        // Said out loud: it changed pages that are not on screen, so nothing else would show it.
        announce(cells == 0
            ? Messages.get("tiles.allIncluded", pages)
            : Messages.plural("tiles.switchedOff", cells, pages));
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
        // Nothing to cut up when nothing is selected, and a grid picker offering to cut up
        // nothing is a question rather than a control.
        tilesSection.setShown(job != null);

        boolean document = job != null && job.isDocument();
        pageRowVisible(document);
        if (document) {
            pageLabel.setText(Messages.get("page.position", job.page(), job.pageCount()));
            previousPage.setDisable(job.page() <= 1);
            nextPage.setDisable(job.page() >= job.pageCount());
        }
    }

    private void pageRowVisible(boolean visible) {
        pageRow.setVisible(visible);
        pageRow.setManaged(visible);
        updateToolbar();
    }

    /** The strip under the picture is in the card only while it has something to say. */
    private void updateToolbar() {
        boolean anything = pageRow.isManaged() || tileRow.isManaged();
        previewToolbar.setVisible(anything);
        previewToolbar.setManaged(anything);
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

        Button collapse = iconButton(chevron, Messages.get("settings.collapse"), fold);
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
        Button chooseOutput = new Button(Messages.get("dock.outputFolder"), new FontIcon(Feather.FOLDER));
        chooseOutput.getStyleClass().add(Styles.FLAT);
        chooseOutput.setOnAction(e -> chooseOutputDir());

        outputLabel.getStyleClass().add(Styles.TEXT_MUTED);

        Button theme = iconButton(Feather.MOON, Messages.get("dock.theme"),
            OmniPlotterApp.Theme::toggle);
        theme.getStyleClass().add(Styles.FLAT);

        // Everything that is not part of converting something, behind one labelled menu. As
        // four unlabelled glyphs in a row, the terminal setup in particular was a thing you had
        // to hover over to discover — and it is the only hint in the window that this application
        // is also a command line.
        Button more = iconButton(Feather.MORE_HORIZONTAL, Messages.get("dock.more"), () -> {});
        more.getStyleClass().add(Styles.FLAT);

        MenuItem logs = new MenuItem(Messages.get("dock.logs"), new FontIcon(Feather.FILE_TEXT));
        logs.setOnAction(e -> Desktops.openFolder(AppPaths.logs()));

        MenuItem terminal = new MenuItem(Messages.get("dock.terminal"), new FontIcon(Feather.TERMINAL));
        terminal.setOnAction(e -> setUpCommand());

        // A moving background is a running repaint, which not everyone wants on a laptop. It
        // was reachable only by right-clicking the moon, which is not somewhere anyone looks.
        CheckMenuItem animated = new CheckMenuItem(Messages.get("dock.animated"));
        animated.setSelected(Settings.getBoolean(Settings.UI_AURORA, true));
        animated.setOnAction(e -> {
            Settings.setBoolean(Settings.UI_AURORA, animated.isSelected());
            Settings.save();
            backdrop.setEnabled(animated.isSelected());
        });

        MenuItem tour = new MenuItem(Messages.get("help.tour.start"), new FontIcon(Feather.MAP));
        tour.setOnAction(e -> help.startTour());

        ContextMenu menu = new ContextMenu(tour, new SeparatorMenuItem(), terminal, logs,
            new SeparatorMenuItem(), animated);
        // Set here rather than at construction: the handler needs the button it is shown on.
        more.setOnAction(e -> menu.show(more, Side.TOP, 0, -6));

        // A ring rather than a 160px bar: in a dock this narrow the bar was most of the width,
        // and the ring says the same thing in the space of a button while also reading the figure
        // out. Kept in the layout when hidden, so pressing Convert does not shuffle the row.
        progress.setVisible(false);
        progress.setMinSize(38, 38);
        progress.setPrefSize(38, 38);
        progress.setMaxSize(38, 38);

        convertButton.setDefaultButton(true);
        convertButton.getStyleClass().addAll(Styles.ACCENT, "hero", "split-left");
        convertButton.setGraphic(new FontIcon(Feather.DOWNLOAD));
        convertButton.setOnAction(e -> convertAll(false));

        everyPage.getStyleClass().add(Styles.FLAT);
        everyPage.setMaxWidth(Double.MAX_VALUE);
        everyPage.setAlignment(Pos.CENTER_LEFT);
        everyPage.setOnAction(e -> {
            showConvertMenu(false);
            convertAll(true);
        });

        convertMenu.getChildren().add(everyPage);
        convertMenu.getStyleClass().add("floating-menu");
        convertMenu.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        convertMenu.setVisible(false);
        convertMenu.setManaged(false);
        StackPane.setAlignment(convertMenu, Pos.TOP_LEFT);

        // Pointing where the menu will appear, which is above the button: up while it is closed,
        // and turned over to point back down while it is open.
        FontIcon arrow = new FontIcon(Feather.CHEVRON_UP);
        convertMore.setGraphic(arrow);
        convertMore.setOnAction(e -> showConvertMenu(!convertMenu.isVisible()));
        convertMenu.visibleProperty().addListener((o, was, now) -> arrow.setRotate(now ? 180 : 0));

        // One path, and this is all of it: the arrow toggles, anything else in the window closes.
        addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (convertMenu.isVisible()
                && !inside(convertMore, e.getSceneX(), e.getSceneY())
                && !inside(convertMenu, e.getSceneX(), e.getSceneY())) {
                showConvertMenu(false);
            }
        });

        convertMore.getStyleClass().addAll(Styles.ACCENT, "hero", "split-right");
        convertMore.setTooltip(new Tooltip(Messages.get("dock.convert.more")));
        // Only for documents: on a queue of photographs there is no second way to do it.
        convertMore.setVisible(false);
        convertMore.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // The two halves are one control, so they are laid out as one: no gap between them, a
        // radius each on its outer side only, and the glow carried by the group rather than by
        // each half, which would otherwise show as two shadows meeting in the middle.
        HBox convertGroup = new HBox(convertButton, convertMore);
        convertGroup.getStyleClass().add("split-button");
        convertGroup.setAlignment(Pos.CENTER_LEFT);

        Button info = iconButton(Feather.INFO, Messages.get("help.open"), () -> help.showConcepts());
        info.getStyleClass().add(Styles.FLAT);

        HBox bar = new HBox(12, chooseOutput, outputLabel, spacer, status, progress,
            info, theme, more, convertGroup);
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

        // The name the calculator itself shows is not prose: it is what appears on its screen.
        String shown = format == Format.ZPIC ? "pic" : format == Format.TI_8CA ? "Image" : "Pic";
        onCalcHint.setText(switch (rule) {
            case SLOT -> Messages.get("name.slot", shown, onCalcNumber.getValue(),
                slots.min(), slots.max());
            case NONE -> Messages.get("name.none");
            default -> Messages.get("name.written",
                OnCalcName.describe(format, targetBox.getValue()));
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
            sourceCaption.setText(Messages.get("preview.none"));
            previewCaption.setText("");
            return;
        }
        BufferedImage src = job.source();
        if (src == null) {
            Animations.swap(sourceView, null);
            cropOverlay.setImage(0, 0, null);
            restoreCropTool(null);
            sourceCaption.setText(Messages.get("preview.unreadable", job.error()));
        } else {
            Animations.swap(sourceView, SwingFXUtils.toFXImage(src, null));
            sourceCaption.setText(Messages.get("preview.size", job.name(),
                String.valueOf(src.getWidth()), String.valueOf(src.getHeight())));
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
            previewCaption.setText(Messages.get("preview.summary", format.id(),
                String.valueOf(out.getWidth()), String.valueOf(out.getHeight()),
                String.valueOf(countColors(out)), humanSize(preview.encodedSize())));

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
            previewCaption.setText(Messages.get("preview.failed", task.getException().getMessage()));
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

    /**
     * Opens or closes the convert menu, putting it just above the arrow that owns it.
     *
     * <p>Placed at show time from the arrow's own position, so it follows the button as the window
     * is resized instead of being pinned to a guess about how tall the action bar is.
     */
    private void showConvertMenu(boolean show) {
        convertMenu.setVisible(show);
        convertMenu.setManaged(show);
        if (show) {
            requestLayout();
        }
    }

    /**
     * Puts the convert menu just above the arrow that owns it.
     *
     * <p>Done here, after the layout pass, and not when it is opened. Measuring it at that moment
     * asks for a size it may not have been given yet, and a height that comes back too small puts
     * the panel over the button instead of above it — where it swallows the very click meant to
     * close it, which is why the arrow sometimes worked and sometimes did nothing.
     *
     * <p>Running every pass also means it follows the button when the window is resized.
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!convertMenu.isVisible()) {
            return;
        }
        var arrow = sceneToLocal(convertMore.localToScene(convertMore.getBoundsInLocal()));
        convertMenu.setTranslateX(Math.max(8, arrow.getMaxX() - convertMenu.getWidth()));
        convertMenu.setTranslateY(arrow.getMinY() - convertMenu.getHeight() - 8);
    }

    /** Whether a point in scene coordinates is over this node. */
    private static boolean inside(Node node, double sceneX, double sceneY) {
        return node.isVisible() && node.localToScene(node.getBoundsInLocal())
            .contains(sceneX, sceneY);
    }

    /**
     * What Convert is about to do, and whether there is a second way to do it.
     *
     * <p>The button said "Convert" and meant "the page each of these files is showing", which is
     * not something a button can leave to be inferred — especially next to a menu item that means
     * the whole document.
     */
    private void updateConvertMenu() {
        boolean anyDocument = jobs.stream().anyMatch(job -> job.pageCount() > 1);
        int queued = jobs.size();

        convertButton.setText(
            queued == 0 ? Messages.get("dock.convert")
                : !anyDocument ? Messages.plural("dock.convert.images", queued)
                : queued == 1 ? Messages.get("dock.convert.page")
                : Messages.get("dock.convert.pages"));

        // The count only where it means something: with two documents queued it is two numbers.
        everyPage.setText(queued == 1 && anyDocument
            ? Messages.get("dock.convert.everyPage.count", jobs.get(0).pageCount())
            : Messages.get("dock.convert.everyPage"));

        convertMore.setVisible(anyDocument);
        convertMore.setManaged(anyDocument);
        // Without its other half, Convert is a button again and has to be shaped like one: the
        // split radius left a flat edge on the side the arrow used to be.
        convertButton.getStyleClass().remove("split-left");
        if (anyDocument) {
            convertButton.getStyleClass().add("split-left");
        }
    }

    private void convertAll(boolean everyPage) {
        if (jobs.isEmpty()) {
            announce(Messages.get("dock.nothing"));
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
                        ConversionOptions perJob = currentOptionsFor(job);
                        written[0] += (everyPage
                            ? job.convertAllPages(format, perJob, destination)
                            : job.convert(format, perJob, destination)).size();
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
            announce(failures.isEmpty()
                ? Messages.plural("dock.written", ok, destination.getFileName())
                : Messages.get("dock.partly", ok, failures.size(), failures.get(0)));
        });
        task.setOnFailed(e -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            convertButton.setDisable(false);
            announce(Messages.get("dock.failed", task.getException().getMessage()));
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

    /**
     * The one filter that has to be right is the first: it is the one the dialog opens on.
     *
     * <p>It listed five raster extensions, so the button could not open a PDF at all — the whole
     * document side of the application was reachable only by dragging a file onto the window.
     */
    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("chooser.add"));
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(Messages.get("chooser.both"),
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp", "*.tif", "*.tiff", "*.pdf"),
            new FileChooser.ExtensionFilter(Messages.get("chooser.images"),
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp", "*.tif", "*.tiff"),
            new FileChooser.ExtensionFilter(Messages.get("chooser.documents"), "*.pdf"));
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
        chooser.setTitle(Messages.get("chooser.output"));
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
            report("setup.noLauncher", paragraph(Messages.get("setup.noLauncher.detail")));
            return;
        }
        try {
            CommandSetup.Outcome outcome =
                CommandSetup.install(CommandSetup.consoleLauncher(launcher.get()));
            if (outcome.onPath()) {
                report("setup.ready", paragraph(Messages.get("setup.ready.detail")), new CommandBlock(HELP));
            } else {
                report("setup.oneStep", oneStepLeft(outcome));
            }
        } catch (IOException e) {
            // A message of null is not worth showing anyone, and String.valueOf printed that word.
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            report("setup.error", paragraph(reason));
        }
    }

    /**
     * What is left to do, and an offer to do it.
     *
     * <p>The command line asks {@code Add it? [y/N]} and then edits the startup file. The window
     * used to show the same line and stop there, which left the one instruction it gave — an
     * {@code export} of a path with a home directory in it — as something to be understood and
     * retyped. The line is still shown, and still nobody's business but the user's; the difference
     * is that agreeing to it is now a button.
     */
    private Node[] oneStepLeft(CommandSetup.Outcome outcome) {
        String line = outcome.shellLine().orElseThrow();
        Label what = paragraph(Messages.get("setup.oneStep.detail",
            shorten(outcome.link().getParent()), CommandSetup.COMMAND));

        if (outcome.shellFile().isEmpty()) {
            // Windows keeps PATH in the registry, so there is no file to offer to edit.
            return new Node[] {
                what,
                paragraph(Messages.get("setup.windows")),
                new CommandBlock(line),
            };
        }

        Path file = outcome.shellFile().get();
        VBox rest = new VBox(10);
        Button add = new Button(Messages.get("setup.add", shorten(file)));
        add.getStyleClass().addAll(Styles.ACCENT, Styles.SMALL);
        add.setOnAction(e -> {
            try {
                CommandSetup.addToShellFile(file, line);
                rest.getChildren().setAll(
                    paragraph(Messages.get("setup.added", shorten(file))),
                    new CommandBlock(HELP));
            } catch (IOException failed) {
                rest.getChildren().setAll(paragraph(
                    Messages.get("setup.addFailed", shorten(file), failed.getMessage())));
            }
        });
        rest.getChildren().add(add);

        return new Node[] {
            what,
            paragraph(Messages.get("setup.line", shorten(file))),
            new CommandBlock(line),
            rest,
        };
    }

    /**
     * A dialog in the window's own styling.
     *
     * <p>Not an {@code Alert}: its content text is a Label, so every command the window has ever
     * proposed was text nobody could select. This one takes nodes, which is what lets a
     * {@link CommandBlock} carry the line instead.
     */
    private void report(String headerKey, Node... body) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(Messages.get("setup.title"));
        dialog.setHeaderText(Messages.get(headerKey));

        VBox content = new VBox(10, body);
        content.setMaxWidth(520);

        DialogPane pane = dialog.getDialogPane();
        // A dialog is a scene of its own, so it does not inherit the window's stylesheet.
        pane.getStylesheets().add(
            OmniPlotterApp.class.getResource("/omniplotter.css").toExternalForm());
        pane.setContent(content);
        pane.setMinWidth(560);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static Label paragraph(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(500);
        return label;
    }

    /** A path as somebody would say it out loud, rather than with their home directory spelled out. */
    private static String shorten(Path path) {
        return path.toString().replace(System.getProperty("user.home"), "~");
    }

    // --- help -------------------------------------------------------------------------------

    /**
     * The tour, over the regions it is about.
     *
     * <p>The stops are the window's own nodes, so there is no second description of the layout to
     * keep in step with the first. The previews are one stop rather than two: which of the two
     * images is the output is a thing to say once, and the accent edge says it afterwards.
     */
    private HelpOverlay buildHelp(Node queueBox, Node settingsBox, Node dock) {
        return new HelpOverlay(List.of(
            new HelpOverlay.Spot(queueBox, "files"),
            new HelpOverlay.Spot(previewPane, "previews"),
            new HelpOverlay.Spot(settingsBox, "steps"),
            new HelpOverlay.Spot(dock, "convert")), helpFooter());
    }

    /** The version, an update check, and which language the window is in. */
    private Node helpFooter() {
        Version running = UpdateCheck.current();
        Label version = new Label(running == null
            ? Messages.get("help.version.dev")
            : Messages.get("help.version", running));
        version.getStyleClass().add(Styles.TEXT_MUTED);

        Label answer = new Label();
        answer.getStyleClass().add(Styles.TEXT_MUTED);
        answer.setWrapText(true);

        Button check = new Button(Messages.get("help.checkUpdates"));
        check.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED);
        check.setOnAction(e -> checkForUpdateNow(check, answer));

        ComboBox<String> language = new ComboBox<>(
            FXCollections.observableArrayList(Messages.AUTOMATIC, "en", "it"));
        language.setConverter(labeller(code -> switch (code) {
            case "en" -> "English";
            case "it" -> "Italiano";
            default -> Messages.get("help.language.auto");
        }));
        language.getSelectionModel().select(Settings.get(Settings.UI_LANGUAGE, Messages.AUTOMATIC));
        language.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                Settings.set(Settings.UI_LANGUAGE, now);
                Settings.save();
            }
        });

        Label restart = new Label(Messages.get("help.language.restart"));
        restart.getStyleClass().add("step-subtitle");
        restart.setWrapText(true);

        Label unsigned = new Label(Messages.get("help.unsigned"));
        unsigned.getStyleClass().add("step-subtitle");
        unsigned.setWrapText(true);

        HBox versionRow = new HBox(10, version, check);
        versionRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(8, new Separator(), versionRow, answer,
            field(Messages.get("help.language"), language), restart, unsigned);
    }

    // --- updates ----------------------------------------------------------------------------

    /**
     * Asks whether a newer release exists, off the interface thread and without blocking startup.
     *
     * <p>Silent unless there is something to say: no spinner, no "you are up to date", nothing at
     * all when the machine is offline.
     */
    private void checkForUpdate() {
        UpdateCheck.inBackground(result -> Platform.runLater(() -> showUpdateBanner(result)));
    }

    private void showUpdateBanner(UpdateCheck.Result result) {
        Node banner = updateBanner(result);
        BorderPane.setMargin(banner, new Insets(GAP, GAP, 0, GAP));
        content.setTop(banner);
        Animations.dropIn(banner);
    }

    /**
     * The check someone asked for, now.
     *
     * <p>The automatic one is silent unless it has news, which is right — and left no way at all to
     * tell a check that works from one that cannot. This one answers either way, including saying
     * what went wrong, and ignores both the daily interval and the skipped version: someone
     * pressing a button is asking.
     */
    private void checkForUpdateNow(Button check, Label answer) {
        check.setDisable(true);
        answer.setText(Messages.get("update.checking"));

        Task<java.util.Optional<UpdateCheck.Result>> task = new Task<>() {
            @Override
            protected java.util.Optional<UpdateCheck.Result> call() throws Exception {
                return UpdateCheck.fetchLatest();
            }
        };
        task.setOnSucceeded(e -> {
            check.setDisable(false);
            Version running = UpdateCheck.current();
            var latest = task.getValue();
            if (latest.isEmpty()) {
                // A private repository answers 404, which is indistinguishable from having no
                // releases. Both are said, because from here they are the same answer.
                answer.setText(Messages.get("update.none"));
            } else if (running == null) {
                answer.setText(Messages.get("update.development", latest.get().latest()));
            } else if (latest.get().latest().isNewerThan(running)) {
                answer.setText(Messages.get("update.available", latest.get().latest()));
                showUpdateBanner(latest.get());
            } else {
                answer.setText(Messages.get("update.current"));
            }
        });
        task.setOnFailed(e -> {
            check.setDisable(false);
            answer.setText(Messages.get("update.unreachable", task.getException().getMessage()));
        });

        Thread thread = new Thread(task, "update-check-now");
        thread.setDaemon(true);
        thread.start();
    }

    private Node updateBanner(UpdateCheck.Result result) {
        Label text = new Label(Messages.get("update.available", result.latest()));

        Button notes = new Button(Messages.get("update.notes"));
        notes.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
        notes.setOnAction(e -> Desktops.openUrl(result.url()));

        // Skipping is remembered, so the same version does not come back tomorrow. Dismissing is
        // not: closing a banner is not the same as saying no.
        Button skip = new Button(Messages.get("update.skip"));
        skip.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        skip.setOnAction(e -> {
            Settings.set(Settings.UPDATE_SKIPPED, result.latest().toString());
            Settings.save();
            hideBanner();
        });

        Button dismiss = iconButton(Feather.X, Messages.get("update.dismiss"), this::hideBanner);
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
     * The tour, once, on a first launch.
     *
     * <p>The only thing in the application that appears without being asked for. Remembered as
     * soon as it is shown rather than when it is finished, because someone who closes it on the
     * first card has answered the question.
     */
    private void firstRun() {
        if (Settings.getBoolean(Settings.UI_HELP_SEEN, false)) {
            return;
        }
        Settings.setBoolean(Settings.UI_HELP_SEEN, true);
        Settings.save();
        // After the intro animation, which is running on the surfaces it would be pointing at.
        PauseTransition wait = new PauseTransition(Duration.millis(900));
        wait.setOnFinished(e -> help.startTour());
        wait.play();
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
        outputLabel.setText(shorten(outputDir));
    }

    /**
     * Something that just happened, for a few seconds.
     *
     * <p>One label carried the queue count, every conversion result, every failure and the
     * apply-to-all-pages confirmation, each one overwriting the last and none of them saying which
     * kind of thing it was. A result now has its moment and then gives the label back.
     */
    private void announce(String text) {
        status.setText(text);
        if (statusRevert != null) {
            statusRevert.stop();
        }
        statusRevert = new PauseTransition(Duration.seconds(6));
        statusRevert.setOnFinished(e -> refreshStatus());
        statusRevert.play();
    }

    private void refreshStatus() {
        status.setText(jobs.isEmpty()
            ? Messages.get("queue.begin")
            : Messages.plural("queue.queued", jobs.size()));
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
