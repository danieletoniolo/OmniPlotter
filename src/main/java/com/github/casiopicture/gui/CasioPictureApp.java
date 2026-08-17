package com.github.casiopicture.gui;

import io.github.palexdev.materialfx.layout.ScalableContentPane;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;
import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class CasioPictureApp extends Application {

    @Override
    public void start(Stage stage) {
        try {
            // Load custom fonts
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/fonts/Outfit-Regular.ttf"), 14);
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/fonts/Outfit-SemiBold.ttf"), 14);
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/fonts/Outfit-Bold.ttf"), 14);

            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/board.fxml"));
            VBox root = loader.load();
            
            // Get the controller
            BoardController controller = loader.getController();

            // Initialize the controller with the stage (configs loaded on-demand per format)
            controller.initialize(stage);

            // Apply MaterialFX theme
            UserAgentBuilder.builder()
                    .themes(MaterialFXStylesheets.forAssemble(true))
                    .setDeploy(true)
                    .setResolveAssets(true)
                    .build()
                    .setGlobal();

            // Setup and show stage in custom borderless transparent mode
            stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            stage.setTitle("CasioPicture - Batch Image Converter");
            
            // Create scene with root directly for responsive layout
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
            
            stage.setScene(scene);
            stage.setWidth(1100);
            stage.setHeight(700);
            stage.setResizable(true);
            stage.show();

            // Setup custom window resizing helper
            WindowResizeHelper.addResizeListener(stage);
        } catch (Exception e) {
            showError("Failed to load UI: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Application Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
