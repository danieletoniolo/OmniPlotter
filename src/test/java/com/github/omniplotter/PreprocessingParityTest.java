package com.github.omniplotter;

import com.github.omniplotter.engine.converter.ImagePreprocessor;
import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.FormatConfig;
import com.github.omniplotter.engine.data.Target;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares {@link ImagePreprocessor} against the ImageMagick pipeline it replaces.
 *
 * <p>The reference resizes and quantises by shelling out to ImageMagick (as WebAssembly, in the
 * browser). This port reimplements those operators in Java, so the pixels cannot be expected to
 * match bit for bit — resampling and median cut both admit many correct implementations. What must
 * match is the shape of the result: the same canvas, the same colour count, and pixels close
 * enough that the picture is visibly the same.
 *
 * <p>Byte-level correctness lives in {@code ReferenceVectorTest}, which feeds both sides identical
 * pixels and so is unaffected by any of this.
 *
 * <p>Skipped when ImageMagick is not installed; it is a development check, not a build dependency.
 */
class PreprocessingParityTest {

    /** Mean absolute per-channel difference accepted between the two pipelines. */
    private static final double MAX_MEAN_DELTA = 24.0;

    /**
     * Looser bound for the 1-bit formats.
     *
     * <p>With only black and white available, every pixel the two dithers place differently counts
     * a full 255, so the metric saturates in a way it does not for a colour image. A local ink
     * density within ~15% is as close as two different error-diffusion algorithms get.
     */
    private static final double MAX_MONOCHROME_DELTA = 40.0;

    private static double maxDelta(Format format) {
        return switch (format) {
            case TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> MAX_MONOCHROME_DELTA;
            default -> MAX_MEAN_DELTA;
        };
    }

    private static Path magick;
    private static Path work;
    private static boolean transparencyWorks;
    private static final List<String> REPORT = new ArrayList<>();

    @BeforeAll
    static void findMagick() throws IOException {
        for (String candidate : new String[]{"/opt/homebrew/bin/magick", "/usr/local/bin/magick", "/usr/bin/magick"}) {
            if (Files.isExecutable(Path.of(candidate))) {
                magick = Path.of(candidate);
                break;
            }
        }
        if (magick != null) {
            work = Files.createTempDirectory("omniplotter-parity");
            transparencyWorks = transparencyWorks();
        }
    }

    /**
     * Whether this ImageMagick can still make a colour transparent.
     *
     * <p>7.1.2-30 cannot: {@code -transparent white} over an all-white image returns it fully
     * opaque, with an alpha channel present and every pixel set. The formats that carry their ink
     * in alpha then look as though this port had put the ink somewhere else, when what actually
     * happened is that the thing being compared against stopped doing the operation.
     *
     * <p>Checked rather than assumed, and checked on the simplest case there is, so that the
     * failure it guards against cannot be mistaken for a subtle one. When a fixed build arrives the
     * check passes again and the comparison resumes with nobody having to remember it exists.
     */
    private static boolean transparencyWorks() {
        try {
            Path probe = work.resolve("transparency-probe.png");
            Process process = new ProcessBuilder(magick.toString(), "-size", "8x8", "xc:white",
                "-transparent", "white", probe.toString()).redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return false;
            }
            BufferedImage result = ImageIO.read(probe.toFile());
            return result != null && ((result.getRGB(0, 0) >>> 24) & 0xFF) == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @AfterAll
    static void report() {
        if (!REPORT.isEmpty()) {
            System.out.println("\npreprocessing vs ImageMagick\n" + String.join("\n", REPORT) + "\n");
        }
    }

    /** Formats whose ImageMagick command line the reference builds; one case each. */
    private static final Format[] CASES = {
        Format.CP_G3P, Format.CP_I_G3P, Format.C2P, Format.I_C2P,
        Format.TI_8XV, Format.TI_8CA, Format.TI_8CI, Format.TI_8XI, Format.TI_85I,
        Format.ZPIC,
        Format.KANDINSKY_PY, Format.CASIOPLOT_CG_PY, Format.GRAPHIC_G3_PY,
        Format.HPPRIME_PY, Format.MICROBIT_PY, Format.TI_HUB_RGBARR_PY,
    };

    @TestFactory
    Iterable<DynamicTest> matchesImageMagick() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Format format : CASES) {
            tests.add(DynamicTest.dynamicTest(format.id(), () -> check(format)));
        }
        return tests;
    }

    /** The ones whose ink ends up in the alpha channel, and so need a working {@code -transparent}. */
    private static boolean needsTransparency(Format format) {
        return switch (format) {
            case TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> true;
            default -> false;
        };
    }

    private void check(Format format) throws Exception {
        Assumptions.assumeTrue(magick != null, "ImageMagick not installed; skipping parity check");
        Assumptions.assumeTrue(transparencyWorks || !needsTransparency(format),
            "this ImageMagick cannot make a colour transparent, so it cannot say where the ink of "
                + format.id() + " belongs; skipping rather than reporting a difference it invented");

        FormatConfig config = FormatConfig.of(format);
        BufferedImage source = photoLike(640, 400);
        Path sourceFile = work.resolve("src-" + format.id().replace('.', '_') + ".png");
        ImageIO.write(source, "png", sourceFile.toFile());

        ConversionOptions options = ConversionOptions.defaults(Target.CASIO_CG, format);
        BufferedImage ours = ImagePreprocessor.preprocess(source, format, options);

        Path referenceFile = work.resolve("ref-" + format.id().replace('.', '_') + ".png");
        runReference(format, config, options, sourceFile, referenceFile);
        BufferedImage theirs = ImageIO.read(referenceFile.toFile());

        assertEquals(theirs.getWidth(), ours.getWidth(), format + ": canvas width");
        assertEquals(theirs.getHeight(), ours.getHeight(), format + ": canvas height");

        // Dithered output cannot be compared pixel by pixel: both sides scatter their rounding
        // error deliberately, and ImageMagick scatters it along a Hilbert curve while this port
        // uses Floyd-Steinberg. What has to agree is the local tone, so those formats are compared
        // as 8x8 block averages — which is roughly what the eye does with a dither pattern anyway.
        boolean dithered = DITHERED.contains(format);
        BufferedImage a = dithered ? blockAverage(ours, 8) : ours;
        BufferedImage b = dithered ? blockAverage(theirs, 8) : theirs;

        double delta = meanAbsoluteDifference(a, b);
        int oursColors = distinctColors(ours);
        int theirsColors = distinctColors(theirs);
        REPORT.add(String.format(Locale.ROOT, "  %-18s %dx%d  %s delta %5.1f  colours %d vs %d",
            format.id(), ours.getWidth(), ours.getHeight(),
            dithered ? "block" : " mean", delta, oursColors, theirsColors));

        assertTrue(delta <= maxDelta(format),
            format + ": " + (dithered ? "block-average" : "mean per-channel")
                + " difference " + delta + " exceeds " + maxDelta(format));
        assertTrue(oursColors <= config.maxColors(),
            format + ": produced " + oursColors + " colours, over the " + config.maxColors() + " limit");
    }

    /** Runs the exact argument list the reference would build for this format. */
    private void runReference(Format format, FormatConfig config, ConversionOptions options,
                              Path in, Path out) throws Exception {
        int width = config.clampWidth(options.width());
        int height = config.clampHeight(options.height());
        boolean splices = switch (format) {
            case TI_8CA, TI_8CI, TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> true;
            default -> false;
        };
        String size = (splices ? width - 1 : width) + "x" + height;
        String sizeParam = size + (options.keepRatio() ? "" : "!") + (options.enlargeSmaller() ? "" : ">");

        List<String> cmd = new ArrayList<>(List.of(magick.toString(), in.toString()));
        List<String> sizeParams = new ArrayList<>(List.of("-resize", sizeParam));
        if (!config.editableSize()) {
            sizeParams.addAll(List.of("-extent", size));
        }
        List<String> colorParams = List.of("-colors", String.valueOf(config.clampColors(options.colors())));
        List<String> depth565 = List.of("+dither", "-channel", "red", "-depth", "5",
            "-channel", "green", "-depth", "6", "-channel", "blue", "-depth", "5",
            "-channel", "alpha", "-depth", "1");

        switch (format) {
            case TI_8XV -> {
                cmd.addAll(sizeParams); cmd.addAll(depth565); cmd.addAll(colorParams);
            }
            case TI_8CA -> {
                cmd.addAll(List.of("-background", "white", "-flatten"));
                cmd.addAll(sizeParams); cmd.addAll(colorParams);
                cmd.addAll(List.of("-gravity", "northeast", "-splice", "1x0"));
            }
            case TI_8CI -> {
                cmd.addAll(List.of("-background", "none"));
                cmd.addAll(sizeParams);
                cmd.addAll(List.of("-remap", palettePath("pal8ci.png")));
                cmd.addAll(colorParams);
                cmd.addAll(List.of("-gravity", "northeast", "-splice", "1x0"));
            }
            case TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> {
                cmd.addAll(List.of("-background", "white", "-flatten"));
                cmd.addAll(sizeParams);
                cmd.addAll(List.of("-colorspace", "Gray", "-auto-level", "-posterize", "2"));
                cmd.addAll(colorParams);
                cmd.addAll(List.of("-gravity", "northeast", "-splice", "1x0", "-transparent", "white"));
            }
            case ZPIC -> {
                cmd.addAll(List.of("-background", "none"));
                cmd.addAll(sizeParams); cmd.addAll(colorParams);
            }
            case C2P, CP_G3P, CP01_G3P, CP01_G4P -> {
                cmd.addAll(List.of("-background", "white", "-flatten"));
                cmd.addAll(sizeParams); cmd.addAll(colorParams);
            }
            case I_C2P, CP_I_G3P, CP01_I_G3P, CP01_I_G4P -> {
                cmd.addAll(sizeParams);
                cmd.addAll(List.of("-remap", palettePath("palcp.png")));
                cmd.addAll(colorParams);
            }
            case CASIOPLOT_G3_PY, GINT_G3_PY, NSP_NS_PY, GRAPHIC_NS_PY, GRAPHIC_G3_PY -> {
                cmd.addAll(sizeParams);
                cmd.addAll(List.of("+dither", "-colorspace", "Gray", "-auto-level", "-posterize", "2"));
                cmd.addAll(colorParams);
            }
            case TI_GRAPHICS_PY, TI_DRAW_CE_PY, TI_DRAW_CX_PY, GRAPHIC_PY, GRAPHIC_CG_PY,
                 GINT_CG_PY, NSP_CX_PY, CASIOPLOT_CG_PY, KANDINSKY_PY, KANDINSKY_CG_PY -> {
                cmd.addAll(depth565); cmd.addAll(sizeParams); cmd.addAll(colorParams);
            }
            case HPPRIME_PY -> {
                cmd.addAll(sizeParams);
                cmd.addAll(List.of("+dither", "-channel", "alpha", "-depth", "1"));
                cmd.addAll(colorParams);
            }
            case MICROBIT_PY, TI_HUB_MB_PY -> {
                cmd.addAll(sizeParams);
                cmd.addAll(List.of("-colorspace", "Gray", "-auto-level", "-posterize", "10"));
                cmd.addAll(colorParams);
                cmd.addAll(List.of("-colorspace", "RGB", "-channel", "R", "-evaluate", "set", "0%",
                    "-channel", "R", "-evaluate", "set", "0%", "-negate"));
            }
            default -> {
                cmd.addAll(sizeParams);
                cmd.addAll(List.of("+dither", "-channel", "alpha", "-depth", "1"));
                cmd.addAll(colorParams);
            }
        }
        cmd.add(out.toString());

        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "ImageMagick timed out");
        assertEquals(0, process.exitValue(), "ImageMagick failed: " + output);
    }

    private String palettePath(String name) throws IOException {
        Path path = work.resolve(name);
        if (!Files.exists(path)) {
            try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
                Files.write(path, in.readAllBytes());
            }
        }
        return path.toString();
    }

    /** A synthetic photo: smooth gradients plus hard-edged shapes, so both stages get exercised. */
    private static BufferedImage photoLike(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (int) (127 + 127 * Math.sin(x * 0.03));
                int g = (int) (127 + 127 * Math.cos(y * 0.05));
                int b = (x / 40 + y / 40) % 2 == 0 ? 40 : 210;
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static double meanAbsoluteDifference(BufferedImage a, BufferedImage b) {
        long sum = 0;
        long n = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                // Compare visible colour only; a fully transparent pixel's RGB is not observable.
                int pa = (p >>> 24) & 0xFF;
                int qa = (q >>> 24) & 0xFF;
                sum += Math.abs(pa - qa);
                n++;
                if (pa != 0 || qa != 0) {
                    for (int shift : new int[]{16, 8, 0}) {
                        sum += Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF));
                        n++;
                    }
                }
            }
        }
        return n == 0 ? 0 : (double) sum / n;
    }

    /** Formats the reference leaves dithering enabled for; see ImagePreprocessor#noDither. */
    private static final java.util.Set<Format> DITHERED = java.util.EnumSet.of(
        Format.TI_8CA, Format.TI_8CI, Format.TI_8XI, Format.TI_83I, Format.TI_73I,
        Format.TI_82I, Format.TI_85I, Format.TI_86I, Format.ZPIC,
        Format.C2P, Format.I_C2P, Format.CP_G3P, Format.CP_I_G3P,
        Format.CP01_G3P, Format.CP01_I_G3P, Format.CP01_G4P, Format.CP01_I_G4P,
        Format.MICROBIT_PY, Format.TI_HUB_MB_PY);

    /** Downsamples to block means, so a dither pattern is judged by its tone, not its layout. */
    private static BufferedImage blockAverage(BufferedImage src, int block) {
        int w = Math.max(1, (src.getWidth() + block - 1) / block);
        int h = Math.max(1, (src.getHeight() + block - 1) / block);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int by = 0; by < h; by++) {
            for (int bx = 0; bx < w; bx++) {
                long r = 0, g = 0, b = 0, a = 0, n = 0;
                for (int y = by * block; y < Math.min((by + 1) * block, src.getHeight()); y++) {
                    for (int x = bx * block; x < Math.min((bx + 1) * block, src.getWidth()); x++) {
                        int p = src.getRGB(x, y);
                        a += (p >>> 24) & 0xFF;
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                out.setRGB(bx, by, (int) ((a / n) << 24 | (r / n) << 16 | (g / n) << 8 | (b / n)));
            }
        }
        return out;
    }

    /**
     * Distinct visible colours.
     *
     * <p>Fully transparent pixels all count as one, whatever RGB they carry: an invisible pixel is
     * not a colour choice. Without this, {@code 8ci} reads as 17 because its transparent palette
     * entry is {@code (0,0,255)} while the padding added afterwards is {@code (0,0,0)}.
     */
    private static int distinctColors(BufferedImage img) {
        var seen = new java.util.HashSet<Integer>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                seen.add((p >>> 24) == 0 ? 0 : p);
            }
        }
        return seen.size();
    }
}
