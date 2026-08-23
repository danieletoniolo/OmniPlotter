package com.github.omniplotter.engine.data;

/**
 * The physical size of a page, which is what turns a grid into a resolution.
 *
 * <p>Millimetres rather than pixels on purpose: how many pixels a line of text ends up being depends
 * on how much paper each tile covers, and a scan carries no such thing as a pixel size. A PDF page
 * reports its own; anything else is assumed to be the page the user says it is.
 */
public record PageSize(double widthMm, double heightMm) {

    public static final PageSize A4 = new PageSize(210, 297);
    public static final PageSize LETTER = new PageSize(215.9, 279.4);
    public static final PageSize A5 = new PageSize(148, 210);

    private static final double MM_PER_INCH = 25.4;

    public PageSize {
        if (widthMm <= 0 || heightMm <= 0) {
            throw new IllegalArgumentException("A page needs a positive size, got "
                + widthMm + "x" + heightMm + "mm");
        }
    }

    public double widthInches() {
        return widthMm / MM_PER_INCH;
    }

    public double heightInches() {
        return heightMm / MM_PER_INCH;
    }

    /** Width over height. Below one for anything held upright, which is most paper. */
    public double aspect() {
        return widthMm / heightMm;
    }

    /** The same page turned on its side, for a scan that came out landscape. */
    public PageSize rotated() {
        return new PageSize(heightMm, widthMm);
    }

    /** Reads a name — a4, letter, a5 — or a size in millimetres, as {@code 210x297}. */
    public static PageSize fromString(String value) {
        String name = value.trim().toLowerCase();
        switch (name) {
            case "a4" -> {
                return A4;
            }
            case "letter" -> {
                return LETTER;
            }
            case "a5" -> {
                return A5;
            }
            default -> { }
        }
        String[] parts = name.split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "A page is a4, letter, a5, or a size in millimetres like 210x297 — got: " + value);
        }
        try {
            return new PageSize(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A page size is two numbers in millimetres — got: " + value);
        }
    }

    @Override
    public String toString() {
        return trim(widthMm) + "x" + trim(heightMm) + "mm";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
