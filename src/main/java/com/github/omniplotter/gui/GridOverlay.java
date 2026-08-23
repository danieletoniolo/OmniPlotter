package com.github.omniplotter.gui;

import com.github.omniplotter.engine.data.Tile;
import com.github.omniplotter.engine.data.Tiling;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The grid drawn over the page, and the cells switched off in it.
 *
 * <p>Nobody wants the running header as one of their twelve images, so a cell can be excluded with
 * a click. Excluding is the only gesture: the preview follows whatever was clicked last, whether it
 * is in or out, so what you touched is always what you are looking at.
 *
 * <p>Hit testing uses the grid without its overlap. The tiles themselves reach into each other, so
 * a point near a cut belongs to two of them, and "the cell you clicked" has to mean the one drawn
 * under the pointer rather than whichever tile happens to be first in the list.
 */
public class GridOverlay extends Pane {

    private final ImageView view;

    private final Map<String, Rectangle> cells = new LinkedHashMap<>();
    private final Set<String> excluded = new HashSet<>();

    private int imageWidth;
    private int imageHeight;
    private Tiling tiling = Tiling.NONE;
    private String focused;

    private Consumer<Tile> onTileClicked = tile -> { };
    private Runnable onChange = () -> { };

    public GridOverlay(ImageView view) {
        this.view = view;
        setMouseTransparent(true);
        getStyleClass().add("grid-overlay");
        setCursor(Cursor.HAND);

        // Clicking picks a cell to look at. Excluding it is a separate thing to say, because one
        // gesture that both shows you a tile and throws it away is a gesture you cannot use to
        // look around.
        setOnMouseClicked(e -> {
            Tile tile = tileAt(e.getX(), e.getY());
            if (tile == null) {
                return;
            }
            focused = tile.label();
            rebuild();
            onTileClicked.accept(tile);
            e.consume();
        });
    }

    public void setOnTileClicked(Consumer<Tile> onTileClicked) {
        this.onTileClicked = onTileClicked;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Drawn whenever there is a grid to draw; the clicking is what switching on enables. */
    public void setActive(boolean active) {
        setMouseTransparent(!active);
    }

    public void setImage(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;
        rebuild();
    }

    /**
     * A different grid means the old exclusions describe cells that no longer exist.
     *
     * <p>Keeping them would mean a cell nobody switched off coming back excluded because something
     * in the same position once was.
     */
    public void setTiling(Tiling tiling) {
        if (!this.tiling.equals(tiling)) {
            excluded.clear();
            focused = null;
        }
        this.tiling = tiling;
        rebuild();
    }

    public Tiling tiling() {
        return tiling;
    }

    public Set<String> excluded() {
        return Set.copyOf(excluded);
    }

    /** Which cell is being looked at, so the preview and the outline agree. */
    public void setFocused(Tile tile) {
        this.focused = tile == null ? null : tile.label();
        rebuild();
    }

    /** Switches one cell off, or back on. The only way a cell leaves the conversion. */
    public void toggleExcluded(Tile tile) {
        if (tile == null) {
            return;
        }
        if (!excluded.remove(tile.label())) {
            excluded.add(tile.label());
        }
        rebuild();
        onChange.run();
    }

    /** Puts back what this image had switched off, without treating it as a fresh decision. */
    public void setExcluded(Set<String> labels) {
        excluded.clear();
        excluded.addAll(labels);
        focused = null;
        rebuild();
    }

    public boolean isExcluded(Tile tile) {
        return excluded.contains(tile.label());
    }

    /** How many pieces will actually be converted, which is what the interface has to report. */
    public int includedCount() {
        return tiling.count() - excluded.size();
    }

    public void includeEverything() {
        excluded.clear();
        rebuild();
        onChange.run();
    }

    // --- geometry ---------------------------------------------------------------------------

    private Fit fit() {
        return Fit.of(getWidth(), getHeight(), imageWidth, imageHeight);
    }

    /** The cell under a point on screen; which cell that is belongs to the geometry, not here. */
    private Tile tileAt(double screenX, double screenY) {
        if (imageWidth <= 0 || tiling.isWhole()) {
            return null;
        }
        Fit fit = fit();
        return tiling.tileAt(imageWidth, imageHeight,
            fit.toImageX(screenX), fit.toImageY(screenY));
    }

    private void rebuild() {
        getChildren().clear();
        cells.clear();
        if (imageWidth <= 0 || tiling.isWhole() || !tiling.fits(imageWidth, imageHeight)) {
            return;
        }
        for (int row = 1; row <= tiling.rows(); row++) {
            for (int column = 1; column <= tiling.columns(); column++) {
                String label = "r" + row + "c" + column;
                Rectangle cell = new Rectangle();
                cell.setMouseTransparent(true);
                cell.setStrokeWidth(1);
                boolean out = excluded.contains(label);
                cell.setFill(out ? Color.rgb(0, 0, 0, 0.55) : Color.TRANSPARENT);
                cell.setStroke(label.equals(focused)
                    ? Color.web("#4dabf7")
                    : Color.rgb(255, 255, 255, 0.75));
                cell.setStrokeWidth(label.equals(focused) ? 2 : 1);
                cells.put(label, cell);
                getChildren().add(cell);
            }
        }
        layoutCells();
    }

    private void layoutCells() {
        if (cells.isEmpty() || getWidth() <= 0) {
            return;
        }
        Fit fit = fit();
        for (int row = 1; row <= tiling.rows(); row++) {
            for (int column = 1; column <= tiling.columns(); column++) {
                Rectangle cell = cells.get("r" + row + "c" + column);
                if (cell == null) {
                    continue;
                }
                // Drawn on the plain grid: the overlap is real but showing it would draw every cut
                // twice and make the page look like it had been cut somewhere it had not.
                double left = fit.toScreenX((double) (column - 1) * imageWidth / tiling.columns());
                double right = fit.toScreenX((double) column * imageWidth / tiling.columns());
                double top = fit.toScreenY((double) (row - 1) * imageHeight / tiling.rows());
                double bottom = fit.toScreenY((double) row * imageHeight / tiling.rows());
                cell.setX(left);
                cell.setY(top);
                cell.setWidth(Math.max(0, right - left));
                cell.setHeight(Math.max(0, bottom - top));
            }
        }
    }

    @Override
    protected void layoutChildren() {
        layoutCells();
    }
}
