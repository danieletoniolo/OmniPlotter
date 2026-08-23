package com.github.omniplotter.engine.pdf;

import com.github.omniplotter.engine.data.PageSize;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The only part of this application that knows what a PDF is.
 *
 * <p>Everything past here works on a {@link BufferedImage} and cannot tell a rendered page from a
 * photograph, which is what keeps tiling a property of geometry rather than a feature of documents:
 * cutting a large scan into pieces goes down the same path with no PDF anywhere in it.
 *
 * <p>Held open rather than reopened per page. A ten-page document converted at eight tiles a page is
 * eighty conversions, and parsing the file eighty times to answer eighty questions about it would be
 * the slowest possible way to do that.
 */
public final class PdfPages implements AutoCloseable {

    /** Points per inch, which is what a PDF measures itself in. */
    private static final double POINTS_PER_INCH = 72;

    private static final double MM_PER_INCH = 25.4;

    /**
     * Where rendering stops growing, in pixels.
     *
     * <p>A page is rendered above the size it will be converted at, so the downscale has something
     * to work with; asked for enough columns and enough oversampling that becomes hundreds of
     * megabytes for one page. Sixteen megapixels is far more than any calculator screen can use and
     * still fits in a default heap.
     */
    private static final long MAX_PIXELS = 16_000_000L;

    /**
     * Resolution for a page nobody said anything about.
     *
     * <p>Generously above what a single tile on the widest calculator screen needs — 384 pixels
     * across A4 is 46 — so the pipeline is always downscaling. Rendering below what the output needs
     * and enlarging afterwards is the mistake that makes this kind of tool look cheap, and the
     * tiling path computes its own figure rather than trusting this one.
     */
    public static final int DEFAULT_DPI = 150;

    private final PDDocument document;
    private final PDFRenderer renderer;

    private PdfPages(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    /** Whether these bytes are worth handing to a PDF parser. */
    public static boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            return false;
        }
        // The header is allowed to sit a little way in: files with a shebang or a stray byte order
        // mark in front of it are common enough that readers are expected to look for it.
        int limit = Math.min(bytes.length, 1024);
        String head = new String(bytes, 0, limit, StandardCharsets.ISO_8859_1);
        return head.contains("%PDF-");
    }

    public static PdfPages open(byte[] bytes) throws IOException {
        return new PdfPages(Loader.loadPDF(bytes));
    }

    public int pageCount() {
        return document.getNumberOfPages();
    }

    /**
     * How big the page is on paper, which is what turns a grid into a resolution.
     *
     * @param page counted from one, the way a person refers to a page
     */
    public PageSize sizeOf(int page) {
        PDPage pdPage = document.getPage(index(page));
        var box = pdPage.getCropBox();
        double widthMm = box.getWidth() / POINTS_PER_INCH * MM_PER_INCH;
        double heightMm = box.getHeight() / POINTS_PER_INCH * MM_PER_INCH;

        // A page can carry a rotation that the box does not: the renderer applies it, so the size
        // reported here has to agree with the image that comes out.
        int rotation = ((pdPage.getRotation() % 360) + 360) % 360;
        return rotation == 90 || rotation == 270
            ? new PageSize(heightMm, widthMm)
            : new PageSize(widthMm, heightMm);
    }

    /**
     * Renders one page, at the requested resolution or the highest one that still fits in memory.
     *
     * @param page counted from one
     */
    public BufferedImage render(int page, int dpi) throws IOException {
        return renderer.renderImageWithDPI(index(page), (float) capped(page, dpi), ImageType.RGB);
    }

    /** Reduces the resolution rather than failing, and rather than taking the machine with it. */
    private double capped(int page, int dpi) {
        PageSize size = sizeOf(page);
        double pixels = size.widthInches() * dpi * size.heightInches() * dpi;
        if (pixels <= MAX_PIXELS) {
            return dpi;
        }
        return dpi * Math.sqrt(MAX_PIXELS / pixels);
    }

    private int index(int page) {
        if (page < 1 || page > pageCount()) {
            throw new IllegalArgumentException("This document has " + pageCount()
                + (pageCount() == 1 ? " page" : " pages") + "; there is no page " + page);
        }
        return page - 1;
    }

    @Override
    public void close() throws IOException {
        document.close();
    }
}
