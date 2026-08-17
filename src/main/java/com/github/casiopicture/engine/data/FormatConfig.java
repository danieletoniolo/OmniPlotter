package com.github.casiopicture.engine.data;

import java.util.Map;

/**
 * Configuration for a specific target format, mirroring the configForFormat object in index.html.
 *
 * @param defaultWidth  Default output width.
 * @param defaultHeight Default output height.
 * @param editableSize  Whether the user can change the size.
 * @param maxWidth      Maximum allowed width.
 * @param maxHeight     Maximum allowed height.
 * @param maxColors     Maximum number of colors.
 */
public record FormatConfig(
    int defaultWidth,
    int defaultHeight,
    boolean editableSize,
    int maxWidth,
    int maxHeight,
    int maxColors
) {
    private static final Map<Format, FormatConfig> CONFIG_MAP = Map.ofEntries(
        Map.entry(Format.TI_8XV, new FormatConfig(96, 64, true, 255, 255, 256)),
        Map.entry(Format.TI_8CA, new FormatConfig(320, 240, true, 320, 240, 65536)),
        Map.entry(Format.TI_8CI, new FormatConfig(320, 240, true, 320, 240, 16)),
        Map.entry(Format.TI_8XI, new FormatConfig(96, 64, true, 96, 64, 2)),
        Map.entry(Format.TI_83I, new FormatConfig(96, 64, true, 96, 64, 2)),
        Map.entry(Format.TI_73I, new FormatConfig(96, 64, true, 96, 64, 2)),
        Map.entry(Format.TI_82I, new FormatConfig(96, 64, true, 96, 64, 2)),
        Map.entry(Format.TI_85I, new FormatConfig(96, 64, true, 96, 64, 2)),
        Map.entry(Format.TI_86I, new FormatConfig(96, 64, true, 96, 64, 2)),
        Map.entry(Format.TI_GRAPHICS_PY, new FormatConfig(128, 64, false, 128, 64, 256)),
        Map.entry(Format.KANDINSKY_PY, new FormatConfig(320, 222, false, 320, 222, 65536)),
        Map.entry(Format.CASIO_PICTURE_PY, new FormatConfig(384, 192, false, 384, 192, 65536)),
        Map.entry(Format.MICROBIT_PY, new FormatConfig(5, 5, false, 5, 5, 2)),
        Map.entry(Format.MICROBIT_SMALL_PY, new FormatConfig(5, 5, false, 5, 5, 2)),
        Map.entry(Format.TI_HUB_RGBARR_PY, new FormatConfig(16, 16, false, 16, 16, 65536)),
        Map.entry(Format.C2P, new FormatConfig(320, 528, true, 528, 528, 65536)),
        Map.entry(Format.CP_G3P, new FormatConfig(384, 192, true, 384, 192, 65536)),
        Map.entry(Format.CP01_G3P, new FormatConfig(384, 192, true, 384, 192, 65536)),
        Map.entry(Format.CP01_G4P, new FormatConfig(384, 192, true, 384, 192, 65536)),
        Map.entry(Format.I_C2P, new FormatConfig(320, 528, true, 528, 528, 8)),
        Map.entry(Format.CP_I_G3P, new FormatConfig(384, 192, true, 384, 192, 8)),
        Map.entry(Format.CP01_I_G3P, new FormatConfig(384, 192, true, 384, 192, 8)),
        Map.entry(Format.CP01_I_G4P, new FormatConfig(384, 192, true, 384, 192, 8)),
        Map.entry(Format.ZPIC, new FormatConfig(128, 64, false, 128, 64, 2))
    );

    /**
     * Gets the configuration for a specific format.
     * @param format The format to get configuration for.
     * @return The FormatConfig, or null if not found.
     */
    public static FormatConfig getFormatConfiguration(Format format) {
        return CONFIG_MAP.get(format);
    }

    /**
     * Gets the configuration for a specific format string.
     * @param formatStr The format string to get configuration for.
     * @return The FormatConfig, or null if not found.
     */
    public static FormatConfig getFormatConfiguration(String formatStr) {
        Format format = Format.fromString(formatStr);
        return getFormatConfiguration(format);
    }
}
