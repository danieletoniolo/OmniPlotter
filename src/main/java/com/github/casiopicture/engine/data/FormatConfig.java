package com.github.casiopicture.engine.data;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Canvas limits and presets for one output format.
 *
 * <p>A transcription of {@code configForFormat} (tmp/index.html:141). Every {@link Format} has an
 * entry; {@code FormatMetadataTest} enforces that, because a missing one is not a silent gap but a
 * conversion that throws at runtime.
 *
 * @param defaultWidth  canvas width offered initially
 * @param defaultHeight canvas height offered initially
 * @param editableSize  whether the user may change the canvas size at all; when false the image is
 *                      also padded out to the fixed canvas rather than merely fitted inside it
 * @param maxWidth      largest accepted width
 * @param maxHeight     largest accepted height
 * @param maxColors     largest accepted colour count, and the initial value
 * @param presets       named canvas sizes offered as shortcuts, e.g. full screen vs below the menu
 *                      bar
 */
public record FormatConfig(
    int defaultWidth,
    int defaultHeight,
    boolean editableSize,
    int maxWidth,
    int maxHeight,
    int maxColors,
    List<SizePreset> presets
) {

    /** A named canvas size, shown as a one-click shortcut next to the width and height fields. */
    public record SizePreset(String label, int width, int height) {}

    private static SizePreset preset(String label, int width, int height) {
        return new SizePreset(label, width, height);
    }

    private static FormatConfig cfg(int w, int h, boolean editable, int maxW, int maxH, int colors,
                                    SizePreset... presets) {
        return new FormatConfig(w, h, editable, maxW, maxH, colors, List.of(presets));
    }

    private static final Map<Format, FormatConfig> CONFIGS = new EnumMap<>(Format.class);

    static {
        // Casio ClassPad
        var classPad = new SizePreset[]{
            preset("full", 320, 528), preset("graph", 310, 401), preset("1/2 graph", 310, 185),
            preset("graph rotated", 518, 193), preset("1/2 graph rotated", 518, 81),
        };
        CONFIGS.put(Format.C2P, cfg(320, 528, true, 528, 528, 1 << 16, classPad));
        CONFIGS.put(Format.I_C2P, cfg(320, 528, true, 528, 528, 1 << 3, classPad));

        // Casio Prizm / Graph 90+E
        var g3pReset = preset("reset", 384, 192);
        CONFIGS.put(Format.CP_G3P, cfg(384, 192, true, 384, 192, 1 << 16, g3pReset));
        CONFIGS.put(Format.CP_I_G3P, cfg(384, 192, true, 384, 192, 1 << 3, g3pReset));
        CONFIGS.put(Format.CP01_G3P, cfg(384, 192, true, 384, 192, 1 << 16, g3pReset));
        CONFIGS.put(Format.CP01_I_G3P, cfg(384, 192, true, 384, 192, 1 << 3, g3pReset));
        CONFIGS.put(Format.CP01_G4P, cfg(384, 192, true, 384, 192, 1 << 16, g3pReset));
        CONFIGS.put(Format.CP01_I_G4P, cfg(384, 192, true, 384, 192, 1 << 3, g3pReset));

        // TI-Z80 variables
        var ce = new SizePreset[]{preset("full", 320, 210), preset("menu", 320, 191)};
        CONFIGS.put(Format.TI_8XV, cfg(320, 210, true, 320, 210, 1 << 8, ce));
        CONFIGS.put(Format.TI_8CA, cfg(134, 83, false, 134, 83, 1 << 16, preset("reset", 134, 83)));
        CONFIGS.put(Format.TI_8CI, cfg(266, 165, false, 266, 165, 1 << 4, preset("reset", 266, 165)));
        CONFIGS.put(Format.TI_8XI, cfg(96, 63, false, 96, 63, 1 << 1, preset("reset", 96, 63)));
        CONFIGS.put(Format.TI_83I, cfg(96, 63, false, 96, 63, 1 << 1, preset("reset", 96, 63)));
        CONFIGS.put(Format.TI_82I, cfg(96, 63, false, 96, 63, 1 << 1, preset("reset", 96, 63)));
        CONFIGS.put(Format.TI_73I, cfg(96, 63, false, 96, 63, 1 << 1, preset("reset", 96, 63)));
        CONFIGS.put(Format.TI_85I, cfg(128, 63, false, 128, 63, 1 << 1, preset("reset", 128, 63)));
        CONFIGS.put(Format.TI_86I, cfg(128, 63, false, 128, 63, 1 << 1, preset("reset", 128, 63)));

        // Zero
        CONFIGS.put(Format.ZPIC, cfg(320, 195, false, 320, 195, 1 << 16, preset("reset", 320, 195)));

        // Casio scripts. The monochrome models cap at 3 colours, not 2: black, white and the
        // transparent index.
        var g3Reset = preset("reset", 128, 64);
        CONFIGS.put(Format.CASIOPLOT_G3_PY, cfg(128, 64, true, 128, 64, 3, g3Reset));
        CONFIGS.put(Format.GRAPHIC_G3_PY, cfg(128, 64, true, 128, 64, 3, g3Reset));
        CONFIGS.put(Format.GINT_G3_PY, cfg(128, 64, true, 128, 64, 3, g3Reset));
        CONFIGS.put(Format.CASIOPLOT_CG_PY, cfg(384, 192, true, 384, 192, 1 << 8, preset("reset", 384, 192)));
        CONFIGS.put(Format.GRAPHIC_CG_PY, cfg(384, 192, true, 384, 192, 1 << 8,
            preset("full", 384, 192), preset("menu", 384, 174)));
        CONFIGS.put(Format.GINT_CG_PY, cfg(396, 224, true, 396, 224, 1 << 8,
            preset("full", 396, 224), preset("menu", 384, 206)));
        CONFIGS.put(Format.KANDINSKY_CG_PY, cfg(396, 206, true, 396, 206, 1 << 8, preset("reset", 396, 206)));

        // TI scripts
        CONFIGS.put(Format.TI_GRAPHICS_PY, cfg(320, 210, true, 320, 210, 1 << 8, ce));
        CONFIGS.put(Format.TI_DRAW_CE_PY, cfg(320, 210, true, 320, 210, 1 << 8, ce));
        CONFIGS.put(Format.TI_DRAW_CX_PY, cfg(318, 212, true, 318, 212, 1 << 8, preset("reset", 318, 212)));
        CONFIGS.put(Format.NSP_CX_PY, cfg(320, 240, true, 320, 240, 1 << 8, preset("reset", 320, 240)));
        CONFIGS.put(Format.NSP_NS_PY, cfg(320, 240, true, 320, 240, 3, preset("reset", 320, 240)));
        CONFIGS.put(Format.GRAPHIC_PY, cfg(320, 222, true, 320, 222, 1 << 8,
            preset("full", 320, 222), preset("menu", 320, 205)));
        CONFIGS.put(Format.GRAPHIC_NS_PY, cfg(320, 222, true, 320, 222, 3,
            preset("full", 320, 222), preset("menu", 320, 205)));

        // NumWorks / HP scripts
        CONFIGS.put(Format.KANDINSKY_PY, cfg(320, 222, true, 320, 222, 1 << 8, preset("reset", 320, 222)));
        CONFIGS.put(Format.HPPRIME_PY, cfg(320, 240, true, 320, 240, 1 << 8,
            preset("full", 320, 240), preset("menu", 320, 220)));

        // micro:bit and TI-Innovator. 10 here is a shade count, not a power of two: the display has
        // ten brightness levels.
        CONFIGS.put(Format.MICROBIT_PY, cfg(5, 5, false, 5, 5, 10, preset("reset", 5, 5)));
        CONFIGS.put(Format.TI_HUB_MB_PY, cfg(5, 5, false, 5, 5, 10, preset("reset", 5, 5)));
        CONFIGS.put(Format.TI_HUB_RGBARR_PY, cfg(8, 2, false, 8, 2, 1 << 24, preset("reset", 8, 2)));
    }

    public static FormatConfig of(Format format) {
        FormatConfig config = CONFIGS.get(format);
        if (config == null) {
            throw new IllegalStateException("No canvas configuration for format: " + format);
        }
        return config;
    }

    public static FormatConfig of(String formatId) {
        return of(Format.fromString(formatId));
    }

    /** Clamps a requested canvas to what the format accepts, honouring fixed-size formats. */
    public int clampWidth(int width) {
        return editableSize ? Math.max(1, Math.min(width, maxWidth)) : defaultWidth;
    }

    public int clampHeight(int height) {
        return editableSize ? Math.max(1, Math.min(height, maxHeight)) : defaultHeight;
    }

    public int clampColors(int colors) {
        return Math.max(1, Math.min(colors, maxColors));
    }
}
