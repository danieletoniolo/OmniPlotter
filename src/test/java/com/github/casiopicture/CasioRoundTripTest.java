package com.github.casiopicture;

import com.github.casiopicture.engine.EngineApi;
import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.data.Target;
import com.github.casiopicture.engine.inspect.CasioFileInspector;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Converts to each Casio format and reads the result back apart.
 *
 * <p>{@code ReferenceVectorTest} proves the bytes match img2calc. This proves something different
 * and independently useful: that the file is internally consistent. The inspector re-derives the
 * structure from scratch — un-inverting the header, checking the length recorded in one place
 * against the length recorded in another, undoing the CP obfuscation and inflating — so it would
 * catch a container that is self-contradictory even if both this port and the reference agreed on
 * producing it.
 */
class CasioRoundTripTest {

    private static final Format[] FORMATS = {
        Format.CP_G3P, Format.CP01_G3P, Format.CP01_G4P,
        Format.CP_I_G3P, Format.CP01_I_G3P, Format.CP01_I_G4P,
        Format.C2P, Format.I_C2P,
    };

    /** A gradient with some structure, so the compressed stream is not degenerate. */
    private static BufferedImage source() {
        BufferedImage img = new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 500; x++) {
                int r = x * 255 / 499;
                int g = y * 255 / 299;
                int b = (x / 25 + y / 25) % 2 == 0 ? 60 : 200;
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    @TestFactory
    Iterable<DynamicTest> producesConsistentFiles() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Format format : FORMATS) {
            tests.add(DynamicTest.dynamicTest(format.id(), () -> check(format)));
        }
        return tests;
    }

    private void check(Format format) throws Exception {
        Target target = Target.supporting(com.github.casiopicture.engine.data.Mode.VAR, format).get(0);
        // Stretch to the full canvas, so the decompressed size is exactly width * height.
        ConversionOptions options = ConversionOptions.defaults(target, format).withKeepRatio(false);

        ConversionResult result = EngineApi.convert(source(), "TESTPIC.png", format, options);
        CasioFileInspector.Report report = CasioFileInspector.inspect(result.fileBytes());

        assertTrue(report.ok(), format + ": " + String.join("; ", report.problems()));
        assertNotNull(report.pixels(), format + ": pixel data did not decompress");

        int expected = format.id().contains("_i.") || format.id().startsWith("i.")
            ? options.width() * options.height() / 2   // 4-bit indexed, two pixels per byte
            : options.width() * options.height() * 2;  // RGB-565
        assertEquals(expected, report.pixels().length, format + ": decompressed size");
    }

    @TestFactory
    Iterable<DynamicTest> rejectsCorruptedFiles() {
        return List.of(
            DynamicTest.dynamicTest("truncated file", () -> {
                var report = CasioFileInspector.inspect(new byte[64]);
                assertTrue(!report.ok(), "a 64-byte file should not pass inspection");
            }),
            DynamicTest.dynamicTest("tampered length", () -> {
                ConversionOptions options = ConversionOptions
                    .defaults(Target.CASIO_CG, Format.CP_G3P).withKeepRatio(false);
                byte[] bytes = EngineApi.convert(source(), "T.png", Format.CP_G3P, options).fileBytes();
                // Flip a bit in the stored file length; the calculator checks this field, and so
                // must the inspector.
                bytes[17] ^= 0x01;
                var report = CasioFileInspector.inspect(bytes);
                assertTrue(!report.ok(), "a file with a wrong declared size should not pass");
            }));
    }
}
