package com.github.omniplotter;

import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.OutputLimits;
import com.github.omniplotter.engine.data.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capacity warnings, including the ordering that makes them useful.
 *
 * <p>The thresholds nest — anything over 58.6 KiB is also over 42 and over 32 — so the branch
 * order decides which message the user actually sees. Getting that backwards would report "over
 * 32 KiB" for a file that is nowhere near fitting, which is technically true and useless.
 */
class OutputLimitsTest {

    @Test
    void filesWithinCapacityProduceNoWarning() {
        assertTrue(OutputLimits.check(Target.CASIO_CG, Format.CP_G3P, 6_000).isEmpty());
        assertTrue(OutputLimits.check(Target.TI_8X_PYTHON, Format.TI_8XV, 60_000).isEmpty());
        assertTrue(OutputLimits.check(Target.NUMWORKS_N0110, Format.KANDINSKY_PY, 30_000).isEmpty());
    }

    @Test
    void casioAndCasioLikeTargetsAreUnlimited() {
        // The reference sets no ceiling for these, so even a huge file passes without comment.
        assertTrue(OutputLimits.check(Target.CASIO_CG, Format.CP_G3P, 5_000_000).isEmpty());
        assertTrue(OutputLimits.check(Target.CASIO_CP2, Format.C2P, 5_000_000).isEmpty());
    }

    @Test
    void tiTransferCeilingAppliesToTheWholeTarget() {
        assertTrue(OutputLimits.check(Target.TI_8X_PYTHON, Format.TI_8XV, 0xFFFF).isEmpty());
        assertTrue(OutputLimits.check(Target.TI_8X_PYTHON, Format.TI_8XV, 0xFFFF + 1)
            .orElseThrow().contains("64 KiB"));
    }

    @Test
    void theTighterPythonAppCeilingAppliesOnlyToScriptFormats() {
        // 60 KB fits the transfer limit but not the Python app, and only for those two formats.
        assertTrue(OutputLimits.check(Target.TI_8X_PYTHON, Format.TI_GRAPHICS_PY, 60_000)
            .orElseThrow().contains("51.2 KB"));
        assertTrue(OutputLimits.check(Target.TI_8X_PYTHON, Format.TI_DRAW_CE_PY, 60_000)
            .orElseThrow().contains("51.2 KB"));
        assertTrue(OutputLimits.check(Target.TI_8X_PYTHON, Format.TI_8XV, 60_000).isEmpty());
    }

    @Test
    void theMostSevereNumWorksWarningWins() {
        // 60 KiB exceeds all three NumWorks thresholds; only the largest should be reported.
        String worst = OutputLimits.check(Target.NUMWORKS_N0110, Format.KANDINSKY_PY, 61_000).orElseThrow();
        assertTrue(worst.contains("58.6 KiB"), worst);
        assertFalse(worst.contains("32 KiB"), worst);

        assertTrue(OutputLimits.check(Target.NUMWORKS_N0110, Format.KANDINSKY_PY, 45_000)
            .orElseThrow().contains("42 KiB"));
        assertTrue(OutputLimits.check(Target.NUMWORKS_N0110, Format.KANDINSKY_PY, 35_000)
            .orElseThrow().contains("32 KiB"));
    }

    @Test
    void numWorksAdviceNamesTheFirmwareThatWouldFit() {
        assertTrue(OutputLimits.check(Target.NUMWORKS_N0110, Format.KANDINSKY_PY, 61_000)
            .orElseThrow().contains("Khi"));
        assertTrue(OutputLimits.check(Target.NUMWORKS_N0100, Format.KANDINSKY_PY, 45_000)
            .orElseThrow().contains("Upsilon"));
        // The N0120 has no firmware caveat at 42 KiB, just the size.
        String n0120 = OutputLimits.check(Target.NUMWORKS_N0120, Format.KANDINSKY_PY, 45_000).orElseThrow();
        assertTrue(n0120.contains("42 KiB"), n0120);
        assertFalse(n0120.contains("firmware"), n0120);
    }

    @Test
    void theN0120HasNoSmallerCeilings() {
        // Only the 42 KiB rule lists nw120; the 58.6 and 32 KiB ones do not.
        assertTrue(OutputLimits.check(Target.NUMWORKS_N0120, Format.KANDINSKY_PY, 35_000).isEmpty());
    }

    @Test
    void aMissingTargetIsNotAnError() {
        assertEquals(java.util.Optional.empty(),
            OutputLimits.check(null, Format.CP_G3P, 5_000_000));
    }
}
