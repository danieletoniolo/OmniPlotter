package com.github.omniplotter.gui;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.ConversionResult;
import com.github.omniplotter.engine.data.Crop;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.PageSize;
import com.github.omniplotter.engine.data.Target;
import com.github.omniplotter.engine.data.Tile;
import com.github.omniplotter.engine.data.TilePlan;
import com.github.omniplotter.engine.data.Tiling;
import com.github.omniplotter.engine.pdf.PdfPages;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One queued file, along with the decoded page and its most recent preview.
 *
 * <p>A document and an image differ here only in how many pages there are and in the fact that a
 * page has to be rendered at a resolution somebody chose. Everything past {@link #source()} is the
 * same either way.
 */
public class ConversionJob {

    private final File file;
    private byte[] bytes;
    private BufferedImage source;
    private String error;

    /**
     * The part of this image to convert, or null for all of it.
     *
     * <p>Kept here rather than with the rest of the settings because it is the one choice that is
     * about the picture and not about the file being produced: a queue of ten photographs wants ten
     * rectangles, while they all want the same format.
     */
    private Crop crop;

    /** Documents only; one for everything else. */
    private int pageCount = 1;
    private int page = 1;

    /** What the page was last rendered at, so it is not rendered again for the same question. */
    private int renderDpi = PdfPages.DEFAULT_DPI;
    private int renderedPage;
    private int renderedDpi;

    private Tiling tiling = Tiling.NONE;

    /**
     * Which cells are switched off, per page.
     *
     * <p>Per page and not per document: the choice is made by looking at a page, and a header that
     * happens to sit in the same cell on page one is not a reason to drop that cell from page four
     * unseen. Pages nobody has looked at keep everything.
     */
    private final Map<Integer, Set<String>> excludedTiles = new HashMap<>();

    public ConversionJob(File file) {
        this.file = file;
    }

    public File file() {
        return file;
    }

    public String name() {
        return file.getName();
    }

    public String error() {
        return error;
    }

    public Crop crop() {
        return crop;
    }

    public void setCrop(Crop crop) {
        this.crop = crop;
    }

    public Tiling tiling() {
        return tiling;
    }

    /**
     * A different grid invalidates every page's exclusions, since they described the old one.
     *
     * <p>That part is not per page: {@code r1c1} of a six-by-two grid is a different piece of paper
     * from {@code r1c1} of an eight-by-three one, on every page alike.
     */
    public void setTiling(Tiling tiling) {
        if (!this.tiling.equals(tiling)) {
            excludedTiles.clear();
        }
        this.tiling = tiling;
    }

    /** What is switched off on the page being looked at. */
    public Set<String> excludedTiles() {
        return excludedTiles.computeIfAbsent(page, p -> new HashSet<>());
    }

    /**
     * Gives every page the exclusions of the one being looked at.
     *
     * <p>Replaces rather than adds to what the other pages had. A recurring header is the case this
     * is for, and "these cells, everywhere" is a sentence someone can check against the document;
     * merging would leave a state that is the sum of several past decisions and visible on none of
     * the pages at once.
     *
     * @return how many pages it was applied to
     */
    public int applyExclusionsToAllPages() {
        Set<String> current = Set.copyOf(excludedTiles());
        for (int other = 1; other <= pageCount(); other++) {
            Set<String> forPage = excludedTiles.computeIfAbsent(other, p -> new HashSet<>());
            forPage.clear();
            forPage.addAll(current);
        }
        return pageCount();
    }

    public void setExcludedTiles(Set<String> excluded) {
        Set<String> forPage = excludedTiles.computeIfAbsent(page, p -> new HashSet<>());
        forPage.clear();
        forPage.addAll(excluded);
    }

    // --- pages ------------------------------------------------------------------------------

    public boolean isDocument() {
        return pageCount() > 1 || PdfPages.isPdf(bytes());
    }

    public int pageCount() {
        bytes();
        return pageCount;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(1, Math.min(page, pageCount()));
    }

    /** How big the page is on paper, or null for something that is not a document. */
    public PageSize pageSize() {
        if (!PdfPages.isPdf(bytes())) {
            return null;
        }
        try (PdfPages document = PdfPages.open(bytes())) {
            return document.sizeOf(page);
        } catch (IOException e) {
            return null;
        }
    }

    /** The resolution the current grid needs. Changing it is what makes the page render again. */
    public void setRenderDpi(int dpi) {
        this.renderDpi = Math.max(1, dpi);
    }

    private byte[] bytes() {
        if (bytes == null && error == null) {
            try {
                bytes = Files.readAllBytes(file.toPath());
                if (PdfPages.isPdf(bytes)) {
                    try (PdfPages document = PdfPages.open(bytes)) {
                        pageCount = document.pageCount();
                    }
                }
            } catch (Exception e) {
                error = e.getMessage();
                bytes = new byte[0];
            }
        }
        return bytes == null ? new byte[0] : bytes;
    }

    /** Decodes the image, or renders the current page, remembering any failure so the list can show it. */
    public BufferedImage source() {
        byte[] content = bytes();
        if (error != null) {
            return null;
        }
        boolean stale = source == null || renderedPage != page || renderedDpi != renderDpi;
        if (!stale) {
            return source;
        }
        try {
            if (PdfPages.isPdf(content)) {
                try (PdfPages document = PdfPages.open(content)) {
                    source = document.render(page, renderDpi);
                }
            } else {
                source = EngineApi.decode(content, file.getName());
            }
            renderedPage = page;
            renderedDpi = renderDpi;
        } catch (Exception e) {
            error = e.getMessage();
            source = null;
        }
        return source;
    }

    /**
     * What the encoder will actually see, plus what it will actually produce.
     *
     * <p>Encoding as well as preprocessing lets the window show the output size and any capacity
     * warning <em>before</em> converting, which the web tool can only report afterwards. Encoding
     * is cheap next to the preprocessing that precedes it.
     */
    public record Preview(BufferedImage image, int encodedSize) {}

    public Preview preview(Format format, ConversionOptions options) throws Exception {
        BufferedImage src = source();
        if (src == null) {
            throw new IllegalStateException(error == null ? "could not read image" : error);
        }
        BufferedImage processed = EngineApi.preview(src, format, options);
        int size = EngineApi.encode(processed, file.getName(), format, options).fileBytes().length;
        return new Preview(processed, size);
    }

    /** Converts the page being looked at. */
    public List<Path> convert(Format format, ConversionOptions options, Path outputDir) throws Exception {
        return convertPages(List.of(page), format, options, outputDir);
    }

    /** Converts every page of the document, with the grid and the exclusions each one has. */
    public List<Path> convertAllPages(Format format, ConversionOptions options, Path outputDir)
            throws Exception {
        List<Integer> all = new ArrayList<>();
        for (int p = 1; p <= pageCount(); p++) {
            all.add(p);
        }
        return convertPages(all, format, options, outputDir);
    }

    /**
     * Converts every piece of the given pages and writes the results.
     *
     * <p>The plan is drawn up over the whole document even when one page is being converted, so
     * the names do not depend on how much was converted at a time. Planning only the page in hand
     * left every page producing {@code notes-r1c1.g3p} and overwriting the last one.
     *
     * <p>With no grid this is one file and the name the encoder suggests, exactly as it always was.
     */
    private List<Path> convertPages(List<Integer> pages, Format format, ConversionOptions options,
                                    Path outputDir) throws Exception {
        Target target = options.target();
        List<Integer> everyPage = new ArrayList<>();
        for (int p = 1; p <= pageCount(); p++) {
            everyPage.add(p);
        }
        TilePlan plan = TilePlan.of(file.getName(), format, target, everyPage, tiling,
            options.onCalcNumber());

        Files.createDirectories(outputDir);
        List<Path> written = new ArrayList<>();

        for (int p : pages) {
            BufferedImage src = imageFor(p);
            if (src == null) {
                throw new IllegalStateException(error == null ? "could not read image" : error);
            }
            if (!tiling.fits(src.getWidth(), src.getHeight())) {
                throw new IllegalStateException(src.getWidth() + "x" + src.getHeight()
                    + " is too small to cut into " + tiling);
            }
            Set<String> excluded = excludedFor(p);

            for (Tile tile : tiling.tilesOf(src.getWidth(), src.getHeight())) {
                if (excluded.contains(tile.label())) {
                    continue;
                }
                TilePlan.PlannedTile planned = plan.at(p, tile.row(), tile.column());
                ConversionOptions perTile = options.withOnCalc(planned.onCalcName(), planned.slot());
                if (!tiling.isWhole()) {
                    perTile = perTile.withCrop(tile.crop());
                }

                ConversionResult result = EngineApi.convert(src, file.getName(), format, perTile);
                String name = tiling.isWhole() && pageCount() == 1
                    ? result.suggestedFileName()
                    : planned.fileName();
                Path destination = outputDir.resolve(name);
                Files.write(destination, result.fileBytes());
                written.add(destination);

                // allFiles() is the main output plus any companion the format wants alongside it.
                for (var extra : result.extras()) {
                    Files.write(outputDir.resolve(extra.name()), extra.bytes());
                }
            }
        }
        return written;
    }

    /** The pixels of any page, without disturbing the one the window is showing. */
    private BufferedImage imageFor(int wanted) throws IOException {
        if (wanted == page) {
            return source();
        }
        byte[] content = bytes();
        if (!PdfPages.isPdf(content)) {
            return source();
        }
        try (PdfPages document = PdfPages.open(content)) {
            return document.render(wanted, renderDpi);
        }
    }

    private Set<String> excludedFor(int wanted) {
        return excludedTiles.getOrDefault(wanted, Set.of());
    }

    /** Base name of the input, used as the default on-calculator variable name. */
    public String baseName() {
        String name = file.getName();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public String toString() {
        return name();
    }
}
