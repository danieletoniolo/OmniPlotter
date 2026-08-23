package com.github.omniplotter.engine.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A grid that could be used for a page, and what it would actually give you.
 *
 * <p>This type exists because "how many pieces?" is the wrong question to ask anyone. Three
 * horizontal bands are very nearly a perfect fit for a 2:1 screen and are the obvious thing to
 * reach for, and they are useless for text: cutting horizontally does not add a single pixel across
 * the width of the page, so the resolution stays where it was however many rows there are.
 * Resolution comes only from columns.
 *
 * <p>So the choice is offered as the grids that fit, each carrying the number that decides the
 * outcome — how tall a line of text ends up being. That is the same bargain {@link OutputLimits}
 * strikes for file sizes: find out here, not standing in front of the calculator.
 *
 * @param dpi        resolution of the page as converted, in dots per inch
 * @param lineHeight how many pixels tall a ten-point line of text ends up
 * @param waste      fraction of the screen left unused because the tile is the wrong shape for it
 */
public record TileGrid(int rows, int columns, int dpi, double lineHeight, double waste) {

    /** A ten-point line is the ordinary size of body text, and the thing being judged. */
    private static final double BODY_TEXT_POINTS = 10;

    /** Past this the tile is the wrong shape for the screen badly enough not to be worth offering. */
    private static final double MAX_WASTE = 0.25;

    public Tiling tiling() {
        return Tiling.of(rows, columns);
    }

    public int count() {
        return rows * columns;
    }

    /**
     * Roughly what a reader needs.
     *
     * <p>Not a threshold the code enforces — legibility is a judgement and screens differ — but the
     * interface has to say something, and eleven pixels is about where a line of body text stops
     * being a smudge.
     */
    public boolean isLegible() {
        return lineHeight >= 11;
    }

    /**
     * One grid per column count: the row count that wastes least of the screen.
     *
     * <p>Enumerated by columns because that is the axis that carries resolution, and the rows then
     * follow from the shape of the screen. Grids that would leave a quarter of the screen empty are
     * dropped rather than offered with a warning nobody reads.
     */
    public static List<TileGrid> candidatesFor(PageSize page, int canvasWidth, int canvasHeight, int maxColumns) {
        double screenAspect = (double) canvasWidth / canvasHeight;
        List<TileGrid> grids = new ArrayList<>();

        for (int columns = 1; columns <= maxColumns; columns++) {
            // A cell is pageWidth/columns by pageHeight/rows, so its aspect is the page's scaled by
            // rows over columns. Setting that equal to the screen's gives the rows to aim for.
            int rows = (int) Math.max(1, Math.round(columns * screenAspect / page.aspect()));
            double cellAspect = page.aspect() * rows / columns;
            double waste = 1 - Math.min(cellAspect, screenAspect) / Math.max(cellAspect, screenAspect);
            if (waste > MAX_WASTE) {
                continue;
            }

            int dpi = (int) Math.round(canvasWidth * columns / page.widthInches());
            grids.add(new TileGrid(rows, columns, dpi, dpi * BODY_TEXT_POINTS / 72, waste));
        }
        return grids;
    }

    public static List<TileGrid> candidatesFor(PageSize page, int canvasWidth, int canvasHeight) {
        // Four columns of A4 onto a calculator is twelve tiles a row and past the point of use.
        return candidatesFor(page, canvasWidth, canvasHeight, 4);
    }

    @Override
    public String toString() {
        return rows + "x" + columns;
    }
}
