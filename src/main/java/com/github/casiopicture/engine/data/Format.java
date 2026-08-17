package com.github.casiopicture.engine.data;

import java.util.HashMap;
import java.util.Map;

/**
 * An output format, identified by the same string img2calc uses in its {@code format} query
 * parameter.
 *
 * <p>The set mirrors the reference exactly (tmp/index.html:141): 18 binary variable formats and 19
 * Python script generators. Keeping the ids identical means a URL from the web tool and a
 * {@code --format} flag here name the same thing.
 */
public enum Format {

    // --- TI-Z80 variables ---
    TI_8XV("im8c.8xv"),
    TI_8CA("8ca"),
    TI_8CI("8ci"),
    TI_8XI("8xi"),
    TI_83I("83i"),
    TI_82I("82i"),
    TI_73I("73i"),
    TI_85I("85i"),
    TI_86I("86i"),

    // --- Casio variables ---
    C2P("c2p"),
    I_C2P("i.c2p"),
    CP_G3P("cp.g3p"),
    CP_I_G3P("cp_i.g3p"),
    CP01_G3P("cp01.g3p"),
    CP01_I_G3P("cp01_i.g3p"),
    CP01_G4P("cp01.g4p"),
    CP01_I_G4P("cp01_i.g4p"),

    // --- Zero variables ---
    ZPIC("zpic"),

    // --- Python scripts ---
    TI_GRAPHICS_PY("ti_graphics.py"),
    TI_DRAW_CE_PY("ti_draw_ce.py"),
    TI_DRAW_CX_PY("ti_draw_cx.py"),
    CASIOPLOT_CG_PY("casioplot_cg.py"),
    CASIOPLOT_G3_PY("casioplot_g3.py"),
    GRAPHIC_PY("graphic.py"),
    GRAPHIC_NS_PY("graphic_ns.py"),
    GRAPHIC_CG_PY("graphic_cg.py"),
    GRAPHIC_G3_PY("graphic_g3.py"),
    GINT_CG_PY("gint_cg.py"),
    GINT_G3_PY("gint_g3.py"),
    KANDINSKY_PY("kandinsky.py"),
    KANDINSKY_CG_PY("kandinsky_cg.py"),
    NSP_CX_PY("nsp_cx.py"),
    NSP_NS_PY("nsp_ns.py"),
    HPPRIME_PY("hpprime.py"),
    MICROBIT_PY("microbit.py"),
    TI_HUB_MB_PY("ti_hub_mb.py"),
    TI_HUB_RGBARR_PY("ti_hub_rgbarr.py");

    private final String id;
    private final String fileExtension;

    private static final Map<String, Format> BY_ID = new HashMap<>();

    static {
        for (Format f : values()) {
            BY_ID.put(f.id, f);
        }
    }

    Format(String id) {
        this.id = id;
        // Everything after the *first* dot, or the whole id when there is none — the reference's
        // rule. "im8c.8xv" -> "8xv", "cp01_i.g3p" -> "g3p", "ti_graphics.py" -> "py", "8ca" ->
        // "8ca". zpic is the one exception, handled in getFileExtension.
        int dot = id.indexOf('.');
        this.fileExtension = dot < 0 ? id : id.substring(dot + 1);
    }

    public String id() {
        return id;
    }

    /** True for the binary variable formats, false for the Python script generators. */
    public boolean isScript() {
        return id.endsWith(".py");
    }

    /**
     * File extension for this format, without the dot. Empty for {@code zpic}: the Zero stores its
     * pictures as a bare {@code picN}.
     */
    public String getFileExtension() {
        return this == ZPIC ? "" : fileExtension;
    }

    /**
     * File extension for this format on a given target.
     *
     * <p>Ndless-based Nspire targets wrap their scripts in a {@code .tns} document, so the same
     * generator produces {@code foo.py} on one target and {@code foo.py.tns} on another.
     */
    public String getFileExtension(Target target) {
        String base = getFileExtension();
        boolean nspireDocument = target != null
            && (target == Target.NSPIRE_NS || target == Target.NSPIRE_CX || target == Target.NSPIRE_CX2)
            && (this == NSP_CX_PY || this == NSP_NS_PY || this == GRAPHIC_PY || this == GRAPHIC_NS_PY);
        return nspireDocument ? base + ".tns" : base;
    }

    /** Appends this format's extension to a base name, omitting the dot when there is none. */
    public String fileName(String baseName, Target target) {
        String extension = getFileExtension(target);
        return extension.isEmpty() ? baseName : baseName + "." + extension;
    }

    public static Format fromString(String id) {
        Format result = BY_ID.get(id);
        if (result == null) {
            throw new IllegalArgumentException("Unknown format: " + id);
        }
        return result;
    }

    @Override
    public String toString() {
        return id;
    }
}
