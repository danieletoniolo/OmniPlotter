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
import java.util.HashSet;
import java.util.List;
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
    private final Set<String> excludedTiles = new HashSet<>();

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

    /** A different grid invalidates which cells were switched off, since they described the old one. */
    public void setTiling(Tiling tiling) {
        if (!this.tiling.equals(tiling)) {
            excludedTiles.clear();
        }
        this.tiling = tiling;
    }

    public Set<String> excludedTiles() {
        return excludedTiles;
    }

    public void setExcludedTiles(Set<String> excluded) {
        excludedTiles.clear();
        excludedTiles.addAll(excluded);
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

    /**
     * Converts every piece of every selected page and writes the results.
     *
     * <p>With no grid this is one file and the name the encoder suggests, exactly as it always was.
     * With one, it is the same loop the command line runs, over the same plan, so the two faces
     * cannot disagree about what a tile is called.
     */
    public List<Path> convert(Format format, ConversionOptions options, Path outputDir) throws Exception {
        Target target = options.target();
        // The page on screen, and only that one. Cells are switched off by looking at them, so
        // converting pages nobody has looked at would apply a judgement that was never made — and
        // producing eighty files from a window showing one page is not a thing to do quietly. The
        // command line is where a whole document goes through at once.
        TilePlan plan = TilePlan.of(file.getName(), format, target, List.of(page), tiling,
            options.onCalcNumber());

        Files.createDirectories(outputDir);
        List<Path> written = new ArrayList<>();

        BufferedImage src = source();
        if (src == null) {
            throw new IllegalStateException(error == null ? "could not read image" : error);
        }
        if (!tiling.fits(src.getWidth(), src.getHeight())) {
            throw new IllegalStateException(src.getWidth() + "x" + src.getHeight()
                + " is too small to cut into " + tiling);
        }

        for (Tile tile : tiling.tilesOf(src.getWidth(), src.getHeight())) {
            if (excludedTiles.contains(tile.label())) {
                continue;
            }
            TilePlan.PlannedTile planned = plan.at(page, tile.row(), tile.column());
            ConversionOptions perTile = options.withOnCalc(planned.onCalcName(), planned.slot());
            if (!tiling.isWhole()) {
                perTile = perTile.withCrop(tile.crop());
            }

            ConversionResult result = EngineApi.convert(src, file.getName(), format, perTile);
            String name = tiling.isWhole() ? result.suggestedFileName() : planned.fileName();
            Path destination = outputDir.resolve(name);
            Files.write(destination, result.fileBytes());
            written.add(destination);

            // allFiles() is the main output plus any companion the format wants alongside it.
            for (var extra : result.extras()) {
                Files.write(outputDir.resolve(extra.name()), extra.bytes());
            }
        }
        return written;
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
