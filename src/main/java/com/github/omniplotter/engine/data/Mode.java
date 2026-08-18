package com.github.omniplotter.engine.data;

/**
 * Represents the output mode for the conversion.
 * <p>
 * - VAR: Generates a variable file (binary format) to be loaded on the calculator.
 * - SCRIPT: Generates a Python script that draws the image.
 */
public enum Mode {
    VAR("var"),
    SCRIPT("script");

    private final String value;

    Mode(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of this mode.
     * @return The string representation of this mode.
     */
    public String getValue() {
        return value;
    }

    /**
     * Converts a string to the corresponding Mode enum.
     * @param value The string value ("var" or "script").
     * @return The corresponding Mode enum.
     * @throws IllegalArgumentException if the value is unknown.
     */
    public static Mode fromString(String value) {
        return switch (value.toLowerCase()) {
            case "var", "variable" -> VAR;
            case "script", "python" -> SCRIPT;
            default -> throw new IllegalArgumentException("Unknown mode: " + value);
        };
    }

    @Override
    public String toString() {
        return value;
    }
}

