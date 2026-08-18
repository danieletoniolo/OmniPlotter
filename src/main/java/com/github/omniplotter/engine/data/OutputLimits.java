/*
 * OmniPlotter — convert images to calculator picture and script formats.
 * Copyright (C) 2026 Daniele Toniolo
 *
 * Derived from TI-Planet's img2calc (https://github.com/TI-Planet/img2calc),
 * by Xavier Andreani (@critor) and Adrien Bertrand (@Adriweb).
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.github.omniplotter.engine.data;

import java.util.Optional;

/**
 * Capacity limits of the target calculators.
 *
 * <p>A conversion can succeed and still produce a file the calculator cannot accept — too large to
 * transfer, too large for the Python app to load, or simply larger than the free space a given
 * firmware leaves. The file is valid; it just will not fit. Without this check that only becomes
 * apparent in front of the device, which is the failure mode the whole byte-exactness effort exists
 * to avoid.
 *
 * <p>Ported from the chain at the end of {@code handleOutImg}
 * (reference/img2calc/index.html:1668-1706). It is an {@code if/else} chain, so <b>only the first
 * applicable warning is reported</b>, and the branches are ordered from most to least severe. That
 * ordering is deliberate: a script over 58.6 KiB is also over 42 and over 32, and the reference
 * reports the one that matters.
 *
 * <p>The upstream branch for the TI-84 "Evo" ({@code 8x2}) is omitted, since this port does not
 * support that target yet.
 */
public final class OutputLimits {

    private OutputLimits() {}

    private static final int TI_TRANSFER_MAX = 0xFFFF;      // 64 KiB
    private static final int TI_PYTHON_APP_MAX = 51_200;    // 51.2 KB
    private static final int NW_KHI_MAX = 0xEA5E;           // 58.6 KiB
    private static final int NW_UPSILON_MAX = 0xA7FE;       // 42 KiB
    private static final int NW_OMEGA_MAX = 0x7FFE;         // 32 KiB

    private static final String RETRY =
        "Retry with a smaller canvas, fewer colours, or a simpler image.";

    /**
     * Returns a warning if the encoded output exceeds what the target can hold.
     *
     * @param byteLength size of the produced file
     * @return the single most severe applicable warning, or empty if the file fits
     */
    public static Optional<String> check(Target target, Format format, int byteLength) {
        if (target == null) {
            return Optional.empty();
        }

        if (target == Target.TI_8X_PYTHON && byteLength > TI_TRANSFER_MAX) {
            return Optional.of("Over 64 KiB: the file will not transfer to the calculator in full. " + RETRY);
        }
        if (target == Target.TI_8X_PYTHON
            && (format == Format.TI_GRAPHICS_PY || format == Format.TI_DRAW_CE_PY)
            && byteLength > TI_PYTHON_APP_MAX) {
            return Optional.of("Over 51.2 KB: the calculator's Python app will not load the script. " + RETRY);
        }

        // The NumWorks limits depend on the firmware, not just the model, so the advice names the
        // firmware that would still fit.
        boolean nw100 = target == Target.NUMWORKS_N0100;
        boolean nw110 = target == Target.NUMWORKS_N0110;
        boolean nw120 = target == Target.NUMWORKS_N0120;

        if ((nw100 || nw110) && byteLength > NW_KHI_MAX) {
            return Optional.of("Over 58.6 KiB: will not fit"
                + (nw110 ? " on the Omega, Upsilon or Epsilon firmwares — use Khi. " : ". ")
                + RETRY);
        }
        if ((nw100 || nw110 || nw120) && byteLength > NW_UPSILON_MAX) {
            String firmware = nw110 ? " on the Omega or Epsilon firmwares — use Khi or Upsilon. "
                            : nw100 ? " on the Omega or Epsilon firmwares — use Upsilon. "
                            : ". ";
            return Optional.of("Over 42 KiB: will not fit" + firmware + RETRY);
        }
        if ((nw100 || nw110) && byteLength > NW_OMEGA_MAX) {
            return Optional.of("Over 32 KiB: will not fit on the Omega firmware — use "
                + (nw110 ? "Khi, Upsilon or Epsilon. " : "Upsilon or Epsilon. ")
                + RETRY);
        }

        return Optional.empty();
    }
}
