package com.github.casiopicture.engine.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A calculator model, and the formats it accepts in each mode.
 *
 * <p>Transcribed from the reference's mode/target/format resolution and its format buttons
 * (tmp/index.html:212-256 and 355-440). A target usually supports only one of the two modes: most
 * Casio and TI models take binary variables, the Python-capable ones take scripts, and a few take
 * both through different formats.
 */
public enum Target {

    // --- TI ---
    TI_8X_PYTHON("8xpython", "83PCE | 84+CE Python", Brand.TI,
        List.of(Format.TI_8XV, Format.TI_8CA, Format.TI_8CI), Format.TI_8XV,
        List.of(Format.TI_GRAPHICS_PY, Format.TI_DRAW_CE_PY, Format.MICROBIT_PY,
                Format.TI_HUB_MB_PY, Format.TI_HUB_RGBARR_PY), Format.TI_GRAPHICS_PY),

    TI_8X_ONLINE("8xonline", "MaClasseTI.fr", Brand.TI,
        List.of(), null,
        List.of(Format.TI_DRAW_CE_PY), Format.TI_DRAW_CE_PY),

    TI_8X_COLOR("8xcolor", "82A Python | 83PCE | 84+CE | 84+CSE", Brand.TI,
        List.of(Format.TI_8CA, Format.TI_8CI), Format.TI_8CA,
        List.of(), null),

    TI_8XP("8xp", "82+ | 82A | 83+ | 84+", Brand.TI,
        List.of(Format.TI_8XI), Format.TI_8XI,
        List.of(), null),

    TI_83("83", "76 | 82Stats | 83", Brand.TI,
        List.of(Format.TI_8XI, Format.TI_83I), Format.TI_8XI,
        List.of(), null),

    TI_73("73", "73", Brand.TI,
        List.of(Format.TI_8XI, Format.TI_73I), Format.TI_8XI,
        List.of(), null),

    TI_82("82", "82", Brand.TI,
        List.of(Format.TI_82I), Format.TI_82I,
        List.of(), null),

    TI_85("85", "85", Brand.TI,
        List.of(Format.TI_85I), Format.TI_85I,
        List.of(), null),

    TI_86("86", "86", Brand.TI,
        List.of(Format.TI_86I), Format.TI_86I,
        List.of(), null),

    // --- TI-Nspire ---
    NSPIRE_NS("ns", "Nspire", Brand.TI,
        List.of(), null,
        List.of(Format.NSP_NS_PY, Format.GRAPHIC_NS_PY), Format.NSP_NS_PY),

    NSPIRE_CM("cm", "Nspire CM", Brand.TI,
        List.of(), null,
        List.of(Format.NSP_CX_PY, Format.KANDINSKY_PY), Format.NSP_CX_PY),

    NSPIRE_CX("cx", "Nspire CX", Brand.TI,
        List.of(), null,
        List.of(Format.NSP_CX_PY, Format.GRAPHIC_PY, Format.KANDINSKY_PY), Format.GRAPHIC_PY),

    NSPIRE_CX2("cx2", "Nspire CX II", Brand.TI,
        List.of(), null,
        List.of(Format.NSP_CX_PY, Format.TI_DRAW_CX_PY, Format.GRAPHIC_PY, Format.KANDINSKY_PY,
                Format.MICROBIT_PY, Format.TI_HUB_MB_PY, Format.TI_HUB_RGBARR_PY), Format.TI_DRAW_CX_PY),

    // --- Casio ---
    CASIO_CG("cg", "fx-CG10/20/50 | Graph 90+E", Brand.CASIO,
        List.of(Format.CP_G3P, Format.CP01_G3P, Format.CP_I_G3P, Format.CP01_I_G3P), Format.CP_G3P,
        List.of(Format.GRAPHIC_CG_PY, Format.GINT_CG_PY, Format.KANDINSKY_CG_PY), Format.GRAPHIC_CG_PY),

    CASIO_CG2("cg2", "fx-CG50/100 | Graph 90+E/Math+", Brand.CASIO,
        List.of(), null,
        List.of(Format.CASIOPLOT_CG_PY, Format.GRAPHIC_CG_PY, Format.KANDINSKY_CG_PY, Format.GINT_CG_PY),
        Format.CASIOPLOT_CG_PY),

    CASIO_CG3("cg3", "fx-CG100 | Graph Math+", Brand.CASIO,
        List.of(Format.CP_G3P, Format.CP01_G3P, Format.CP01_G4P,
                Format.CP_I_G3P, Format.CP01_I_G3P, Format.CP01_I_G4P), Format.CP01_G4P,
        List.of(), null),

    CASIO_CP2("cp2", "fx-CP400/CG500", Brand.CASIO,
        List.of(Format.C2P, Format.I_C2P), Format.C2P,
        List.of(), null),

    CASIO_G3("g3", "fx-9750/9860GIII | Graph 35+E II", Brand.CASIO,
        List.of(), null,
        List.of(Format.CASIOPLOT_G3_PY, Format.GRAPHIC_G3_PY, Format.GINT_G3_PY), Format.CASIOPLOT_G3_PY),

    CASIO_G2_APP("g2app", "fx-9860G/GII | Graph 75/85/95", Brand.CASIO,
        List.of(), null,
        List.of(Format.GRAPHIC_G3_PY, Format.GINT_G3_PY), Format.GRAPHIC_G3_PY),

    CASIO_G2("g2", "fx-9750GII | Graph 35+E/USB", Brand.CASIO,
        List.of(), null,
        List.of(Format.GRAPHIC_G3_PY, Format.GINT_G3_PY), Format.GRAPHIC_G3_PY),

    // --- NumWorks ---
    NUMWORKS_N0100("nw100", "N0100", Brand.NUMWORKS,
        List.of(), null,
        List.of(Format.KANDINSKY_PY), Format.KANDINSKY_PY),

    NUMWORKS_N0110("nw110", "N0110", Brand.NUMWORKS,
        List.of(), null,
        List.of(Format.KANDINSKY_PY, Format.GRAPHIC_PY), Format.KANDINSKY_PY),

    NUMWORKS_N0120("nw120", "N0120", Brand.NUMWORKS,
        List.of(), null,
        List.of(Format.KANDINSKY_PY, Format.GRAPHIC_PY), Format.KANDINSKY_PY),

    // --- HP ---
    HP_PRIME("prime", "Prime", Brand.HP,
        List.of(), null,
        List.of(Format.HPPRIME_PY), Format.HPPRIME_PY),

    // --- Zero ---
    ZERO("zero", "ZGC1 | ZGC2 | ZGC3 | ZGC4", Brand.ZERO,
        List.of(Format.ZPIC), Format.ZPIC,
        List.of(), null);

    /** Manufacturer, used to group targets in the UI the way the reference groups its buttons. */
    public enum Brand {
        TI("TI"), CASIO("Casio"), NUMWORKS("NumWorks"), HP("HP"), ZERO("Zero");

        private final String label;

        Brand(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String id;
    private final String displayName;
    private final Brand brand;
    private final List<Format> varFormats;
    private final Format defaultVarFormat;
    private final List<Format> scriptFormats;
    private final Format defaultScriptFormat;

    Target(String id, String displayName, Brand brand,
           List<Format> varFormats, Format defaultVarFormat,
           List<Format> scriptFormats, Format defaultScriptFormat) {
        this.id = id;
        this.displayName = displayName;
        this.brand = brand;
        this.varFormats = varFormats;
        this.defaultVarFormat = defaultVarFormat;
        this.scriptFormats = scriptFormats;
        this.defaultScriptFormat = defaultScriptFormat;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Brand getBrand() {
        return brand;
    }

    public List<Format> getSupportedFormats(Mode mode) {
        return mode == Mode.VAR ? varFormats : scriptFormats;
    }

    /** Default format for this target in the given mode, or null if it does not support the mode. */
    public Format getDefaultFormat(Mode mode) {
        return mode == Mode.VAR ? defaultVarFormat : defaultScriptFormat;
    }

    public boolean supportsMode(Mode mode) {
        return !getSupportedFormats(mode).isEmpty();
    }

    public boolean supports(Mode mode, Format format) {
        return getSupportedFormats(mode).contains(format);
    }

    public static Target fromString(String id) {
        // The reference treats a bare "nw" as the N0110.
        if ("nw".equals(id)) {
            return NUMWORKS_N0110;
        }
        for (Target target : values()) {
            if (target.id.equals(id)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Unknown target: " + id);
    }

    public static List<Target> getByMode(Mode mode) {
        List<Target> result = new ArrayList<>();
        for (Target target : values()) {
            if (target.supportsMode(mode)) {
                result.add(target);
            }
        }
        return result;
    }

    /** Targets that can produce the given format, in declaration order. */
    public static List<Target> supporting(Mode mode, Format format) {
        List<Target> result = new ArrayList<>();
        for (Target target : values()) {
            if (target.supports(mode, format)) {
                result.add(target);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return id;
    }
}
