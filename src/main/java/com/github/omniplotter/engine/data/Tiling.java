package com.github.omniplotter.engine.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Cutting one image into a grid of pieces, each of which becomes its own file.
 *
 * <p>Deliberately knows nothing about documents. A tile is a {@link Crop}, and converting a crop of
 * an image is what the engine already does, so this is arithmetic and not a second pipeline. That is
 * also what makes it work on a large scan without a PDF anywhere in sight.
 *
 * @param rows    pieces from top to bottom
 * @param columns pieces from left to right — the number that decides whether text is legible, since
 *                cutting horizontally adds no resolution across the width of the page
 * @param overlap fraction of a cell by which each piece reaches into its neighbours, so that a line
 *                of text falling on a cut survives in one of them rather than being halved in both
 */
public record Tiling(int rows, int columns, double overlap) {

    /** The whole image, in one piece. What every conversion does unless asked otherwise. */
    public static final Tiling NONE = new Tiling(1, 1, 0);

    /** Enough to save a line of text, little enough not to read as duplication. */
    public static final double DEFAULT_OVERLAP = 0.03;

    public Tiling {
        if (rows < 1 || columns < 1) {
            throw new IllegalArgumentException("A grid needs at least one row and column, got "
                + rows + "x" + columns);
        }
        if (overlap < 0 || overlap > 0.5) {
            throw new IllegalArgumentException("Overlap runs from 0 to 0.5, got " + overlap);
        }
    }

    public static Tiling of(int rows, int columns) {
        return new Tiling(rows, columns, DEFAULT_OVERLAP);
    }

    public int count() {
        return rows * columns;
    }

    /** Whether this would change anything, and so whether the image has to be touched at all. */
    public boolean isWhole() {
        return rows == 1 && columns == 1;
    }

    /** An image has to have at least one pixel per cell before it can be cut into them. */
    public boolean fits(int imageWidth, int imageHeight) {
        return imageWidth >= columns && imageHeight >= rows;
    }

    /**
     * The pieces, in reading order.
     *
     * <p>Cell edges are computed from the image size rather than accumulated from a cell width, so
     * rounding cannot leave a seam of unconverted pixels between two tiles or run off the end.
     * Overlap is added afterwards and clipped, which is why the tiles nearest an edge are slightly
     * smaller than the ones in the middle.
     */
    public List<Tile> tilesOf(int imageWidth, int imageHeight) {
        if (!fits(imageWidth, imageHeight)) {
            throw new IllegalArgumentException("An image of " + imageWidth + "x" + imageHeight
                + " cannot be cut into " + rows + "x" + columns + " pieces");
        }

        List<Tile> tiles = new ArrayList<>(count());
        for (int row = 0; row < rows; row++) {
            int top = edge(row, rows, imageHeight);
            int bottom = edge(row + 1, rows, imageHeight);
            int marginY = (int) Math.round((bottom - top) * overlap);

            for (int column = 0; column < columns; column++) {
                int left = edge(column, columns, imageWidth);
                int right = edge(column + 1, columns, imageWidth);
                int marginX = (int) Math.round((right - left) * overlap);

                int x = Math.max(0, left - marginX);
                int y = Math.max(0, top - marginY);
                int width = Math.min(imageWidth, right + marginX) - x;
                int height = Math.min(imageHeight, bottom + marginY) - y;

                tiles.add(new Tile(new Crop(x, y, width, height), row + 1, column + 1));
            }
        }
        return tiles;
    }

    private static int edge(int index, int divisions, int total) {
        return (int) Math.round((double) index * total / divisions);
    }

    /** Reads {@code 9x3}: rows first, the way the grid is written everywhere else here. */
    public static Tiling parse(String value) {
        String[] parts = value.trim().toLowerCase().split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException("A grid is rows x columns, like 9x3 — got: " + value);
        }
        try {
            return of(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A grid is two whole numbers, like 9x3 — got: " + value);
        }
    }

    @Override
    public String toString() {
        return rows + "x" + columns;
    }
}
