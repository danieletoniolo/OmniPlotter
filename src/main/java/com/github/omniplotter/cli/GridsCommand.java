package com.github.omniplotter.cli;

import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.FormatConfig;
import com.github.omniplotter.engine.data.PageSize;
import com.github.omniplotter.engine.data.Target;
import com.github.omniplotter.engine.data.TileGrid;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Answers the question that decides whether tiling a page was worth doing.
 *
 * <p>Not "how many pieces" — that is the question an interface asks when it does not know what
 * matters. Cutting a page across adds no resolution at all, so the number to look at is how tall a
 * line of text ends up, and this prints it for every grid that suits the screen.
 */
@Command(name = "grids", mixinStandardHelpOptions = true,
    description = "Show the ways a page can be cut up for a screen, and how legible each is.")
public class GridsCommand implements Callable<Integer> {

    @Option(names = {"-t", "--target"}, paramLabel = "TARGET",
        description = "Calculator model, e.g. cg, nw110. See 'targets'.")
    private String targetId = "cg";

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT",
        description = "Output format, for its canvas size. Defaults to the target's usual one.")
    private String formatId;

    @Option(names = "--page", paramLabel = "SIZE",
        description = "Page being cut up: a4, letter, a5, or millimetres as 210x297. Default a4.")
    private String page = "a4";

    @Override
    public Integer call() {
        Target target;
        PageSize size;
        try {
            target = Target.fromString(targetId);
            size = PageSize.fromString(page);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        Format format;
        try {
            format = formatId != null ? Format.fromString(formatId) : defaultFormat(target);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        FormatConfig config = FormatConfig.of(format);
        int width = config.defaultWidth();
        int height = config.defaultHeight();

        System.out.println(size + " on " + target.getDisplayName()
            + " (" + format.id() + ", " + width + "x" + height + ")");
        System.out.println();
        System.out.printf("%-8s %-7s %-6s %-11s %s%n", "GRID", "TILES", "DPI", "TEXT LINE", "");
        for (TileGrid grid : TileGrid.candidatesFor(size, width, height)) {
            System.out.printf("%-8s %-7d %-6d %-11s %s%n",
                grid, grid.count(), grid.dpi(),
                String.format("%.1f px", grid.lineHeight()),
                grid.isLegible() ? "readable" : "too small to read");
        }
        System.out.println();
        System.out.println("Resolution comes from columns: cutting a page across adds none.");
        return 0;
    }

    private static Format defaultFormat(Target target) {
        List<Format> supported = target.getSupportedFormats(
            target.supportsMode(com.github.omniplotter.engine.data.Mode.VAR)
                ? com.github.omniplotter.engine.data.Mode.VAR
                : com.github.omniplotter.engine.data.Mode.SCRIPT);
        if (supported.isEmpty()) {
            throw new IllegalArgumentException(target.getId() + " has no formats to size a page against.");
        }
        return supported.get(0);
    }
}
