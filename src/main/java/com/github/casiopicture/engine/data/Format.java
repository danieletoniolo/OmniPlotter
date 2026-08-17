package com.github.casiopicture.engine.data;

import java.util.HashMap;
import java.util.Map;

public enum Format {
    TI_8XV("im8c.8xv"),
    TI_8CA("8ca"),
    TI_8CI("8ci"),
    TI_8XI("8xi"),
    TI_83I("83i"),
    TI_73I("73i"),
    TI_82I("82i"),
    TI_85I("85i"),
    TI_86I("86i"),
    TI_GRAPHICS_PY("ti_graphics.py"),
    TI_DRAW_CE_PY("ti_draw_ce.py"),
    TI_DRAW_CX_PY("ti_draw_cx.py"),
    KANDINSKY_PY("kandinsky.py"),
    CASIO_PICTURE_PY("casio_picture.py"),
    CASIOPLOT_CG_PY("casioplot_cg.py"),
    GRAPHIC_G3_PY("graphic_g3.py"),
    GINT_G3_PY("gint_g3.py"),
    GRAPHIC_CG_PY("graphic_cg.py"),
    GINT_CG_PY("gint_cg.py"),
    NSP_CX_PY("nsp_cx.py"),
    NSP_NS_PY("nsp_ns.py"),
    MICROBIT_PY("microbit.py"),
    MICROBIT_SMALL_PY("microbit_small.py"),
    TI_HUB_RGBARR_PY("ti_hub_rgbarr.py"),
    HPPRIME_PY("hpprime.py"),
    C2P("c2p"),
    CP_G3P("cp.g3p"),
    CP01_G3P("cp01.g3p"),
    CP01_G4P("cp01.g4p"),
    I_C2P("i.c2p"),
    CP_I_G3P("cp_i.g3p"),
    CP01_I_G3P("cp01_i.g3p"),
    CP01_I_G4P("cp01_i.g4p"),
    ZPIC("zpic");

    /**
     * The format string identifier.
     */
    private final String formatString;

    /**
     * The standard file extension associated with this format.
     */
    private final String fileExtension;

    /**
     * Lookup map for fast string-to-enum conversion.
     */
    private static final Map<String, Format> FORMAT_MAP = new HashMap<>();

    static {
        for (Format f : values()) {
            FORMAT_MAP.put(f.formatString, f);
        }
    }

    /**
     * Constructor to initialize the format with its corresponding format string.
     * @param formatString The format string identifier.
     */
    Format(String formatString) {
        this.formatString = formatString;
        this.fileExtension = formatString.contains(".")
            ? formatString.substring(formatString.lastIndexOf(".") + 1)
            : formatString;
    }

    /**
     * Converts a string representation of a format to the corresponding Format enum.
     * @param format The format as a string.
     * @return A {@link Format} which represent the corresponding Format enumeration.
     * @throws IllegalArgumentException if the format is unknown.
     */
    public static Format fromString(String format) {
        Format result = FORMAT_MAP.get(format);
        if (result == null) {
            throw new IllegalArgumentException("Unknown format: " + format);
        }
        return result;
    }

    /**
     * Gets the standard file extension for this format.
     * @return A {@link String} representing the file extension.
     */
    public String getFileExtension() {
        return fileExtension;
    }

    /**
     * Returns the string representation of the format, matching the original format strings.
     * @return A {@link String} which represents the format as a string.
     */
    @Override
    public String toString() {
        return formatString;
    }

}