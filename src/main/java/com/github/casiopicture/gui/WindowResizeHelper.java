package com.github.casiopicture.gui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class WindowResizeHelper {
    private static final double RESIZE_MARGIN = 12.0;
    
    public static boolean isMaximized = false;
    private static double prevX, prevY, prevWidth, prevHeight;
    
    public static void addResizeListener(Stage stage) {
        Scene scene = stage.getScene();
        if (scene == null) return;
        
        final double[] initialX = new double[1];
        final double[] initialY = new double[1];
        final double[] initialStageX = new double[1];
        final double[] initialStageY = new double[1];
        final double[] initialWidth = new double[1];
        final double[] initialHeight = new double[1];
        final int[] resizeMode = new int[1]; // 0=NONE, 1=N, 2=S, 3=W, 4=E, 5=NW, 6=NE, 7=SW, 8=SE
        
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (isMaximized) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }
            
            double x = event.getSceneX();
            double y = event.getSceneY();
            double w = scene.getWidth();
            double h = scene.getHeight();
            
            boolean left = x <= RESIZE_MARGIN;
            boolean right = x >= w - RESIZE_MARGIN;
            boolean top = y <= RESIZE_MARGIN;
            boolean bottom = y >= h - RESIZE_MARGIN;
            
            if (left && top) {
                scene.setCursor(Cursor.NW_RESIZE);
            } else if (right && top) {
                scene.setCursor(Cursor.NE_RESIZE);
            } else if (left && bottom) {
                scene.setCursor(Cursor.SW_RESIZE);
            } else if (right && bottom) {
                scene.setCursor(Cursor.SE_RESIZE);
            } else if (left) {
                scene.setCursor(Cursor.W_RESIZE);
            } else if (right) {
                scene.setCursor(Cursor.E_RESIZE);
            } else if (top) {
                scene.setCursor(Cursor.N_RESIZE);
            } else if (bottom) {
                scene.setCursor(Cursor.S_RESIZE);
            } else {
                scene.setCursor(Cursor.DEFAULT);
            }
        });
        
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (isMaximized) return;
            
            double x = event.getSceneX();
            double y = event.getSceneY();
            double w = scene.getWidth();
            double h = scene.getHeight();
            
            boolean left = x <= RESIZE_MARGIN;
            boolean right = x >= w - RESIZE_MARGIN;
            boolean top = y <= RESIZE_MARGIN;
            boolean bottom = y >= h - RESIZE_MARGIN;
            
            int mode = 0;
            if (left && top) mode = 5;
            else if (right && top) mode = 6;
            else if (left && bottom) mode = 7;
            else if (right && bottom) mode = 8;
            else if (left) mode = 3;
            else if (right) mode = 4;
            else if (top) mode = 1;
            else if (bottom) mode = 2;
            
            resizeMode[0] = mode;
            
            if (resizeMode[0] != 0) {
                initialX[0] = event.getScreenX();
                initialY[0] = event.getScreenY();
                initialStageX[0] = stage.getX();
                initialStageY[0] = stage.getY();
                initialWidth[0] = stage.getWidth();
                initialHeight[0] = stage.getHeight();
                event.consume(); // Intercept and consume so children don't click
            }
        });
        
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (resizeMode[0] == 0 || isMaximized) return;
            
            double deltaX = event.getScreenX() - initialX[0];
            double deltaY = event.getScreenY() - initialY[0];
            
            double newWidth = initialWidth[0];
            double newHeight = initialHeight[0];
            double newX = initialStageX[0];
            double newY = initialStageY[0];
            
            final double ASPECT_RATIO = 1100.0 / 700.0;
            
            switch (resizeMode[0]) {
                case 1: { // N (Top side)
                    double calcH = initialHeight[0] - deltaY;
                    if (calcH < 500) calcH = 500;
                    newHeight = calcH;
                    newWidth = newHeight * ASPECT_RATIO;
                    newY = initialStageY[0] + (initialHeight[0] - newHeight);
                    newX = initialStageX[0] - (newWidth - initialWidth[0]) / 2.0;
                    break;
                }
                case 2: { // S (Bottom side)
                    double calcH = initialHeight[0] + deltaY;
                    if (calcH < 500) calcH = 500;
                    newHeight = calcH;
                    newWidth = newHeight * ASPECT_RATIO;
                    newX = initialStageX[0] - (newWidth - initialWidth[0]) / 2.0;
                    break;
                }
                case 3: { // W (Left side)
                    double calcW = initialWidth[0] - deltaX;
                    if (calcW < 800) calcW = 800;
                    newWidth = calcW;
                    newHeight = newWidth / ASPECT_RATIO;
                    newX = initialStageX[0] + (initialWidth[0] - newWidth);
                    newY = initialStageY[0] - (newHeight - initialHeight[0]) / 2.0;
                    break;
                }
                case 4: { // E (Right side)
                    double calcW = initialWidth[0] + deltaX;
                    if (calcW < 800) calcW = 800;
                    newWidth = calcW;
                    newHeight = newWidth / ASPECT_RATIO;
                    newY = initialStageY[0] - (newHeight - initialHeight[0]) / 2.0;
                    break;
                }
                case 5: { // NW (Top-Left)
                    double scale = Math.max((initialWidth[0] - deltaX) / initialWidth[0], (initialHeight[0] - deltaY) / initialHeight[0]);
                    newWidth = initialWidth[0] * scale;
                    if (newWidth < 800) {
                        newWidth = 800;
                        scale = newWidth / initialWidth[0];
                    }
                    newHeight = initialHeight[0] * scale;
                    newX = initialStageX[0] + (initialWidth[0] - newWidth);
                    newY = initialStageY[0] + (initialHeight[0] - newHeight);
                    break;
                }
                case 6: { // NE (Top-Right)
                    double scale = Math.max((initialWidth[0] + deltaX) / initialWidth[0], (initialHeight[0] - deltaY) / initialHeight[0]);
                    newWidth = initialWidth[0] * scale;
                    if (newWidth < 800) {
                        newWidth = 800;
                        scale = newWidth / initialWidth[0];
                    }
                    newHeight = initialHeight[0] * scale;
                    newY = initialStageY[0] + (initialHeight[0] - newHeight);
                    break;
                }
                case 7: { // SW (Bottom-Left)
                    double scale = Math.max((initialWidth[0] - deltaX) / initialWidth[0], (initialHeight[0] + deltaY) / initialHeight[0]);
                    newWidth = initialWidth[0] * scale;
                    if (newWidth < 800) {
                        newWidth = 800;
                        scale = newWidth / initialWidth[0];
                    }
                    newHeight = initialHeight[0] * scale;
                    newX = initialStageX[0] + (initialWidth[0] - newWidth);
                    break;
                }
                case 8: { // SE (Bottom-Right)
                    double scale = Math.max((initialWidth[0] + deltaX) / initialWidth[0], (initialHeight[0] + deltaY) / initialHeight[0]);
                    newWidth = initialWidth[0] * scale;
                    if (newWidth < 800) {
                        newWidth = 800;
                        scale = newWidth / initialWidth[0];
                    }
                    newHeight = initialHeight[0] * scale;
                    break;
                }
            }
            
            stage.setX(newX);
            stage.setY(newY);
            stage.setWidth(newWidth);
            stage.setHeight(newHeight);
            event.consume();
        });
    }
    
    public static void toggleMaximize(Stage stage) {
        double centerX = stage.getX() + stage.getWidth() / 2;
        double centerY = stage.getY() + stage.getHeight() / 2;
        
        Screen targetScreen = Screen.getPrimary();
        for (Screen scr : Screen.getScreens()) {
            if (scr.getVisualBounds().contains(centerX, centerY)) {
                targetScreen = scr;
                break;
            }
        }
        
        Rectangle2D bounds = targetScreen.getVisualBounds();
        
        if (isMaximized) {
            stage.setX(prevX);
            stage.setY(prevY);
            stage.setWidth(prevWidth);
            stage.setHeight(prevHeight);
            isMaximized = false;
        } else {
            prevX = stage.getX();
            prevY = stage.getY();
            prevWidth = stage.getWidth();
            prevHeight = stage.getHeight();
            
            // For custom maximize, we also lock to screen visual bounds but keep ratio if desired.
            // However, native maximize should cover the whole visual bounds.
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            isMaximized = true;
        }
    }
}
