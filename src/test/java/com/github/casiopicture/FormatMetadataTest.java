package com.github.casiopicture;

import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.data.FormatConfig;
import com.github.casiopicture.engine.data.Mode;
import com.github.casiopicture.engine.data.Target;
import com.github.casiopicture.engine.encoder.FileEncoder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the format metadata against the gaps that used to make conversions fail at runtime.
 *
 * <p>Before this existed, roughly a third of the {@link Format} values had no {@link FormatConfig}
 * entry, so choosing them threw rather than converting — a failure only reachable by actually
 * running each one.
 */
class FormatMetadataTest {

    @Test
    void everyFormatHasACanvasConfig() {
        List<String> missing = new ArrayList<>();
        for (Format format : Format.values()) {
            try {
                assertNotNull(FormatConfig.of(format));
            } catch (IllegalStateException e) {
                missing.add(format.id());
            }
        }
        if (!missing.isEmpty()) {
            fail("Formats without a FormatConfig entry: " + missing);
        }
    }

    @Test
    void everyFormatHasAnEncoder() {
        List<String> missing = new ArrayList<>();
        for (Format format : Format.values()) {
            try {
                assertNotNull(FileEncoder.getEncoder(format));
            } catch (RuntimeException e) {
                missing.add(format.id());
            }
        }
        if (!missing.isEmpty()) {
            fail("Formats without an encoder: " + missing);
        }
    }

    @Test
    void everyFormatIsReachableFromSomeTarget() {
        Set<Format> reachable = EnumSet.noneOf(Format.class);
        for (Target target : Target.values()) {
            reachable.addAll(target.getSupportedFormats(Mode.VAR));
            reachable.addAll(target.getSupportedFormats(Mode.SCRIPT));
        }
        List<String> orphans = new ArrayList<>();
        for (Format format : Format.values()) {
            if (!reachable.contains(format)) {
                orphans.add(format.id());
            }
        }
        if (!orphans.isEmpty()) {
            fail("Formats no target offers: " + orphans);
        }
    }

    @Test
    void defaultFormatIsAlwaysSupported() {
        for (Target target : Target.values()) {
            for (Mode mode : Mode.values()) {
                Format def = target.getDefaultFormat(mode);
                if (target.supportsMode(mode)) {
                    assertNotNull(def, target + "/" + mode + " supports the mode but has no default");
                    assertTrue(target.getSupportedFormats(mode).contains(def),
                        target + "/" + mode + " defaults to " + def + ", which it does not list");
                } else {
                    assertEquals(null, def, target + "/" + mode + " has a default but supports no formats");
                }
            }
        }
    }

    @Test
    void everyTargetSupportsAtLeastOneMode() {
        for (Target target : Target.values()) {
            assertTrue(target.supportsMode(Mode.VAR) || target.supportsMode(Mode.SCRIPT),
                target + " offers no formats in either mode");
        }
    }

    @Test
    void formatIdsRoundTrip() {
        for (Format format : Format.values()) {
            assertEquals(format, Format.fromString(format.id()));
        }
        for (Target target : Target.values()) {
            assertEquals(target, Target.fromString(target.getId()));
        }
    }

    @Test
    void scriptFormatsAreExactlyThePythonOnes() {
        for (Format format : Format.values()) {
            boolean listedAsScript = Target.supporting(Mode.SCRIPT, format).size() > 0;
            boolean listedAsVar = Target.supporting(Mode.VAR, format).size() > 0;
            assertEquals(format.isScript(), listedAsScript,
                format + " script-mode listing disagrees with its id");
            assertEquals(!format.isScript(), listedAsVar,
                format + " var-mode listing disagrees with its id");
        }
    }

    @Test
    void nspireScriptsBecomeTnsDocuments() {
        // Ndless-based Nspire targets wrap scripts in a .tns document; the same generator on a
        // NumWorks does not.
        assertEquals("py.tns", Format.NSP_CX_PY.getFileExtension(Target.NSPIRE_CX));
        assertEquals("py.tns", Format.GRAPHIC_PY.getFileExtension(Target.NSPIRE_CX2));
        assertEquals("py", Format.GRAPHIC_PY.getFileExtension(Target.NUMWORKS_N0110));
        assertEquals("py", Format.KANDINSKY_PY.getFileExtension(Target.NSPIRE_CX));
    }

    @Test
    void zpicHasNoExtension() {
        assertEquals("", Format.ZPIC.getFileExtension());
        assertEquals("pic1", Format.ZPIC.fileName("pic1", Target.ZERO));
        assertEquals("PICT1.g3p", Format.CP_G3P.fileName("PICT1", Target.CASIO_CG));
        assertEquals("Image1.8ca", Format.TI_8CA.fileName("Image1", Target.TI_8X_COLOR));
    }
}
