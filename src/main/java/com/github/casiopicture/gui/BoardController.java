package com.github.casiopicture.gui;

import com.github.casiopicture.engine.EngineApi;
import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.data.FormatConfig;
import com.github.casiopicture.engine.data.Mode;
import com.github.casiopicture.engine.data.Target;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXListView;
import io.github.palexdev.materialfx.controls.MFXProgressBar;
import io.github.palexdev.materialfx.controls.MFXToggleButton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public class BoardController {
    @FXML
    public MFXListView<File> fileListView;
    
    @FXML
    public MFXComboBox<String> modeBox;

    @FXML
    public MFXComboBox<String> targetBox;

    @FXML
    public MFXComboBox<String> formatBox;
    
    @FXML
    public Spinner<Integer> widthSpinner;
    
    @FXML
    public Spinner<Integer> heightSpinner;
    
    @FXML
    public Spinner<Integer> colorsSpinner;
    
    @FXML
    public MFXToggleButton aspectRatioToggle;
    
    @FXML
    public MFXToggleButton fitToggle;

    @FXML
    public MFXButton addFilesButton;
    
    @FXML
    public MFXButton removeButton;
    
    @FXML
    public MFXButton clearButton;
    
    @FXML
    public MFXButton convertAllButton;
    
    @FXML
    public MFXProgressBar progressBar;
    
    @FXML
    public Label progressLabel;
    
    @FXML
    public StackPane previewPane;

    @FXML
    public Label metaNameLabel;

    @FXML
    public Label metaDimLabel;

    @FXML
    public Label metaSizeLabel;

    @FXML
    public Label metaTypeLabel;

    @FXML
    public HBox titleBar;

    @FXML
    public MFXButton minimizeButton;

    @FXML
    public MFXButton maximizeButton;

    @FXML
    public MFXButton closeButton;


    private final ObservableList<File> fileList = FXCollections.observableArrayList();
    private File outputDirectory = new File(System.getProperty("user.home"));
    private ImageView previewImageView;
    private Stage stage;

    private Mode currentMode = Mode.VAR;
    private Target currentTarget = Target.CASIO_CG;

    public void initialize(Stage stage) {
        this.stage = stage;

        // Setup custom window control buttons
        closeButton.setOnAction(event -> stage.close());
        minimizeButton.setOnAction(event -> stage.setIconified(true));
        maximizeButton.setOnAction(event -> {
            WindowResizeHelper.toggleMaximize(stage);
            maximizeButton.setText(WindowResizeHelper.isMaximized ? "🗗" : "⤢");
        });

        // Setup custom title bar dragging
        final double[] xOffset = new double[1];
        final double[] yOffset = new double[1];
        titleBar.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            if (!WindowResizeHelper.isMaximized) {
                stage.setX(event.getScreenX() - xOffset[0]);
                stage.setY(event.getScreenY() - yOffset[0]);
            }
        });
        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                WindowResizeHelper.toggleMaximize(stage);
                maximizeButton.setText(WindowResizeHelper.isMaximized ? "🗗" : "⤢");
            }
        });

        // Setup file list view
        fileListView.setItems(fileList);
        fileListView.getSelectionModel().selectionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                loadPreview(newVal.values().iterator().next());
            }
        });

        // Setup mode box
        modeBox.getItems().setAll("Variable (Binary)", "Python Script");
        modeBox.selectedItemProperty().addListener((obs, oldVal, newVal) -> onModeChanged(newVal));

        // Setup target box - will be populated based on mode
        targetBox.selectedItemProperty().addListener((obs, oldVal, newVal) -> onTargetChanged(newVal));

        // Setup format box - will be populated based on target
        formatBox.selectedItemProperty().addListener((obs, oldVal, newVal) -> applyFormatDefaults(newVal));

        // Initialize spinners
        initializeSpinner(widthSpinner, 1, 10000, 320);
        initializeSpinner(heightSpinner, 1, 10000, 240);
        initializeSpinner(colorsSpinner, 1, 65536, 256);

        // Setup aspect ratio toggle
        aspectRatioToggle.setSelected(true);

        // Setup fit toggle (enlarge smaller images)
        fitToggle.setSelected(false);

        // Setup button event handlers
        addFilesButton.setOnAction(event -> addFiles());
        removeButton.setOnAction(event -> removeSelectedFile());
        clearButton.setOnAction(event -> clearFiles());
        convertAllButton.setOnAction(event -> runBatchConversion());

        // Setup preview pane with responsive bindings to container bounds
        previewImageView = new ImageView();
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);
        previewImageView.fitWidthProperty().bind(previewPane.widthProperty().subtract(20));
        previewImageView.fitHeightProperty().bind(previewPane.heightProperty().subtract(20));
        previewPane.getChildren().clear();
        previewPane.getChildren().add(previewImageView);

        // Set default mode (this will trigger target and format population)
        modeBox.selectItem("Variable (Binary)");

        progressBar.setProgress(0.0);
        progressLabel.setText("Ready to convert");
        updateStatus();
    }

    private void onModeChanged(String modeStr) {
        if (modeStr == null) return;

        currentMode = modeStr.startsWith("Variable") ? Mode.VAR : Mode.SCRIPT;

        // Populate targets for this mode
        List<Target> targets = Target.getByMode(currentMode);
        List<String> targetNames = targets.stream()
            .map(t -> t.getDisplayName() + " [" + t.getId() + "]")
            .toList();

        targetBox.getItems().setAll(targetNames);

        // Select first target or restore previous if still valid
        String previousTargetId = currentTarget.getId();
        boolean foundPrevious = false;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).getId().equals(previousTargetId)) {
                targetBox.selectItem(targetNames.get(i));
                foundPrevious = true;
                break;
            }
        }
        if (!foundPrevious && !targetNames.isEmpty()) {
            targetBox.selectItem(targetNames.get(0));
        }
    }

    private void onTargetChanged(String targetStr) {
        if (targetStr == null) return;

        // Extract target ID from display string "Display Name [id]"
        int start = targetStr.lastIndexOf('[');
        int end = targetStr.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String targetId = targetStr.substring(start + 1, end);
            try {
                currentTarget = Target.fromString(targetId);
            } catch (IllegalArgumentException e) {
                return;
            }
        }

        // Populate formats for this target and mode
        List<Format> formats = currentTarget.getSupportedFormats(currentMode);
        List<String> formatNames = formats.stream()
            .map(Format::toString)
            .toList();

        formatBox.getItems().setAll(formatNames);

        // Select default format for this target
        Format defaultFormat = currentTarget.getDefaultFormat(currentMode);
        if (defaultFormat != null && formatNames.contains(defaultFormat.toString())) {
            formatBox.selectItem(defaultFormat.toString());
        } else if (!formatNames.isEmpty()) {
            formatBox.selectItem(formatNames.get(0));
        }
    }

    private void initializeSpinner(Spinner<Integer> spinner, int min, int max, int initial) {
        spinner.setEditable(true);
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, clamp(initial, min, max)));
    }

    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose images to convert");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.tif", "*.tiff"));
        List<File> chosen = chooser.showOpenMultipleDialog(stage);
        if (chosen != null && !chosen.isEmpty()) {
            fileList.addAll(chosen);
            updateStatus();
            if (!fileList.isEmpty()) {
                fileListView.getSelectionModel().selectItem(fileList.get(0));
            }
        }
    }

    private void removeSelectedFile() {
        var selection = fileListView.getSelectionModel().getSelection();
        if (!selection.isEmpty()) {
            File selectedFile = selection.values().iterator().next();
            int selectedIndex = fileList.indexOf(selectedFile);
            fileList.remove(selectedIndex);
            updateStatus();
            if (!fileList.isEmpty() && selectedIndex < fileList.size()) {
                fileListView.getSelectionModel().selectItem(fileList.get(selectedIndex));
            } else if (!fileList.isEmpty()) {
                fileListView.getSelectionModel().selectItem(fileList.get(fileList.size() - 1));
            } else {
                previewImageView.setImage(null);
            }
        }
    }

    private void clearFiles() {
        fileList.clear();
        previewImageView.setImage(null);
        progressLabel.setText("Ready to convert");
        resetMetadata();
        updateStatus();
    }

    private void updateStatus() {
        int count = fileList.size();
        if (count == 0) {
            progressLabel.setText("Ready to convert");
            convertAllButton.setDisable(true);
            resetMetadata();
        } else {
            progressLabel.setText(count + " image(s) selected");
            convertAllButton.setDisable(false);
        }
    }

    private void loadPreview(File file) {
        try {
            // Load original image to extract metadata dimensions correctly (no downscaling requested here)
            Image image = new Image(file.toURI().toString(), true); // Load in background
            
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() == 1.0 && !image.isError()) {
                    previewImageView.setImage(image);
                    previewImageView.setOpacity(1.0);
                    updateMetadata(file, image);
                }
            });
            
            if (!image.isBackgroundLoading() || image.getProgress() == 1.0) {
                previewImageView.setImage(image);
                previewImageView.setOpacity(1.0);
                updateMetadata(file, image);
            }
        } catch (Exception ex) {
            previewImageView.setImage(null);
            progressLabel.setText("Preview failed: " + ex.getMessage());
            resetMetadata();
        }
    }

    private void updateMetadata(File file, Image image) {
        metaNameLabel.setText(file.getName());
        metaDimLabel.setText((int) image.getWidth() + " × " + (int) image.getHeight() + " px");
        
        long bytes = file.length();
        if (bytes < 1024) {
            metaSizeLabel.setText(bytes + " B");
        } else if (bytes < 1024 * 1024) {
            metaSizeLabel.setText(String.format("%.1f KB", bytes / 1024.0));
        } else {
            metaSizeLabel.setText(String.format("%.1f MB", bytes / (1024.0 * 1024.0)));
        }
        
        metaTypeLabel.setText(formatBox.getSelectedItem() != null ? formatBox.getSelectedItem() : "-");
    }

    private void resetMetadata() {
        metaNameLabel.setText("-");
        metaDimLabel.setText("-");
        metaSizeLabel.setText("-");
        metaTypeLabel.setText("-");
    }

    private void applyFormatDefaults(String format) {
        if (format == null) {
            return;
        }

        if (metaTypeLabel != null) {
            metaTypeLabel.setText(format);
        }

        FormatConfig config = FormatConfig.of(format);
        if (config == null) {
            return;
        }

        updateSpinner(widthSpinner, 1, config.maxWidth(), config.defaultWidth());
        updateSpinner(heightSpinner, 1, config.maxHeight(), config.defaultHeight());

        int currentColors = Objects.requireNonNullElse(colorsSpinner.getValue(), config.maxColors());
        updateSpinner(colorsSpinner, 1, config.maxColors(), currentColors > 0 ? currentColors : config.maxColors());

        boolean editable = config.editableSize();
        widthSpinner.setDisable(!editable);
        heightSpinner.setDisable(!editable);
    }

    private void updateSpinner(Spinner<Integer> spinner, int min, int max, int value) {
        int safeValue = clamp(value, min, max);
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, safeValue));
        spinner.setEditable(true);
    }

    private void runBatchConversion() {
        if (fileList.isEmpty()) {
            showError("Please add images first.");
            return;
        }
        String format = formatBox.getSelectedItem();
        if (format == null) {
            showError("Please choose a target format.");
            return;
        }

        // Ask for output directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Choose output directory");
        dirChooser.setInitialDirectory(outputDirectory);
        File outputDir = dirChooser.showDialog(stage);
        if (outputDir == null) {
            return;
        }
        outputDirectory = outputDir;

        // Start batch conversion
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                int successCount = 0;
                int totalFiles = fileList.size();

                for (int i = 0; i < totalFiles; i++) {
                    if (isCancelled()) {
                        break;
                    }

                    File inputFile = fileList.get(i);
                    updateProgress(i, totalFiles);
                    updateMessage("Converting " + (i + 1) + " of " + totalFiles + ": " + inputFile.getName());

                    try {
                        byte[] inputBytes = Files.readAllBytes(inputFile.toPath());
                        int width = widthSpinner.getValue();
                        int height = heightSpinner.getValue();
                        int colors = colorsSpinner.getValue();

                        boolean preserveRatio = aspectRatioToggle.isSelected();
                        boolean enlargeSmaller = fitToggle.isSelected();
                        ConversionOptions options = ConversionOptions
                            .defaults(currentTarget, Format.fromString(format))
                            .withSize(width, height).withColors(colors)
                            .withKeepRatio(preserveRatio).withEnlargeSmaller(enlargeSmaller);

                        ConversionResult result = EngineApi.convert(inputBytes, inputFile.getName(), format, options);

                        // Save converted file
                        File outputFile = new File(outputDir, result.suggestedFileName());
                        Files.write(outputFile.toPath(), result.fileBytes());
                        successCount++;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                return successCount;
            }
        };

        task.setOnRunning(e -> setBusy(true));
        task.setOnSucceeded(e -> {
            setBusy(false);
            int converted = task.getValue();
            int total = fileList.size();
            progressLabel.setText("Conversion complete: " + converted + "/" + total + " files converted");
            showInfo("Batch Conversion Complete", "Successfully converted " + converted + " of " + total + " images.\nOutput directory: " + outputDirectory.getAbsolutePath());
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable error = task.getException();
            progressLabel.setText("Conversion failed");
            showError(error != null ? error.getMessage() : "Unknown error during conversion");
        });

        task.progressProperty().addListener((obs, oldVal, newVal) -> {
            progressBar.setProgress(newVal.doubleValue());
        });

        task.messageProperty().addListener((obs, oldVal, newVal) -> {
            progressLabel.setText(newVal);
        });

        Thread worker = new Thread(task, "batch-conversion-task");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        convertAllButton.setDisable(busy);
        addFilesButton.setDisable(busy);
        formatBox.setDisable(busy);
        progressBar.setVisible(busy);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Conversion Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
