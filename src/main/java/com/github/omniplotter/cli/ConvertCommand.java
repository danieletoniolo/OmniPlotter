package com.github.omniplotter.cli;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.data.Adjustments;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.Crop;
import com.github.omniplotter.engine.data.Dither;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.FormatConfig;
import com.github.omniplotter.engine.data.Framing;
import com.github.omniplotter.engine.data.Look;
import com.github.omniplotter.engine.data.PageSize;
import com.github.omniplotter.engine.data.Tile;
import com.github.omniplotter.engine.data.TilePlan;
import com.github.omniplotter.engine.data.Tiling;
import com.github.omniplotter.engine.pdf.PdfPages;
import com.github.omniplotter.engine.data.Mode;
import com.github.omniplotter.engine.data.OnCalcName;
import com.github.omniplotter.engine.data.OutputLimits;
import com.github.omniplotter.engine.data.Target;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "convert", mixinStandardHelpOptions = true,
    description = "Convert one or more images to a calculator format.")
public class ConvertCommand implements Callable<Integer> {

    @Parameters(index = "0..*", arity = "1..*", paramLabel = "IMAGE",
        description = "Image files to convert.")
    private List<File> inputs = new ArrayList<>();

    @Option(names = {"-f", "--format"}, required = true, paramLabel = "FORMAT",
        description = "Output format, e.g. cp.g3p, 8ci, kandinsky.py. See 'formats'.")
    private String formatId;

    @Option(names = {"-t", "--target"}, paramLabel = "TARGET",
        description = "Calculator model, e.g. cg, cg3, nw110. Defaults to one that supports the "
            + "format. Affects Python imports and the .tns wrapper.")
    private String targetId;

    @Option(names = {"-o", "--output"}, paramLabel = "PATH",
        description = "Output file, or a directory when converting several images. "
            + "Defaults to the current directory.")
    private File output;

    @Option(names = "--width", description = "Canvas width. Defaults to the format's own.")
    private Integer width;

    @Option(names = "--height", description = "Canvas height. Defaults to the format's own.")
    private Integer height;

    @Option(names = "--colors", description = "Colour budget. Defaults to the format's maximum.")
    private Integer colors;

    @Option(names = "--preset", paramLabel = "NAME",
        description = "Named canvas size for the format, e.g. full, menu, graph.")
    private String preset;

    @Option(names = "--enlarge-smaller",
        description = "Scale a source smaller than the canvas up to fill it. Off by default, "
            + "matching the web tool.")
    private boolean enlargeSmaller;

    @Option(names = "--no-keep-ratio", description = "Stretch to the canvas instead of preserving "
        + "the source aspect ratio.")
    private boolean noKeepRatio;

    @Option(names = "--grid", paramLabel = "RxC",
        description = "Cut each page into this many rows by columns, converting every piece. "
            + "Columns are what carry resolution; see the 'grids' command.")
    private String grid;

    @Option(names = "--pages", paramLabel = "LIST",
        description = "Which pages of a document to convert, as 1-3,7. Default: all of them.")
    private String pages;

    @Option(names = "--overlap", paramLabel = "PERCENT",
        description = "How far each tile reaches into its neighbours, so a line of text on a cut "
            + "survives in one of them. Default 3.")
    private Double overlap;

    @Option(names = "--start-slot", paramLabel = "N",
        description = "First picture slot to number tiles from, for formats addressed by one.")
    private Integer startSlot;

    @Option(names = "--look", paramLabel = "NAME",
        description = "Starting point for the controls below: ${COMPLETION-CANDIDATES}. "
            + "'document' is the one for text and screenshots. Anything given alongside it wins.")
    private Look look;

    @Option(names = "--dither", paramLabel = "WHEN",
        description = "Trade colour accuracy for apparent depth: ${COMPLETION-CANDIDATES}. "
            + "'auto' is what the web tool does for the format, and is the default. Turning it "
            + "off is what makes small text legible.")
    private Dither dither;

    @Option(names = "--brightness", paramLabel = "N", description = "-100 to 100. Default 0.")
    private Integer brightness;

    @Option(names = "--contrast", paramLabel = "N", description = "-100 to 100. Default 0.")
    private Integer contrast;

    @Option(names = "--gamma", paramLabel = "N",
        description = "0.1 to 5.0. Above 1 lightens the midtones. Default 1.")
    private Double gamma;

    @Option(names = "--saturation", paramLabel = "N",
        description = "-100 to 100, where -100 is grey. Default 0.")
    private Integer saturation;

    @Option(names = "--sharpen", paramLabel = "N",
        description = "Unsharp mask strength, 0 to 5, applied after the image is resized. Default 0.")
    private Double sharpen;

    @Option(names = "--crop", paramLabel = "X,Y,W,H",
        description = "Convert only this rectangle of the source, in source pixels.")
    private String crop;

    @Option(names = "--fill",
        description = "Crop the source to the canvas proportions instead of padding it with "
            + "white. Combine with --enlarge-smaller to actually reach the canvas.")
    private boolean fill;

    @Option(names = "--name", paramLabel = "NAME",
        description = "On-calculator variable name. Rules vary by format; see 'formats'. Defaults to the file name.")
    private String onCalcName;

    @Option(names = "--number", paramLabel = "N",
        description = "On-calculator slot for Pic/Image formats. Range depends on the model (TI-73: 1-3, TI-82: 0-6, else 0-9). Default 1.")
    private int onCalcNumber = 1;

    @Override
    public Integer call() {
        Format format;
        try {
            format = Format.fromString(formatId);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown format: " + formatId);
            System.err.println("Run 'omniplotter formats' to list them.");
            return 2;
        }

        Mode mode = format.isScript() ? Mode.SCRIPT : Mode.VAR;
        Target target;
        if (targetId != null) {
            try {
                target = Target.fromString(targetId);
            } catch (IllegalArgumentException e) {
                System.err.println("Unknown target: " + targetId);
                return 2;
            }
            if (!target.supports(mode, format)) {
                System.err.println(target.getId() + " does not support " + format.id() + ".");
                List<Format> supported = target.getSupportedFormats(mode);
                if (supported.isEmpty()) {
                    System.err.println(target.getId() + " has no " + mode + " formats at all.");
                } else {
                    System.err.println("It accepts: " + ids(supported));
                }
                System.err.println("Targets for " + format.id() + ": "
                    + String.join(", ", Target.supporting(mode, format).stream().map(Target::getId).toList()));
                return 2;
            }
        } else {
            List<Target> candidates = Target.supporting(mode, format);
            if (candidates.isEmpty()) {
                System.err.println("No target supports " + format.id() + ".");
                return 2;
            }
            target = candidates.get(0);
        }

        FormatConfig config = FormatConfig.of(format);
        ConversionOptions options = ConversionOptions.defaults(target, format)
            .withKeepRatio(!noKeepRatio)
            .withEnlargeSmaller(enlargeSmaller)
            .withOnCalc(onCalcName == null ? "IMAGE" : onCalcName, onCalcNumber);

        if (preset != null) {
            var match = config.presets().stream()
                .filter(p -> p.label().equalsIgnoreCase(preset)).findFirst();
            if (match.isEmpty()) {
                System.err.println("Unknown preset: " + preset);
                System.err.println("Available for " + format.id() + ": "
                    + config.presets().stream().map(FormatConfig.SizePreset::label).toList());
                return 2;
            }
            options = options.withSize(match.get().width(), match.get().height());
        }
        if (width != null || height != null) {
            options = options.withSize(
                width != null ? width : options.width(),
                height != null ? height : options.height());
        }
        if (colors != null) {
            options = options.withColors(colors);
        }

        // A look writes its values in, and anything named explicitly then overwrites them. It is
        // deliberately not a mode: nothing downstream knows a look was ever chosen, so
        // `--look document --contrast 0` means what it says.
        if (look != null) {
            options = options.withLook(look);
        }
        Adjustments adjustments = options.adjustments();
        if (brightness != null) {
            adjustments = adjustments.withBrightness(brightness);
        }
        if (contrast != null) {
            adjustments = adjustments.withContrast(contrast);
        }
        if (gamma != null) {
            adjustments = adjustments.withGamma(gamma);
        }
        if (saturation != null) {
            adjustments = adjustments.withSaturation(saturation);
        }
        if (sharpen != null) {
            adjustments = adjustments.withSharpen(sharpen);
        }
        options = options.withAdjustments(adjustments);

        if (dither != null) {
            options = options.withDither(dither);
        }

        if (crop != null || fill) {
            Crop rectangle = null;
            if (crop != null) {
                try {
                    rectangle = Crop.parse(crop);
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    return 2;
                }
            }
            options = options.withFraming(new Framing(fill, rectangle));
        }

        // Report anything the format will not honour, rather than silently substituting.
        ConversionOptions clamped = options.clampedTo(format);
        if (clamped.width() != options.width() || clamped.height() != options.height()) {
            System.err.printf("note: %s uses a %dx%d canvas; requested %dx%d%n",
                format.id(), clamped.width(), clamped.height(), options.width(), options.height());
        }
        if (clamped.colors() != options.colors()) {
            System.err.printf("note: %s allows at most %d colours; requested %d%n",
                format.id(), clamped.colors(), options.colors());
        }
        options = clamped;

        // Reject an unusable on-calc address before writing anything: the name and the slot are
        // encoded into the file, so a bad one produces a file the calculator quietly refuses or
        // installs in the wrong place.
        if (onCalcName != null) {
            var problem = OnCalcName.validateName(format, onCalcName);
            if (problem.isPresent()) {
                System.err.println(problem.get());
                return 2;
            }
        }
        var slotProblem = OnCalcName.validateSlot(format, target, onCalcNumber);
        if (slotProblem.isPresent()) {
            System.err.println(slotProblem.get());
            return 2;
        }

        // Checked before anything is opened: a malformed grid or page list is a usage error, and
        // one is worth telling apart from a conversion that was attempted and failed.
        try {
            tiling();
            if (pages != null) {
                parsePages(pages);
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        boolean multiple = inputs.size() > 1;
        if (multiple && output != null && output.exists() && !output.isDirectory()) {
            System.err.println("With several inputs, --output must be a directory.");
            return 2;
        }

        int failures = 0;
        for (File input : inputs) {
            try {
                failures += convertOne(input, format, target, options, multiple) ? 0 : 1;
            } catch (Exception e) {
                System.err.println(input.getName() + ": " + e.getMessage());
                failures++;
            }
        }
        return failures == 0 ? 0 : 1;
    }

    /** How far above the output resolution a page is rendered, so the downscale has something to work with. */
    private static final double OVERSAMPLE = 2;

    private boolean convertOne(File input, Format format, Target target,
                               ConversionOptions options, boolean multiple) throws Exception {
        if (!input.isFile()) {
            System.err.println(input.getPath() + ": not a file");
            return false;
        }
        byte[] bytes = Files.readAllBytes(input.toPath());
        Tiling tiling = tiling();
        String named = onCalcName == null ? input.getName() : onCalcName;
        int slot = startSlot == null ? onCalcNumber : startSlot;

        // A document and an image differ only in where the pixels come from and how many pages
        // there are. Everything after that is the same loop.
        if (PdfPages.isPdf(bytes)) {
            try (PdfPages document = PdfPages.open(bytes)) {
                List<Integer> selected = pages(document.pageCount());
                TilePlan plan = TilePlan.of(named, format, target, selected, tiling, slot);
                announce(plan);

                boolean ok = true;
                for (int page : selected) {
                    PageSize size = document.sizeOf(page);
                    BufferedImage rendered = document.render(page, renderDpi(size, options, tiling));
                    ok &= convertTiles(input, rendered, page, format, target, options, tiling, plan, multiple);
                }
                return ok;
            }
        }

        if (pages != null) {
            System.err.println("note: --pages applies to documents; " + input.getName()
                + " is a single image.");
        }
        BufferedImage image = EngineApi.decode(bytes, input.getName());
        TilePlan plan = TilePlan.of(named, format, target, List.of(1), tiling, slot);
        announce(plan);
        return convertTiles(input, image, 1, format, target, options, tiling, plan, multiple);
    }

    private Tiling tiling() {
        Tiling requested = grid == null ? Tiling.NONE : Tiling.parse(grid);
        return overlap == null
            ? requested
            : new Tiling(requested.rows(), requested.columns(), overlap / 100.0);
    }

    /**
     * The resolution to render a page at.
     *
     * <p>Derived from what the output actually needs — the canvas width, once per column — and then
     * doubled, so the pipeline is downscaling rather than enlarging. Rendering at a fixed low
     * resolution and scaling up is the mistake that makes this kind of tool look cheap.
     */
    private int renderDpi(PageSize page, ConversionOptions options, Tiling tiling) {
        double needed = (double) options.width() * tiling.columns() / page.widthInches();
        return (int) Math.ceil(needed * OVERSAMPLE);
    }

    /** Says what is about to happen, while it can still be stopped. */
    private void announce(TilePlan plan) {
        if (plan.count() > 1) {
            System.out.println(plan.summary() + "...");
        }
        plan.warnings().forEach(warning -> System.err.println("warning: " + warning));
    }

    private List<Integer> pages(int pageCount) {
        if (pages == null) {
            return java.util.stream.IntStream.rangeClosed(1, pageCount).boxed().toList();
        }
        List<Integer> selected = parsePages(pages);
        for (int page : selected) {
            // Out of range is a mistake worth stopping for rather than silently trimming: asking
            // for pages 1-10 of a five-page document is not a request to convert five.
            if (page > pageCount) {
                throw new IllegalArgumentException("this document has " + pageCount
                    + (pageCount == 1 ? " page" : " pages") + "; there is no page " + page);
            }
        }
        return selected;
    }

    /** Reads {@code 1-3,7}. Syntax only — how many pages there are is not known until one is open. */
    static List<Integer> parsePages(String spec) {
        java.util.SortedSet<Integer> selected = new java.util.TreeSet<>();
        for (String part : spec.split(",")) {
            String piece = part.trim();
            if (piece.isEmpty()) {
                continue;
            }
            int dash = piece.indexOf('-', 1);
            try {
                int from = Integer.parseInt((dash < 0 ? piece : piece.substring(0, dash)).trim());
                int to = dash < 0 ? from : Integer.parseInt(piece.substring(dash + 1).trim());
                if (from > to) {
                    throw new IllegalArgumentException("Pages run forwards: " + piece);
                }
                if (from < 1) {
                    throw new IllegalArgumentException("Pages are numbered from one — got: " + piece);
                }
                for (int page = from; page <= to; page++) {
                    selected.add(page);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Pages are numbers and ranges, as 1-3,7 — got: " + spec);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No pages selected by: " + spec);
        }
        return List.copyOf(selected);
    }

    /** Converts every piece of one page, or the whole of one image when there is no grid. */
    private boolean convertTiles(File input, BufferedImage image, int page, Format format, Target target,
                                 ConversionOptions options, Tiling tiling, TilePlan plan,
                                 boolean multiple) throws Exception {
        if (!tiling.fits(image.getWidth(), image.getHeight())) {
            System.err.println(input.getName() + ": " + image.getWidth() + "x" + image.getHeight()
                + " is too small to cut into " + tiling);
            return false;
        }

        boolean ok = true;
        for (Tile tile : tiling.tilesOf(image.getWidth(), image.getHeight())) {
            TilePlan.PlannedTile planned = plan.at(page, tile.row(), tile.column());
            ConversionOptions perTile = options.withOnCalc(planned.onCalcName(), planned.slot());
            // Left alone when there is no grid, so a plain conversion keeps whatever --crop said.
            if (!tiling.isWhole()) {
                perTile = perTile.withCrop(tile.crop());
            }
            ok &= convertTile(input, image, format, target, perTile,
                plan.count() > 1 ? planned.fileName() : null, multiple);
        }
        return ok;
    }

    private boolean convertTile(File input, BufferedImage image, Format format, Target target,
                                ConversionOptions perFile, String fileName, boolean multiple)
            throws Exception {
        ConversionResult result = EngineApi.convert(image, input.getName(), format, perFile);

        // A tile carries a name of its own, which says which part of which page it is; a single
        // conversion keeps the name the encoder suggests, exactly as before.
        String name = fileName == null ? result.suggestedFileName() : fileName;
        Path destination;
        if (output == null) {
            destination = Path.of(name);
        } else if (output.isDirectory() || multiple || fileName != null) {
            Files.createDirectories(output.toPath());
            destination = output.toPath().resolve(name);
        } else {
            destination = output.toPath();
        }

        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.write(destination, result.fileBytes());
        System.out.printf("%s -> %s (%s, %dx%d, %d bytes)%n",
            input.getName(), destination, format.id(),
            perFile.width(), perFile.height(), result.fileBytes().length);

        // Some formats come with a companion file; write it beside the main one.
        for (var extra : result.extras()) {
            Path beside = destination.toAbsolutePath().getParent().resolve(extra.name());
            Files.write(beside, extra.bytes());
            System.out.printf("%s -> %s (%d bytes)%n", input.getName(), beside, extra.bytes().length);
        }

        // The file is valid but may not fit on the device. Warn and still write it, as the
        // reference does: the user may well be targeting a firmware with more room.
        OutputLimits.check(target, format, result.fileBytes().length)
            .ifPresent(warning -> System.err.println("warning: " + warning));
        return true;
    }

    private static String ids(List<Format> formats) {
        return String.join(", ", formats.stream().map(Format::id).toList());
    }
}
