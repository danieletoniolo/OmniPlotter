package com.github.omniplotter;

import com.github.omniplotter.engine.data.PageSize;
import com.github.omniplotter.engine.data.Tile;
import com.github.omniplotter.engine.data.TileGrid;
import com.github.omniplotter.engine.data.Tiling;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cutting a page up, and choosing how.
 *
 * <p>Arithmetic rather than pixels, because arithmetic is what decides whether the result can be
 * read. A grid that leaves a line of text six pixels tall is a correct conversion of an unusable
 * picture, and nothing downstream can recover from it.
 */
class TilingTest {

    /** Which pixels of an image the tiles between them account for. */
    private static int[][] coverage(Tiling tiling, int width, int height) {
        int[][] counts = new int[height][width];
        for (Tile tile : tiling.tilesOf(width, height)) {
            var crop = tile.crop();
            for (int y = crop.y(); y < crop.y() + crop.height(); y++) {
                for (int x = crop.x(); x < crop.x() + crop.width(); x++) {
                    counts[y][x]++;
                }
            }
        }
        return counts;
    }

    @Test
    void resolutionComesFromColumnsAndNotFromRows() {
        // The table this project's roadmap argues from: A4 onto a 384-pixel-wide screen.
        List<TileGrid> grids = TileGrid.candidatesFor(PageSize.A4, 384, 192);

        assertEquals(46, dpiForColumns(grids, 1));
        assertEquals(93, dpiForColumns(grids, 2));
        assertEquals(139, dpiForColumns(grids, 3));

        // Three horizontal bands are the obvious thing to reach for, and they add nothing: the one
        // column is the whole width of the page however many times it is cut across.
        assertEquals(3, rowsForColumns(grids, 1));
        assertEquals(46, dpiForColumns(grids, 1));
    }

    @Test
    void aFourThreeScreenGetsItsOwnFamilyOfGrids() {
        List<TileGrid> grids = TileGrid.candidatesFor(PageSize.A4, 320, 240);

        assertEquals(2, rowsForColumns(grids, 1));
        assertEquals(4, rowsForColumns(grids, 2));
        assertEquals(6, rowsForColumns(grids, 3));
        assertEquals(39, dpiForColumns(grids, 1));
        assertEquals(77, dpiForColumns(grids, 2));
        assertEquals(116, dpiForColumns(grids, 3));
    }

    @Test
    void theGridsOfferedAreTheOnesThatFitTheScreen() {
        for (TileGrid grid : TileGrid.candidatesFor(PageSize.A4, 384, 192)) {
            assertTrue(grid.waste() <= 0.25, grid + " wastes " + grid.waste());
        }
    }

    @Test
    void aLineOfTextIsMeasuredSoTheChoiceCanBeMade() {
        List<TileGrid> grids = TileGrid.candidatesFor(PageSize.A4, 384, 192);

        // One column: a ten-point line lands around six pixels, which is a smudge.
        assertFalse(grids.stream().filter(g -> g.columns() == 1).findFirst().orElseThrow().isLegible());
        // Three: around nineteen, which is a line of text.
        assertTrue(grids.stream().filter(g -> g.columns() == 3).findFirst().orElseThrow().isLegible());
    }

    @Test
    void tilesAccountForEveryPixelExactlyOnce() {
        int[][] counts = coverage(new Tiling(3, 3, 0), 100, 70);

        for (int y = 0; y < 70; y++) {
            for (int x = 0; x < 100; x++) {
                assertEquals(1, counts[y][x], "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void unevenDivisionsStillLeaveNoSeam() {
        // 101 into 3 is where accumulating a cell width instead of computing edges goes wrong.
        int[][] counts = coverage(new Tiling(3, 3, 0), 101, 37);

        for (int y = 0; y < 37; y++) {
            for (int x = 0; x < 101; x++) {
                assertEquals(1, counts[y][x], "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void overlapWidensTheTilesWithoutLeavingTheImage() {
        List<Tile> plain = new Tiling(2, 2, 0).tilesOf(100, 100);
        List<Tile> overlapping = new Tiling(2, 2, 0.10).tilesOf(100, 100);

        for (int i = 0; i < plain.size(); i++) {
            var before = plain.get(i).crop();
            var after = overlapping.get(i).crop();
            assertTrue(after.width() > before.width(), "tile " + i + " should have grown");
            assertTrue(after.x() >= 0 && after.y() >= 0);
            assertTrue(after.x() + after.width() <= 100);
            assertTrue(after.y() + after.height() <= 100);
        }

        // And every pixel is still covered — overlap adds, it does not shift.
        int[][] counts = coverage(new Tiling(2, 2, 0.10), 100, 100);
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                assertTrue(counts[y][x] >= 1, "pixel " + x + "," + y + " fell through the overlap");
            }
        }
    }

    @Test
    void tilesComeBackInReadingOrder() {
        List<Tile> tiles = new Tiling(2, 3, 0).tilesOf(90, 60);

        assertEquals(6, tiles.size());
        assertEquals("r1c1", tiles.get(0).label());
        assertEquals("r1c3", tiles.get(2).label());
        assertEquals("r2c1", tiles.get(3).label());
        // Reading order means the second row starts lower down, not further right.
        assertTrue(tiles.get(3).crop().y() > tiles.get(0).crop().y());
        assertEquals(tiles.get(0).crop().x(), tiles.get(3).crop().x());
    }

    @Test
    void anImageTooSmallToCutSaysSoInsteadOfProducingEmptyTiles() {
        Tiling fine = new Tiling(4, 4, 0);

        assertFalse(fine.fits(3, 10));
        assertThrows(IllegalArgumentException.class, () -> fine.tilesOf(3, 10));
    }

    @Test
    void gridsAreWrittenRowsFirst() {
        assertEquals(new Tiling(9, 3, Tiling.DEFAULT_OVERLAP), Tiling.parse("9x3"));
        assertEquals(9, Tiling.parse("9x3").rows());
        assertEquals(3, Tiling.parse("9x3").columns());
        assertEquals(27, Tiling.parse("9x3").count());

        assertThrows(IllegalArgumentException.class, () -> Tiling.parse("9"));
        assertThrows(IllegalArgumentException.class, () -> Tiling.parse("9xthree"));
        assertThrows(IllegalArgumentException.class, () -> new Tiling(0, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tiling(3, 3, 0.9));
    }

    @Test
    void oneTileIsTheWholeImageAndKnowsIt() {
        assertTrue(Tiling.NONE.isWhole());
        assertEquals(1, Tiling.NONE.count());

        List<Tile> tiles = Tiling.NONE.tilesOf(640, 480);
        assertEquals(1, tiles.size());
        assertTrue(tiles.get(0).crop().isWhole(640, 480));
    }

    @Test
    void pageSizesAreNamedOrMeasured() {
        assertEquals(PageSize.A4, PageSize.fromString("a4"));
        assertEquals(new PageSize(210, 297), PageSize.fromString("210x297"));
        assertEquals(297, PageSize.A4.rotated().widthMm());
        assertThrows(IllegalArgumentException.class, () -> PageSize.fromString("foolscap"));
    }

    private static int dpiForColumns(List<TileGrid> grids, int columns) {
        return grids.stream().filter(g -> g.columns() == columns).findFirst().orElseThrow().dpi();
    }

    private static int rowsForColumns(List<TileGrid> grids, int columns) {
        return grids.stream().filter(g -> g.columns() == columns).findFirst().orElseThrow().rows();
    }
}
