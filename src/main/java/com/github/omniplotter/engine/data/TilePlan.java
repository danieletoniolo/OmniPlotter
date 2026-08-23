package com.github.omniplotter.engine.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What every piece of a tiled conversion will be called, worked out before anything is written.
 *
 * <p>Twelve tiles do not fit in the ten {@code Pic} slots of a TI, and on a TI-73 there are three.
 * A tool that discovers this after producing the files has told the user nothing they could have
 * acted on; the same bargain {@link OutputLimits} strikes for file sizes applies here — find out
 * now, not standing in front of the calculator.
 *
 * <p>The two names are different on purpose. On the calculator there are eight characters and, for
 * some formats, only a number; on disk there is room to say which part of which page this is, and a
 * folder of {@code notes-p2-r3c1.g3p} can be sorted and picked through, while a folder of
 * {@code NOTES14.g3p} cannot.
 */
public record TilePlan(List<PlannedTile> tiles, List<String> warnings) {

    /**
     * @param page   the page this came from, counted from one
     * @param slot   the {@code Pic} number for formats addressed by one; meaningless for the rest
     */
    public record PlannedTile(int page, int row, int column, String onCalcName, int slot, String fileName) {

        public String label() {
            return "p" + page + "r" + row + "c" + column;
        }
    }

    public int count() {
        return tiles.size();
    }

    /** The one line worth printing before starting: how much this is about to produce. */
    public String summary() {
        long pages = tiles.stream().map(PlannedTile::page).distinct().count();
        return count() + (count() == 1 ? " file" : " files")
            + (pages > 1 ? " from " + pages + " pages" : "");
    }

    public PlannedTile at(int page, int row, int column) {
        return tiles.stream()
            .filter(tile -> tile.page() == page && tile.row() == row && tile.column() == column)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No tile planned for page " + page + ", row " + row + ", column " + column));
    }

    /**
     * Works out the names, the slots and what the user needs to be told.
     *
     * <p>Geometry stays out of this. Which pixels each tile covers depends on how large the page
     * turned out to be once rendered, while everything decided here depends only on how many pieces
     * there are — which is exactly why it can be decided, and reported, before a page is rendered
     * at all.
     *
     * @param startSlot the first {@code Pic} number to use, for formats addressed by one
     */
    public static TilePlan of(String sourceName, Format format, Target target,
                              List<Integer> pages, Tiling tiling, int startSlot) {
        List<PlannedTile> tiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String base = OnCalcName.suggestFrom(format, sourceName);
        String fileBase = stripExtension(sourceName);
        int total = pages.size() * tiling.count();
        boolean numbered = total > 1;
        int digits = Math.max(2, String.valueOf(total).length());

        Target.SlotRange slots = Target.slotRange(target);
        boolean addressedBySlot = OnCalcName.ruleFor(format) == OnCalcName.Rule.SLOT;
        int slotCount = slots.max() - slots.min() + 1;

        int index = 0;
        for (int page : pages) {
            // Reading order, the same order tilesOf produces the rectangles in, so a caller can
            // pair the two without either of them knowing about the other.
            for (int row = 1; row <= tiling.rows(); row++) {
                for (int column = 1; column <= tiling.columns(); column++) {
                    String onCalc = numbered ? numbered(base, index + 1, digits) : base;
                    // Past the end of the slots the numbering comes back round. The files stay distinct,
                    // so nothing is lost on disk; what cannot be had is all of them on the calculator at
                    // once, which is what the warning below is about.
                    int slot = addressedBySlot
                        ? slots.min() + Math.floorMod(startSlot - slots.min() + index, slotCount)
                        : startSlot;

                    tiles.add(new PlannedTile(page, row, column, onCalc, slot,
                        format.fileName(fileName(fileBase, page, row, column, pages.size(), tiling), target)));
                    index++;
                }
            }
        }

        if (addressedBySlot && total > slotCount) {
            warnings.add(total + " tiles but " + target.getDisplayName() + " has " + slotCount
                + (slotCount == 1 ? " picture slot" : " picture slots")
                + " (" + slots.min() + "-" + slots.max() + "): the files are all written and all "
                + "different, but only " + slotCount + " can be on the calculator at a time.");
        }
        return new TilePlan(List.copyOf(tiles), List.copyOf(warnings));
    }

    /** {@code NOTES01}: as much of the name as the eight characters leave once the number is on. */
    private static String numbered(String base, int number, int digits) {
        String suffix = String.format(Locale.ROOT, "%0" + digits + "d", number);
        String head = base.length() + suffix.length() > 8
            ? base.substring(0, Math.max(1, 8 - suffix.length()))
            : base;
        return head + suffix;
    }

    /** {@code notes-p2-r3c1}: only the parts that vary, so a single page does not carry a p1. */
    private static String fileName(String base, int page, int row, int column,
                                   int pageCount, Tiling tiling) {
        StringBuilder name = new StringBuilder(base);
        if (pageCount > 1) {
            name.append("-p").append(page);
        }
        if (!tiling.isWhole()) {
            name.append("-r").append(row).append("c").append(column);
        }
        return name.toString();
    }

    private static String stripExtension(String fileName) {
        String base = fileName == null || fileName.isBlank() ? "image" : fileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }
}
