package com.github.casiopicture.engine.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a calculator model (target) and its supported formats.
 * <p>
 * Each target declares its supported formats for VAR and SCRIPT modes directly,
 * along with the default format for each mode.
 */
public enum Target {
    // TI Calculators
    TI_8X_PYTHON("8xpython", "TI-83 Premium CE | TI-84 Plus CE Python",
        List.of(Format.TI_8XV, Format.TI_8CA, Format.TI_8CI), Format.TI_8XV,
        List.of(Format.TI_GRAPHICS_PY, Format.TI_DRAW_CE_PY, Format.MICROBIT_PY, Format.TI_HUB_RGBARR_PY), Format.TI_GRAPHICS_PY),

    TI_8X_ONLINE("8xonline", "MaClasseTI.fr",
        List.of(), null,
        List.of(Format.TI_DRAW_CE_PY), Format.TI_DRAW_CE_PY),

    TI_8X_COLOR("8xcolor", "TI-82 Advanced Python | TI-83 Premium CE | TI-84 Plus CE | TI-84 Plus C SE",
        List.of(Format.TI_8CA, Format.TI_8CI), Format.TI_8CA,
        List.of(), null),

    TI_8XP("8xp", "TI-82 Plus | TI-82 Advanced | TI-83 Plus | TI-84 Plus",
        List.of(Format.TI_8XI), Format.TI_8XI,
        List.of(), null),

    TI_83("83", "TI-76 | TI-82 Stats | TI-83",
        List.of(Format.TI_8XI, Format.TI_83I), Format.TI_8XI,
        List.of(), null),

    TI_73("73", "TI-73",
        List.of(Format.TI_8XI, Format.TI_73I), Format.TI_8XI,
        List.of(), null),

    TI_82("82", "TI-82",
        List.of(Format.TI_82I), Format.TI_82I,
        List.of(), null),

    TI_85("85", "TI-85",
        List.of(Format.TI_85I), Format.TI_85I,
        List.of(), null),

    TI_86("86", "TI-86",
        List.of(Format.TI_86I), Format.TI_86I,
        List.of(), null),

    // TI Nspire
    NSPIRE_NS("ns", "TI-Nspire (grayscale)",
        List.of(), null,
        List.of(Format.NSP_NS_PY), Format.NSP_NS_PY),

    NSPIRE_CM("cm", "TI-Nspire CM",
        List.of(), null,
        List.of(Format.NSP_CX_PY, Format.KANDINSKY_PY), Format.NSP_CX_PY),

    NSPIRE_CX("cx", "TI-Nspire CX",
        List.of(), null,
        List.of(Format.NSP_CX_PY, Format.KANDINSKY_PY), Format.NSP_CX_PY),

    NSPIRE_CX2("cx2", "TI-Nspire CX II",
        List.of(), null,
        List.of(Format.NSP_CX_PY, Format.TI_DRAW_CX_PY, Format.KANDINSKY_PY, Format.MICROBIT_PY, Format.TI_HUB_RGBARR_PY), Format.TI_DRAW_CX_PY),

    // Casio Calculators
    CASIO_CG("cg", "fx-CG10/20/50 | Graph 90+E",
        List.of(Format.CP_G3P, Format.CP01_G3P, Format.CP_I_G3P, Format.CP01_I_G3P), Format.CP_G3P,
        List.of(Format.GRAPHIC_CG_PY, Format.GINT_CG_PY), Format.GRAPHIC_CG_PY),

    CASIO_CG2("cg2", "fx-CG50/100 | Graph 90+E/Math+",
        List.of(), null,
        List.of(Format.CASIOPLOT_CG_PY, Format.GRAPHIC_CG_PY, Format.GINT_CG_PY), Format.CASIOPLOT_CG_PY),

    CASIO_CG3("cg3", "fx-CG100 | Graph Math+",
        List.of(Format.CP_G3P, Format.CP01_G3P, Format.CP01_G4P, Format.CP_I_G3P, Format.CP01_I_G3P, Format.CP01_I_G4P), Format.CP01_G4P,
        List.of(), null),

    CASIO_CP2("cp2", "fx-CP400 | ClassPad CG500",
        List.of(Format.C2P, Format.I_C2P), Format.C2P,
        List.of(), null),

    CASIO_G3("g3", "fx-9750/9860GIII | Graph 35+E II",
        List.of(), null,
        List.of(Format.GRAPHIC_G3_PY, Format.GINT_G3_PY), Format.GRAPHIC_G3_PY),

    CASIO_G2_APP("g2app", "fx-9860G/GII | Graph 75/85/95",
        List.of(), null,
        List.of(Format.GRAPHIC_G3_PY, Format.GINT_G3_PY), Format.GRAPHIC_G3_PY),

    CASIO_G2("g2", "fx-9750GII | Graph 35+E/USB",
        List.of(), null,
        List.of(Format.GRAPHIC_G3_PY, Format.GINT_G3_PY), Format.GRAPHIC_G3_PY),

    // NumWorks Calculators
    NUMWORKS_N0100("nw100", "NumWorks N0100",
        List.of(), null,
        List.of(Format.KANDINSKY_PY), Format.KANDINSKY_PY),

    NUMWORKS_N0110("nw110", "NumWorks N0110",
        List.of(), null,
        List.of(Format.KANDINSKY_PY), Format.KANDINSKY_PY),

    NUMWORKS_N0120("nw120", "NumWorks N0120",
        List.of(), null,
        List.of(Format.KANDINSKY_PY), Format.KANDINSKY_PY),

    // HP Calculators
    HP_PRIME("prime", "HP Prime",
        List.of(), null,
        List.of(Format.HPPRIME_PY), Format.HPPRIME_PY),

    // Zero Calculators
    ZERO("zero", "ZGC1 | ZGC2 | ZGC3 | ZGC4",
        List.of(Format.ZPIC), Format.ZPIC,
        List.of(), null);

    private final String id;
    private final String displayName;
    private final List<Format> varFormats;
    private final Format defaultVarFormat;
    private final List<Format> scriptFormats;
    private final Format defaultScriptFormat;

    Target(String id, String displayName,
           List<Format> varFormats, Format defaultVarFormat,
           List<Format> scriptFormats, Format defaultScriptFormat) {
        this.id = id;
        this.displayName = displayName;
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

    /**
     * Gets the list of supported formats for this target in the given mode.
     */
    public List<Format> getSupportedFormats(Mode mode) {
        return (mode == Mode.VAR) ? varFormats : scriptFormats;
    }

    /**
     * Gets the default format for this target in the given mode.
     * @return The default format, or null if the target doesn't support this mode.
     */
    public Format getDefaultFormat(Mode mode) {
        return (mode == Mode.VAR) ? defaultVarFormat : defaultScriptFormat;
    }

    /**
     * Checks if this target supports the given mode.
     */
    public boolean supportsMode(Mode mode) {
        return !getSupportedFormats(mode).isEmpty();
    }

    /**
     * Converts a string ID to the corresponding Target enum.
     * @throws IllegalArgumentException if the ID is unknown.
     */
    public static Target fromString(String id) {
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

    /**
     * Gets all targets that support a specific mode.
     */
    public static List<Target> getByMode(Mode mode) {
        List<Target> result = new ArrayList<>();
        for (Target target : values()) {
            if (target.supportsMode(mode)) {
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

