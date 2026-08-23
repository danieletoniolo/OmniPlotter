package com.github.omniplotter;

import com.github.omniplotter.engine.EngineApi;
import com.github.omniplotter.engine.data.PageSize;
import com.github.omniplotter.engine.pdf.PdfPages;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning pages into pixels.
 *
 * <p>The documents are built here rather than checked in, so what is being tested is the reading of
 * a real PDF structure and not one file that happened to work. What matters is the page size in
 * millimetres — the grid arithmetic is derived from it, so getting it wrong quietly ruins every
 * resolution downstream — and that a page renders at the size its resolution implies.
 */
class PdfPagesTest {

    /** A document of {@code pages} A4 pages, each with a black band across the top. */
    private static byte[] document(int pages, PDRectangle size, int rotation) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(size);
                page.setRotation(rotation);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.setNonStrokingColor(0f, 0f, 0f);
                    content.addRect(0, size.getHeight() - 40, size.getWidth(), 40);
                    content.fill();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] a4(int pages) throws IOException {
        return document(pages, PDRectangle.A4, 0);
    }

    @Test
    void aPdfIsRecognisedByItsHeaderAndNothingElseIs() throws IOException {
        assertTrue(PdfPages.isPdf(a4(1)));
        assertFalse(PdfPages.isPdf("this is not a pdf".getBytes()));
        assertFalse(PdfPages.isPdf(new byte[0]));
        assertFalse(PdfPages.isPdf(null));
    }

    @Test
    void pagesAreCountedAndNumberedFromOne() throws IOException {
        try (PdfPages pages = PdfPages.open(a4(3))) {
            assertEquals(3, pages.pageCount());

            // Off by one here would silently convert the wrong page, so it says so instead.
            assertThrows(IllegalArgumentException.class, () -> pages.render(0, 72));
            assertThrows(IllegalArgumentException.class, () -> pages.render(4, 72));
        }
    }

    @Test
    void a4ComesBackAsA4InMillimetres() throws IOException {
        // The whole grid arithmetic hangs off this number.
        try (PdfPages pages = PdfPages.open(a4(1))) {
            PageSize size = pages.sizeOf(1);

            assertEquals(210, size.widthMm(), 0.5);
            assertEquals(297, size.heightMm(), 0.5);
        }
    }

    @Test
    void aRotatedPageReportsTheShapeItRendersAs() throws IOException {
        // The renderer applies the rotation, so a size that ignored it would describe an image
        // nobody is going to see.
        try (PdfPages pages = PdfPages.open(document(1, PDRectangle.A4, 90))) {
            PageSize size = pages.sizeOf(1);

            assertEquals(297, size.widthMm(), 0.5);
            assertEquals(210, size.heightMm(), 0.5);

            BufferedImage rendered = pages.render(1, 36);
            assertTrue(rendered.getWidth() > rendered.getHeight(), "a rotated A4 renders landscape");
        }
    }

    @Test
    void resolutionDecidesTheSizeOfTheRender() throws IOException {
        try (PdfPages pages = PdfPages.open(a4(1))) {
            BufferedImage low = pages.render(1, 36);
            BufferedImage high = pages.render(1, 72);

            // A4 is 8.27 inches across.
            assertEquals(298, low.getWidth(), 2);
            assertEquals(595, high.getWidth(), 2);
            assertEquals(2.0, (double) high.getWidth() / low.getWidth(), 0.02);
        }
    }

    @Test
    void anAbsurdResolutionIsReducedRatherThanAllocated() throws IOException {
        // A4 at 2000 DPI would be 274 megapixels, which is a gigabyte of image for a 384-pixel
        // screen. It comes back smaller instead of taking the application down.
        try (PdfPages pages = PdfPages.open(a4(1))) {
            BufferedImage huge = pages.render(1, 2000);

            long pixels = (long) huge.getWidth() * huge.getHeight();
            assertTrue(pixels <= 16_000_000L, "rendered " + pixels + " pixels");
            assertTrue(huge.getWidth() > 2000, "but still as large as it can usefully be");
        }
    }

    @Test
    void aDocumentConvertsWithoutAnyoneMentioningPdf() throws IOException {
        // convert notes.pdf, with nothing else said: the first page, at a resolution comfortably
        // above what a single tile needs.
        BufferedImage decoded = EngineApi.decode(a4(2), "notes.pdf");

        assertEquals(1240, decoded.getWidth(), 5);
        // The band drawn across the top of every page is what says a page was actually rendered.
        assertTrue(isDark(decoded.getRGB(decoded.getWidth() / 2, 10)), "the top band should be there");
        assertFalse(isDark(decoded.getRGB(decoded.getWidth() / 2, decoded.getHeight() / 2)));
    }

    private static boolean isDark(int rgb) {
        return ((rgb >> 16) & 0xFF) < 100 && ((rgb >> 8) & 0xFF) < 100 && (rgb & 0xFF) < 100;
    }
}
