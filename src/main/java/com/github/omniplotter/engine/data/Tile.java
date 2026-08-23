package com.github.omniplotter.engine.data;

/**
 * One piece of a tiled image, and where it sat in the grid.
 *
 * <p>The position is carried alongside the rectangle because everything downstream needs it and
 * none of it can recover it: the file name says which part of the page this is, the window draws
 * the cell that was clicked, and the numbering on the calculator has to run in reading order.
 *
 * @param row    counted from the top, starting at one
 * @param column counted from the left, starting at one
 */
public record Tile(Crop crop, int row, int column) {

    /** How this piece is referred to everywhere a person will read it: {@code r2c3}. */
    public String label() {
        return "r" + row + "c" + column;
    }
}
