package com.github.omniplotter;

import com.github.omniplotter.cli.Cli;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command line as its users see it.
 *
 * <p>The identifiers, the columns and the exit codes are a contract: a link from img2calc turns
 * into a command here, and scripts branch on what comes back. None of that is covered by the
 * encoder tests, which reach the engine directly.
 *
 * <p>It asserts the shape of the output — headers, identifiers, exit codes — rather than the exact
 * bytes. picocli reflows its help text between versions, and a test that breaks on that would be
 * retired rather than fixed.
 */
class CliSnapshotTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void captureStreams() {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /** Runs the real parser. Not {@code Cli.main}, which would take the test JVM down with it. */
    private int run(String... args) {
        return Cli.parser().execute(args);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void helpListsEverySubcommand() {
        assertEquals(0, run("--help"));

        String help = stdout();
        for (String command : new String[]{"convert", "formats", "targets", "inspect", "update", "setup", "doctor"}) {
            assertTrue(help.contains(command), "help does not mention '" + command + "':\n" + help);
        }
    }

    @Test
    void formatsPrintsTheTableForATarget() {
        assertEquals(0, run("formats", "--target", "cg"));

        String table = stdout();
        assertTrue(table.startsWith("FORMAT"), "expected the header first:\n" + table);
        assertTrue(table.contains("CANVAS"));
        // The identifier and the canvas are the two things a caller copies out of this.
        assertTrue(table.contains("cp.g3p"), table);
        assertTrue(table.contains("384x192"), table);
    }

    @Test
    void targetsPrintsTheModelsForAMode() {
        assertEquals(0, run("targets", "--mode", "var"));

        String table = stdout();
        assertTrue(table.contains("cg"), table);
    }

    @Test
    void unknownFormatFailsWithUsageRatherThanAStackTrace() {
        // Two, not one: a mistyped argument is a different thing from a conversion that failed,
        // and a script is entitled to tell them apart.
        assertEquals(2, run("convert", "nothing.png", "-f", "not-a-format"));

        assertTrue(stderr().contains("Unknown format: not-a-format"), stderr());
        assertTrue(stderr().contains("omniplotter formats"), stderr());
        assertTrue(stdout().isEmpty(), "usage errors belong on stderr, got: " + stdout());
    }

    @Test
    void convertWritesTheFileTheFormatCallsFor(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("source.png");
        ImageIO.write(image(), "png", source.toFile());

        assertEquals(0, run("convert", source.toString(), "-f", "cp.g3p", "--name", "PICT1",
            "-o", directory.toString()));

        Path written = directory.resolve("PICT1.g3p");
        assertTrue(Files.exists(written), "expected PICT1.g3p in " + directory + ", found "
            + Files.list(directory).toList());
        assertTrue(Files.size(written) > 0);
        // The line the user reads back to check what happened.
        assertTrue(stdout().contains("cp.g3p"), stdout());
        assertTrue(stdout().contains("384x192"), stdout());
    }

    @Test
    void convertReportsAMissingFileWithoutFailingTheWholeRun(@TempDir Path directory) {
        assertEquals(1, run("convert", directory.resolve("absent.png").toString(), "-f", "cp.g3p",
            "-o", directory.toString()));

        assertTrue(stderr().contains("not a file"), stderr());
    }

    @Test
    void helpDescribesTheImageControls() {
        assertEquals(0, run("convert", "--help"));

        String help = stdout();
        for (String option : new String[]{"--look", "--dither", "--brightness", "--contrast",
                "--gamma", "--saturation", "--sharpen", "--crop", "--fill"}) {
            assertTrue(help.contains(option), "convert --help does not mention " + option + ":\n" + help);
        }
        // The candidates come from the enums themselves, so a new look appears here for free.
        assertTrue(help.contains("photo") && help.contains("document"), help);
    }

    @Test
    void aLookConvertsLikeAnythingElse(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("source.png");
        ImageIO.write(image(), "png", source.toFile());

        assertEquals(0, run("convert", source.toString(), "-f", "cp.g3p", "--look", "document",
            "--contrast", "20", "--fill", "-o", directory.toString()));

        assertTrue(Files.exists(directory.resolve("source.g3p")));
    }

    @Test
    void aMalformedCropIsAUsageErrorAndNotACrash() {
        assertEquals(2, run("convert", "nothing.png", "-f", "cp.g3p", "--crop", "1,2,3"));

        assertTrue(stderr().contains("x,y,width,height"), stderr());
        assertFalse(stderr().contains("Exception"), "a bad argument should not print a stack trace");
    }

    @Test
    void anUnknownDitherSettingListsTheRealOnes() {
        assertEquals(2, run("convert", "nothing.png", "-f", "cp.g3p", "--dither", "maybe"));

        assertTrue(stderr().contains("--dither"), stderr());
        assertTrue(stderr().toUpperCase().contains("AUTO"), stderr());
    }

    @Test
    void gridsMeasureEachWayOfCuttingAPageAgainstAStatedTypeSize() {
        assertEquals(0, run("grids", "--target", "cg"));

        String table = stdout();
        assertTrue(table.contains("DPI"), table);
        // The type size is named in the heading, because the pixel figure means nothing without it.
        assertTrue(table.contains("10 PT TEXT"), table);
        assertTrue(table.contains("Resolution comes from columns"), table);
        assertTrue(table.contains("3x1"), table);
    }

    @Test
    void aDifferentTypeSizeChangesTheAnswerAndNotTheGrids() {
        assertEquals(0, run("grids", "--target", "cg", "--text-size", "18"));

        String table = stdout();
        assertTrue(table.contains("18 PT TEXT"), table);
        // Same grids, same resolutions — larger type simply survives more of them.
        assertTrue(table.contains("3x1"), table);
        assertTrue(table.contains("12 px per line"), table);
    }

    @Test
    void aGridProducesOneFilePerTile(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("page.png");
        ImageIO.write(image(), "png", source.toFile());

        assertEquals(0, run("convert", source.toString(), "-f", "cp.g3p", "--grid", "2x2",
            "-o", directory.toString()));

        for (String tile : new String[]{"page-r1c1.g3p", "page-r1c2.g3p", "page-r2c1.g3p", "page-r2c2.g3p"}) {
            assertTrue(Files.exists(directory.resolve(tile)), "missing " + tile + " in "
                + Files.list(directory).toList());
        }
        // Said before the files appear, not after.
        assertTrue(stdout().contains("4 files"), stdout());
    }

    @Test
    void aMalformedGridIsAUsageErrorAndNotACrash() {
        assertEquals(2, run("convert", "nothing.png", "-f", "cp.g3p", "--grid", "3"));

        assertTrue(stderr().contains("rows x columns"), stderr());
        assertFalse(stderr().contains("Exception"), "a bad argument should not print a stack trace");
    }

    @Test
    void aMalformedPageSelectionIsAUsageErrorToo() {
        assertEquals(2, run("convert", "nothing.png", "-f", "cp.g3p", "--pages", "1-x"));

        assertTrue(stderr().contains("1-3,7"), stderr());
        assertFalse(stderr().contains("Exception"), stderr());
    }

    @Test
    void anImageTooSmallForTheGridSaysSoRatherThanFailingOddly(@TempDir Path directory) throws IOException {
        Path source = directory.resolve("tiny.png");
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", source.toFile());

        assertEquals(1, run("convert", source.toString(), "-f", "cp.g3p", "--grid", "8x8",
            "-o", directory.toString()));

        assertTrue(stderr().contains("too small to cut"), stderr());
    }

    private static BufferedImage image() {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 64, 32);
        g.setColor(Color.YELLOW);
        g.fillRect(0, 0, 32, 16);
        g.dispose();
        return image;
    }
}
