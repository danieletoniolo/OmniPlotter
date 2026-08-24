package com.github.omniplotter.gui;

import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.Target;
import com.github.omniplotter.engine.data.Tiling;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a queued file remembers about itself.
 *
 * <p>Which cells were switched off is a judgement someone made by looking at the page, and the
 * cheapest way to lose it is a setter that quietly discards it. Setting the same grid twice happens
 * constantly — every time the picker is rebuilt, every time the selection comes back to this file —
 * and none of those are a decision to start again.
 */
class ConversionJobTest {

    private static ConversionJob job(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("page.png");
        ImageIO.write(new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        return new ConversionJob(file.toFile());
    }

    @Test
    void theSameGridTwiceIsNotAChangeOfMind(@TempDir Path directory) throws IOException {
        ConversionJob job = job(directory);
        job.setTiling(Tiling.of(6, 2));
        job.setExcludedTiles(Set.of("r1c1", "r1c2"));

        job.setTiling(Tiling.of(6, 2));

        assertEquals(Set.of("r1c1", "r1c2"), job.excludedTiles());
    }

    @Test
    void aDifferentGridDescribesDifferentCells(@TempDir Path directory) throws IOException {
        ConversionJob job = job(directory);
        job.setTiling(Tiling.of(6, 2));
        job.setExcludedTiles(Set.of("r1c1"));

        job.setTiling(Tiling.of(8, 3));

        assertTrue(job.excludedTiles().isEmpty(),
            "r1c1 of a 6x2 grid is not r1c1 of an 8x3 one");
    }

    /** A document of three blank A4 pages. */
    private static ConversionJob document(Path directory) throws IOException {
        Path file = directory.resolve("notes.pdf");
        try (PDDocument pdf = new PDDocument()) {
            for (int i = 0; i < 3; i++) {
                pdf.addPage(new PDPage(PDRectangle.A4));
            }
            pdf.save(Files.newOutputStream(file));
        }
        return new ConversionJob(file.toFile());
    }

    @Test
    void eachPageKeepsItsOwnCells(@TempDir Path directory) throws IOException {
        // Switching a cell off is a judgement made by looking at one page. A header in the same
        // place on page one is not a reason to drop that piece of page three unseen.
        ConversionJob job = document(directory);
        job.setTiling(Tiling.of(3, 2));

        job.setExcludedTiles(Set.of("r1c1"));
        job.setPage(2);
        assertTrue(job.excludedTiles().isEmpty(), "page 2 was never looked at");

        job.setExcludedTiles(Set.of("r3c2"));
        job.setPage(1);
        assertEquals(Set.of("r1c1"), job.excludedTiles(), "page 1 kept what it was given");
        job.setPage(2);
        assertEquals(Set.of("r3c2"), job.excludedTiles());
    }

    @Test
    void oneSayingCanBeGivenToEveryPage(@TempDir Path directory) throws IOException {
        // The recurring header: switch it off once, then say so for the whole document.
        ConversionJob job = document(directory);
        job.setTiling(Tiling.of(3, 2));
        job.setPage(2);
        job.setExcludedTiles(Set.of("r1c1", "r1c2"));

        assertEquals(3, job.applyExclusionsToAllPages());

        for (int page = 1; page <= 3; page++) {
            job.setPage(page);
            assertEquals(Set.of("r1c1", "r1c2"), job.excludedTiles(), "page " + page);
        }
    }

    @Test
    void applyingToEveryPageReplacesWhatTheyHad(@TempDir Path directory) throws IOException {
        // Replacing and not merging, so what the button says is what the document ends up in.
        ConversionJob job = document(directory);
        job.setTiling(Tiling.of(3, 2));
        job.setPage(3);
        job.setExcludedTiles(Set.of("r3c1"));
        job.setPage(1);
        job.setExcludedTiles(Set.of("r1c1"));

        job.applyExclusionsToAllPages();

        job.setPage(3);
        assertEquals(Set.of("r1c1"), job.excludedTiles());
    }

    @Test
    void puttingThemAllBackTravelsToo(@TempDir Path directory) throws IOException {
        // How a decision applied to the whole document is taken back: include everything here,
        // then say that everywhere.
        ConversionJob job = document(directory);
        job.setTiling(Tiling.of(3, 2));
        job.setExcludedTiles(Set.of("r1c1"));
        job.applyExclusionsToAllPages();

        job.setPage(2);
        job.setExcludedTiles(Set.of());
        job.applyExclusionsToAllPages();

        for (int page = 1; page <= 3; page++) {
            job.setPage(page);
            assertTrue(job.excludedTiles().isEmpty(), "page " + page);
        }
    }

    @Test
    void changingTheGridClearsEveryPage(@TempDir Path directory) throws IOException {
        ConversionJob job = document(directory);
        job.setTiling(Tiling.of(3, 2));
        job.setExcludedTiles(Set.of("r1c1"));
        job.setPage(2);
        job.setExcludedTiles(Set.of("r2c1"));

        job.setTiling(Tiling.of(6, 3));

        assertTrue(job.excludedTiles().isEmpty());
        job.setPage(1);
        assertTrue(job.excludedTiles().isEmpty(), "r1c1 of the old grid is a different piece of paper");
    }

    @Test
    void convertingPageByPageDoesNotOverwriteTheLastPage(@TempDir Path directory) throws Exception {
        // Every page used to produce notes-r1c1.g3p, so converting page two silently replaced
        // page one. The names have to come from where a piece is in the document, not from how
        // much of it was converted at a time.
        ConversionJob job = document(directory);
        job.setTiling(Tiling.of(3, 1));
        Path out = directory.resolve("out");
        ConversionOptions options = ConversionOptions.defaults(Target.CASIO_CG, Format.CP_G3P);

        job.setPage(1);
        List<Path> first = job.convert(Format.CP_G3P, options, out);
        job.setPage(2);
        List<Path> second = job.convert(Format.CP_G3P, options, out);

        assertEquals(3, first.size());
        assertEquals(3, second.size());
        assertTrue(Collections.disjoint(first, second), first + " and " + second + " overlap");
        try (var listing = Files.list(out)) {
            assertEquals(6, listing.count());
        }
        assertTrue(first.get(0).getFileName().toString().contains("-p1-"), first.get(0).toString());
        assertTrue(second.get(0).getFileName().toString().contains("-p2-"), second.get(0).toString());
    }

    @Test
    void everyPageAtOnceProducesTheSameNames(@TempDir Path directory) throws Exception {
        // Whether the document goes through a page at a time or all at once must not change what
        // anything is called, or the two ways of working produce different folders.
        ConversionJob job = document(directory);
        job.setTiling(Tiling.of(3, 1));
        ConversionOptions options = ConversionOptions.defaults(Target.CASIO_CG, Format.CP_G3P);

        Path pageByPage = directory.resolve("one-at-a-time");
        for (int page = 1; page <= 3; page++) {
            job.setPage(page);
            job.convert(Format.CP_G3P, options, pageByPage);
        }
        Path allAtOnce = directory.resolve("all-at-once");
        assertEquals(9, job.convertAllPages(Format.CP_G3P, options, allAtOnce).size());

        assertEquals(names(pageByPage), names(allAtOnce));
    }

    private static Set<String> names(Path directory) throws IOException {
        try (var listing = Files.list(directory)) {
            return listing.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test
    void anImageIsOnePageAndKnowsIt(@TempDir Path directory) throws IOException {
        ConversionJob job = job(directory);

        assertEquals(1, job.pageCount());
        assertEquals(1, job.page());
        assertTrue(job.tiling().isWhole());
    }

    @Test
    void turningPastTheEndsStaysInTheDocument(@TempDir Path directory) throws IOException {
        ConversionJob job = job(directory);

        job.setPage(9);
        assertEquals(1, job.page());
        job.setPage(0);
        assertEquals(1, job.page());
    }
}
