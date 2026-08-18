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

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How a converted file is addressed on the calculator, and what counts as a legal address.
 *
 * <p>Formats fall into three groups. Some carry a variable name chosen by the user, and the name is
 * written into the file — a TI variable name must start with a capital and stay within eight
 * alphanumerics, while Casio simply caps the length. Others have no name at all: they occupy a
 * numbered slot ({@code Pic1}..{@code Pic9}, {@code Image1}..), and the slot index is encoded in
 * the bytes. Python scripts are ordinary files with no on-calculator constraint.
 *
 * <p>The slot range is a property of the <em>model</em>, not the format: a TI-73 has three picture
 * slots numbered from one, a TI-82 has seven from zero, everything else has ten from zero
 * (reference/img2calc/index.html:1288-1289). Offering 0-9 everywhere produces files that address a
 * slot the calculator does not have.
 */
public final class OnCalcName {

    private OnCalcName() {}

    /** What kind of address a format uses. */
    public enum Rule {
        /** A variable name written into the file: capital first, up to 8 alphanumerics. */
        TI_VARIABLE,
        /** A variable name written into the file: 1 to 8 characters, otherwise unconstrained. */
        CASIO_VARIABLE,
        /** A numbered slot, encoded in the file; the range depends on the target. */
        SLOT,
        /** An ordinary file, with no on-calculator naming rule. */
        NONE,
    }

    private static final Pattern TI_VARIABLE = Pattern.compile("^[A-Z][a-zA-Z0-9_]{0,7}$");
    private static final int CASIO_MAX_LENGTH = 8;

    public static Rule ruleFor(Format format) {
        return switch (format) {
            case TI_8XV, TI_85I, TI_86I -> Rule.TI_VARIABLE;
            case C2P, I_C2P, CP_G3P, CP_I_G3P, CP01_G3P, CP01_I_G3P, CP01_G4P, CP01_I_G4P
                -> Rule.CASIO_VARIABLE;
            case TI_8CA, TI_8CI, TI_8XI, TI_83I, TI_73I, TI_82I, ZPIC -> Rule.SLOT;
            default -> Rule.NONE;
        };
    }

    /** Human-readable description of what this format will accept, for prompts and errors. */
    public static String describe(Format format, Target target) {
        return switch (ruleFor(format)) {
            case TI_VARIABLE -> "up to 8 letters, digits or underscores, starting with a capital";
            case CASIO_VARIABLE -> "up to 8 characters";
            case SLOT -> {
                Target.SlotRange range = Target.slotRange(target);
                yield "a slot between " + range.min() + " and " + range.max();
            }
            case NONE -> "any file name";
        };
    }

    /** Validates a variable name, returning the reason it is unacceptable. */
    public static Optional<String> validateName(Format format, String name) {
        Rule rule = ruleFor(format);
        if (rule == Rule.SLOT || rule == Rule.NONE) {
            return Optional.empty();
        }
        if (name == null || name.isBlank()) {
            return Optional.of("The on-calculator name is required for " + format.id() + ".");
        }
        if (rule == Rule.TI_VARIABLE && !TI_VARIABLE.matcher(name).matches()) {
            return Optional.of("'" + name + "' is not a valid " + format.id()
                + " variable name: use " + describe(format, null) + ".");
        }
        if (rule == Rule.CASIO_VARIABLE && name.length() > CASIO_MAX_LENGTH) {
            return Optional.of("'" + name + "' is too long for " + format.id()
                + ": use " + describe(format, null) + ".");
        }
        return Optional.empty();
    }

    /** Validates a slot number against the target's range. */
    public static Optional<String> validateSlot(Format format, Target target, int slot) {
        if (ruleFor(format) != Rule.SLOT) {
            return Optional.empty();
        }
        Target.SlotRange range = Target.slotRange(target);
        if (slot < range.min() || slot > range.max()) {
            return Optional.of("Slot " + slot + " does not exist on this model: use "
                + describe(format, target) + ".");
        }
        return Optional.empty();
    }

    /**
     * Derives a usable name from a source file name.
     *
     * <p>Used when the user has not chosen one. A TI variable has to start with a capital and drop
     * anything outside its alphabet, so "my photo 2.png" becomes "Myphoto2" rather than being
     * rejected.
     */
    public static String suggestFrom(Format format, String fileName) {
        String base = fileName == null ? "" : fileName;
        int dot = base.indexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }

        return switch (ruleFor(format)) {
            case TI_VARIABLE -> {
                String cleaned = base.replaceAll("[^a-zA-Z0-9_]", "");
                if (cleaned.isEmpty() || !Character.isLetter(cleaned.charAt(0))) {
                    cleaned = "Image" + cleaned;
                }
                cleaned = cleaned.substring(0, Math.min(8, cleaned.length()));
                yield cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
            }
            case CASIO_VARIABLE -> {
                String cleaned = base.isBlank() ? "IMAGE" : base;
                yield cleaned.substring(0, Math.min(CASIO_MAX_LENGTH, cleaned.length()));
            }
            case SLOT, NONE -> base.isBlank() ? "IMAGE" : base;
        };
    }
}
