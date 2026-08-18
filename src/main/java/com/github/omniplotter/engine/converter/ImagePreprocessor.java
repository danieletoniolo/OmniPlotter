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
package com.github.omniplotter.engine.converter;

import com.github.omniplotter.engine.data.ConversionOptions;
import com.github.omniplotter.engine.data.Format;
import com.github.omniplotter.engine.data.FormatConfig;
import com.github.omniplotter.engine.util.Palette;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Turns a source image into the exact pixels an encoder expects.
 *
 * <p>Each format gets its own sequence of operations, transcribed from the ImageMagick command
 * lines the reference assembles in {@code handleInCanvas} (tmp/index.html:597-636). The order
 * matters and is not always the obvious one — the RGB-565 script formats reduce channel depth
 * <em>before</em> resizing, while the others resize first — so the branches below follow the
 * reference's structure rather than a tidier one.
 *
 * <p>What each family is doing:
 * <ul>
 *   <li>Opaque colour formats flatten onto white, because the calculator has no alpha to give.</li>
 *   <li>Indexed formats snap to a fixed hardware palette before the encoder looks up indices.</li>
 *   <li>Monochrome formats go grey, stretch contrast, then threshold; the ink ends up in alpha.</li>
 *   <li>zpic keeps its alpha, since the encoder emits draw commands only for opaque pixels.</li>
 * </ul>
 */
public final class ImagePreprocessor {

    private ImagePreprocessor() {}

    /**
     * The background used where the reference passes {@code -background none}.
     *
     * <p>Everywhere else it passes nothing, and ImageMagick's default background is opaque white —
     * not transparent. That distinction decides what fills the padding on fixed-size canvases.
     */
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    /** Format branches where the reference passes {@code +dither}, turning dithering off. */
    private static boolean noDither(Format format) {
        return switch (format) {
            case TI_8XV, HPPRIME_PY,
                 CASIOPLOT_G3_PY, GINT_G3_PY, NSP_NS_PY, GRAPHIC_NS_PY, GRAPHIC_G3_PY,
                 TI_GRAPHICS_PY, TI_DRAW_CE_PY, TI_DRAW_CX_PY, GRAPHIC_PY, GRAPHIC_CG_PY,
                 GINT_CG_PY, NSP_CX_PY, CASIOPLOT_CG_PY, KANDINSKY_PY, KANDINSKY_CG_PY,
                 TI_HUB_RGBARR_PY -> true;
            default -> false;
        };
    }

    /** Formats that resize to one pixel narrower and splice the column back on afterwards. */
    private static boolean splicesColumn(Format format) {
        return switch (format) {
            case TI_8CA, TI_8CI, TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> true;
            default -> false;
        };
    }

    public static BufferedImage preprocess(BufferedImage source, Format format, ConversionOptions options)
            throws IOException {
        BufferedImage image = ImageOps.toArgb(source);
        FormatConfig config = FormatConfig.of(format);

        // Whether the *source* had any transparency. Two of the monochrome script formats reorder
        // their contrast handling based on this.
        boolean sourceTransparent = ImageOps.hasTransparency(image);

        int width = config.clampWidth(options.width());
        int height = config.clampHeight(options.height());
        int colors = config.clampColors(options.colors());

        // The spliced formats resize into a canvas one pixel narrower, then get the column back.
        int resizeWidth = splicesColumn(format) ? width - 1 : width;

        // ImageMagick dithers by default; the reference disables it with `+dither` on some format
        // branches and not others. It is a persistent setting, so it also governs the `-colors`
        // step that follows. Leaving it on is what keeps a photo readable at 2 or 8 colours.
        boolean dither = !noDither(format);
        boolean exact = !options.keepRatio();
        boolean shrinkOnly = !options.enlargeSmaller();

        return switch (format) {
            case TI_8XV -> {
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.channelDepth(img, 5, 6, 5, 1);
                yield ImageOps.quantize(img, colors, dither);
            }

            case TI_8CA -> {
                BufferedImage img = ImageOps.flatten(image, Color.WHITE);
                img = resize(img, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.quantize(img, colors, dither);
                yield ImageOps.spliceColumnRight(img, Color.WHITE);
            }

            case TI_8CI -> {
                // -background none, so both the padding and the spliced column stay transparent.
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, TRANSPARENT);
                img = ImageOps.remap(img, Palette.load("pal8ci.png"), dither);
                img = ImageOps.quantize(img, colors, dither);
                yield ImageOps.spliceColumnRight(img, TRANSPARENT);
            }

            case TI_8XI, TI_83I, TI_73I, TI_82I, TI_85I, TI_86I -> {
                BufferedImage img = ImageOps.flatten(image, Color.WHITE);
                img = resize(img, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.grayscale(img);
                img = ImageOps.autoLevel(img);
                img = ImageOps.posterize(img, 2, dither);
                img = ImageOps.quantize(img, colors, dither);
                img = ImageOps.spliceColumnRight(img, Color.WHITE);
                // The ink is carried by alpha from here on: white becomes transparent and the
                // encoder packs one bit per pixel straight out of the alpha channel.
                yield ImageOps.makeTransparent(img, Color.WHITE);
            }

            case ZPIC -> {
                // -background none: the encoder emits a draw command per opaque pixel and simply
                // skips the rest, so the padding must stay transparent.
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, TRANSPARENT);
                yield ImageOps.quantize(img, colors, dither);
            }

            case C2P, CP_G3P, CP01_G3P, CP01_G4P -> {
                BufferedImage img = ImageOps.flatten(image, Color.WHITE);
                img = resize(img, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                yield ImageOps.quantize(img, colors, dither);
            }

            case I_C2P, CP_I_G3P, CP01_I_G3P, CP01_I_G4P -> {
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.remap(img, Palette.load("palcp.png"), dither);
                yield ImageOps.quantize(img, colors, dither);
            }

            case CASIOPLOT_G3_PY, GINT_G3_PY, NSP_NS_PY, GRAPHIC_NS_PY, GRAPHIC_G3_PY -> {
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.grayscale(img);
                // On a transparent source the reference thresholds before stretching contrast, and
                // otherwise stretches first. The order changes which shades survive.
                if (sourceTransparent) {
                    img = ImageOps.posterize(img, 2, dither);
                    img = ImageOps.autoLevel(img);
                } else {
                    img = ImageOps.autoLevel(img);
                    img = ImageOps.posterize(img, 2, dither);
                }
                yield ImageOps.quantize(img, colors, dither);
            }

            case TI_GRAPHICS_PY, TI_DRAW_CE_PY, TI_DRAW_CX_PY, GRAPHIC_PY, GRAPHIC_CG_PY,
                 GINT_CG_PY, NSP_CX_PY, CASIOPLOT_CG_PY, KANDINSKY_PY, KANDINSKY_CG_PY -> {
                // Depth reduction first, then resize: the reference orders it this way, and
                // resampling after the reduction lets intermediate shades back in.
                BufferedImage img = ImageOps.channelDepth(image, 5, 6, 5, 1);
                img = resize(img, resizeWidth, height, exact, shrinkOnly, config, null);
                yield ImageOps.quantize(img, colors, dither);
            }

            case HPPRIME_PY -> {
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.channelDepth(img, 8, 8, 8, 1);
                yield ImageOps.quantize(img, colors, dither);
            }

            case MICROBIT_PY, TI_HUB_MB_PY -> {
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.grayscale(img);
                img = ImageOps.autoLevel(img);
                img = ImageOps.posterize(img, 10, dither);
                img = ImageOps.quantize(img, colors, dither);
                // -colorspace RGB moves to linear light before the negate; the encoder reads the
                // brightness digits off green afterwards.
                img = ImageOps.toLinearRgb(img);
                img = ImageOps.setRed(img, 0);
                yield ImageOps.negateRed(img);
            }

            default -> {
                BufferedImage img = resize(image, resizeWidth, height, exact, shrinkOnly, config, Color.WHITE);
                img = ImageOps.channelDepth(img, 8, 8, 8, 1);
                yield ImageOps.quantize(img, colors, dither);
            }
        };
    }

    /**
     * Resizes, and for fixed-size formats pads the result out to the full canvas.
     *
     * <p>The reference only appends {@code -extent} when the canvas is not user-editable. A format
     * whose size the user can change is fitted inside the box and left at whatever size that gave;
     * a fixed-size one is always padded to exactly its canvas, because the calculator expects a
     * specific number of pixels.
     */
    private static BufferedImage resize(BufferedImage image, int width, int height,
                                        boolean exact, boolean shrinkOnly,
                                        FormatConfig config, Color background) {
        BufferedImage resized = ImageOps.resize(image, width, height, exact, shrinkOnly);
        if (!config.editableSize()) {
            resized = ImageOps.extent(resized, width, height, background);
        }
        return resized;
    }
}
