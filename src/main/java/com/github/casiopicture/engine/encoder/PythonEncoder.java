package com.github.casiopicture.engine.encoder;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.data.Target;
import com.github.casiopicture.engine.util.EncoderUtils;
import com.github.casiopicture.engine.util.PixelBuffer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the Python scripts that draw an image on a calculator.
 *
 * <p>Unlike the binary formats, the output here is source code: a self-contained {@code draw_image}
 * function, a palette, and the image as RLE-compressed byte literals. The module it imports and the
 * drawing primitive it calls vary by format — {@code kandinsky}, {@code casioplot}, {@code gint},
 * {@code ti_draw} and so on all expose different APIs.
 *
 * <p>Ported from {@code handleOutImgPythonRLE}, {@code handleOutImgPythonMB} and
 * {@code handleOutImgPythonRGBARR} (tmp/index.html:746-1148) and checked against the reference
 * byte for byte, whitespace and comments included.
 */
public class PythonEncoder implements FileEncoder {

    private PythonEncoder() {}

    private static final class Holder {
        static final PythonEncoder INSTANCE = new PythonEncoder();
    }

    public static PythonEncoder getInstance() {
        return Holder.INSTANCE;
    }

    private static final String CREDIT = "#image converted on TI-Planet\n#tiplanet.org/img2calc\n\n";

    @Override
    public ConversionResult encode(BufferedImage image, Format format, String originalFileName,
                                   ConversionOptions options) throws IOException {
        Target target = options.target();
        String script = switch (format) {
            case MICROBIT_PY, TI_HUB_MB_PY -> microbitScript(image, format, target);
            case TI_HUB_RGBARR_PY -> rgbArrayScript(image, target);
            default -> rleScript(image, format, target);
        };

        String baseName = originalFileName;
        int dot = baseName.indexOf('.');
        if (dot >= 0) {
            baseName = baseName.substring(0, dot);
        }
        return new ConversionResult(EncoderUtils.scriptToBytes(script), format.fileName(baseName, target));
    }

    // --- shared palette handling ------------------------------------------------------------

    /**
     * Palette of a preprocessed image, in first-seen order, plus the index of the first fully
     * transparent colour.
     *
     * <p>Colours are compared after truncation to the target's bit depth, so shades the display
     * could not tell apart collapse into one entry.
     */
    private record IndexedImage(List<int[]> palette, int[] indices, int transparentIndex) {}

    private static IndexedImage index(PixelBuffer px, int rBits, int gBits, int bBits, int aBits) {
        List<int[]> palette = new ArrayList<>();
        // The reference rescans the whole palette for every pixel; a hash on the packed colour
        // gives the same first-seen ordering without the quadratic cost.
        Map<Integer, Integer> seen = new HashMap<>();
        int[] indices = new int[px.size()];
        int transparent = -1;

        for (int i = 0; i < px.size(); i++) {
            int[] color = px.simplified(i, rBits, gBits, bBits, aBits);
            int key = color[0] << 24 | color[1] << 16 | color[2] << 8 | color[3];
            Integer existing = seen.get(key);
            if (existing == null) {
                existing = palette.size();
                seen.put(key, existing);
                palette.add(color);
                if (transparent < 0 && color[3] == 0) {
                    transparent = existing;
                }
            }
            indices[i] = existing;
        }
        return new IndexedImage(palette, indices, transparent);
    }

    /** Port of {@code color2int}: pack a colour with the reference's channel order. */
    private static int color2int(int[] c, int rBits, int gBits, int bBits, int aBits) {
        return c[2] >> (8 - rBits)
             | c[1] >> (8 - gBits) << rBits
             | c[0] >> (8 - bBits) << (rBits + gBits)
             | c[3] >> (8 - aBits) << (rBits + gBits + bBits);
    }

    // --- RLE scripts (the majority of formats) ----------------------------------------------

    private String rleScript(BufferedImage image, Format format, Target target) {
        PixelBuffer px = PixelBuffer.of(image);

        // hpprime keeps full 8-bit channels; everything else works in RGB-565 with a 1-bit alpha.
        int r = 5, g = 6, b = 5, a = 1;
        if (format == Format.HPPRIME_PY) {
            r = 8; g = 8; b = 8;
        }

        IndexedImage indexed = index(px, r, g, b, a);
        int npal = indexed.palette().size();

        // Index width is the minimum that addresses the palette; the run length shares the byte.
        int nbits = 0;
        for (int t = npal - 1; t != 0; t >>= 1) {
            nbits++;
        }
        CharSequence im = encodeRle(px, indexed.indices(), nbits);

        StringBuilder py = new StringBuilder(CREDIT);
        py.append(importLine(format));
        py.append(drawFunction(format, target));
        py.append(paletteBlock(format, indexed.palette(), r, g, b, a));
        py.append("#your image data\n");
        py.append('#').append(px.width()).append('x').append(px.height())
          .append(" RLE-").append(nbits).append(" pixels\n");
        py.append("image = (\n");

        // Casio's on-calc editor refuses lines over 256 characters, and the graphic module's limit
        // is tighter still; other targets take one long literal.
        int lmax = switch (format) {
            case CASIOPLOT_CG_PY, CASIOPLOT_G3_PY -> 256;
            case GRAPHIC_CG_PY, GRAPHIC_G3_PY, GRAPHIC_PY, GRAPHIC_NS_PY -> 128;
            default -> 0;
        };
        py.append(EncoderUtils.stringToPythonBytes(im, lmax, target != Target.TI_8X_ONLINE));
        py.append(")\n\n");
        py.append(sampleCall(format, px.width(), indexed.transparentIndex()));
        return py.toString();
    }

    /**
     * Run-length encodes the palette indices into the packed byte stream {@code draw_image} reads.
     *
     * <p>Each byte carries the colour index in its low {@code nbits} and a run length above it.
     * When the run does not fit, bit 7 is set and a continuation byte holds the high bits. The
     * 8-bit case is special: there is no room for a length at all, so every entry takes a
     * continuation byte.
     */
    private static CharSequence encodeRle(PixelBuffer px, int[] indices, int nbits) {
        StringBuilder im = new StringBuilder();
        int maskcnt = 0xFF >> nbits >> 1;
        int c = 0;
        int prec = 0;

        for (int y = 0; y < px.height(); y++) {
            for (int x = 0; x < px.width(); x++) {
                int cour = indices[y * px.width() + x];
                if (x == 0 && y == 0) {
                    prec = cour;
                    c = 0;
                }
                if (prec == cour) {
                    c += 1;
                }
                boolean last = y + 1 == px.height() && x + 1 == px.width();
                if (prec != cour || last) {
                    while (c > 0) {
                        int tc = c;
                        int vim = prec;
                        int ic;
                        String cont = "";
                        if (tc <= maskcnt) {
                            ic = tc;
                            c -= ic;
                            ic -= 1;
                            vim |= ic << nbits;
                        } else {
                            ic = Math.min(tc, (1 << (15 - nbits + (nbits == 8 ? 1 : 0))) - 1);
                            c -= ic;
                            ic -= 1;
                            vim |= (((ic & maskcnt) << nbits) | (1 << (7 + (nbits == 8 ? 1 : 0)))) & 0xFF;
                            cont = String.valueOf((char) (ic >> (7 - nbits + (nbits == 8 ? 1 : 0))));
                        }
                        im.append((char) vim).append(cont);
                    }
                    if (prec != cour) {
                        c = 1;
                        prec = cour;
                        // A colour change on the very last pixel leaves that pixel unemitted by the
                        // loop above, so it is appended here as a bare run of one.
                        if (last) {
                            im.append((char) cour);
                            if (nbits == 8) {
                                im.append((char) 0);
                            }
                        }
                    }
                }
            }
        }
        return im;
    }

    private static String importLine(Format format) {
        return switch (format) {
            case TI_GRAPHICS_PY -> "from ti_graphics import fillRect, setColor\n";
            case TI_DRAW_CX_PY, TI_DRAW_CE_PY -> "from ti_draw import fill_rect, set_color\n";
            case CASIOPLOT_CG_PY, CASIOPLOT_G3_PY -> "from casioplot import set_pixel\n\n";
            case HPPRIME_PY -> "from hpprime import fillrect\n";
            case GRAPHIC_PY, GRAPHIC_NS_PY, GRAPHIC_G3_PY, GRAPHIC_CG_PY -> "from graphic import fill_rect\n";
            case GINT_G3_PY, GINT_CG_PY -> "from gint import drect\n";
            case KANDINSKY_PY, KANDINSKY_CG_PY -> "from kandinsky import fill_rect\n";
            default -> "";
        };
    }

    /** The decoder emitted into the script. Only the innermost drawing call differs per format. */
    private static String drawFunction(Format format, Target target) {
        boolean layered = format == Format.HPPRIME_PY
            || format == Format.NSP_CX_PY || format == Format.NSP_NS_PY;

        StringBuilder py = new StringBuilder();
        py.append("\n#the image drawing function\n");
        if (layered) {
            py.append("#- layer to draw on\n");
        }
        py.append("#- rle : image RLE-compressed data\n");
        py.append("#- w : width of image\n");
        py.append("#- pal : palette of colors to use with image\n");
        py.append("#- zoomx : horizontal zoom\n");
        py.append("#- zoomy : vertical zoom\n");
        py.append("#- itransp : index of 1 transparent color in palette or -1 if none\n");
        py.append(layered
            ? "def draw_image(layer, rle, x0, y0, w, pal, zoomx=1, zoomy=1, itransp=-1):\n"
            : "def draw_image(rle, x0, y0, w, pal, zoomx=1, zoomy=1, itransp=-1):\n");
        py.append("  i, x = 0, 0\n");
        py.append("  x0, y0 = int(x0), int(y0)\n");
        py.append("  nvals = len(pal)\n");
        py.append("  nbits = 0\n");
        py.append("  nvals -= 1\n");
        py.append("  while(nvals):\n");
        py.append("    nvals >>= 1\n");
        py.append("    nbits += 1\n");
        py.append("  maskval = (1 << nbits) - 1\n");
        py.append("  maskcnt = (0xFF >> nbits >> 1) << nbits\n");
        py.append("  while i<len(rle):\n");
        py.append("    v = rle[i]\n");
        py.append("    mv = v & maskval\n");
        py.append("    c = (v & maskcnt) >> nbits\n");
        py.append("    if (v & 0b10000000 or nbits == 8):\n");
        py.append("      i += 1\n");
        py.append("      c |= rle[i] << (7 - nbits + (nbits == 8))\n");
        py.append("    c = c + 1\n");
        py.append("    while c:\n");
        py.append("      cw = min(c, w - x)\n");
        py.append("      if mv != itransp:\n");

        // Mirrors the reference's if / if-else chain exactly. ti_draw_cx is tested by a standalone
        // `if`, so for that format its branch runs *and* the trailing `else` runs after it.
        if (format == Format.TI_DRAW_CX_PY) {
            py.append("        set_color(pal[mv])\n");
            py.append("        fill_rect(x0 + x*zoomx, y0, cw*zoomx, zoomy)\n");
        }
        if (format == Format.TI_DRAW_CE_PY) {
            py.append("        set_color(*pal[mv])\n");
            py.append(target == Target.TI_8X_ONLINE
                ? "        fill_rect(x0 + x*zoomx, y0, cw*zoomx, zoomy)\n"
                : "        fill_rect(x0 + x*zoomx - 1, y0 - 1, cw*zoomx + 1, zoomy + 1)\n");
        } else if (format == Format.TI_GRAPHICS_PY) {
            py.append("        setColor(pal[mv])\n");
            py.append("        fillRect(x0 + x*zoomx, y0, cw*zoomx, zoomy)\n");
        } else if (format == Format.CASIOPLOT_CG_PY || format == Format.CASIOPLOT_G3_PY) {
            py.append("        col = pal[mv]\n");
            py.append("        for l in range(0, zoomy, zoomy < 0 and -1 or 1):\n");
            py.append("          for k in range(cw):\n");
            py.append("            for p in range(0, zoomx, zoomx < 0 and -1 or 1):\n");
            py.append("              set_pixel(x0 + (x + k)*zoomx + p - (zoomx < 0), y0 + l - (zoomy < 0), col)\n");
        } else if (format == Format.HPPRIME_PY) {
            py.append("        fillrect(layer, x0 + x*zoomx, y0, cw*zoomx, zoomy, pal[mv], pal[mv])\n");
        } else if (format == Format.NSP_CX_PY || format == Format.NSP_NS_PY) {
            py.append("        col = pal[mv]\n");
            py.append("        for l in range(0, zoomy, zoomy < 0 and -1 or 1):\n");
            py.append("          for k in range(cw):\n");
            py.append("            for p in range(0, zoomx, zoomx < 0 and -1 or 1):\n");
            py.append("              layer.setPx(x0 + (x + k)*zoomx + p - (zoomx < 0), y0 + l - (zoomy < 0), col)\n");
        } else if (format == Format.GINT_CG_PY || format == Format.GINT_G3_PY) {
            py.append("        drect(x0 + x*zoomx, y0, cw*zoomx, zoomy, pal[mv])\n");
        } else {
            py.append("        fill_rect(x0 + x*zoomx, y0, cw*zoomx, zoomy, pal[mv])\n");
        }

        py.append("      c -= cw\n");
        py.append("      x = (x + cw) % w\n");
        py.append("      y0 += x == 0 and zoomy\n");
        py.append("    i += 1\n\n\n");
        return py.toString();
    }

    /**
     * Named colours some modules accept in place of a literal.
     *
     * <p>Transcribed with the reference's quirks intact: the "cyan" row has five components and
     * "magenta" only three. Both get normalised to four by {@code simplPixel_RGBA}, missing
     * components reading as zero, which is why "magenta" ends up naming a colour no image produces.
     */
    private record NamedColors(String[] names, int[][] values) {}

    private static NamedColors namedColors(Format format) {
        if (format == Format.KANDINSKY_PY || format == Format.KANDINSKY_CG_PY) {
            return new NamedColors(
                new String[]{"w", "k", "gray", "r", "g", "b", "y", "brown", "pink", "orange", "purple", "cyan", "magenta"},
                new int[][]{
                    {255, 255, 255, 255}, {0, 0, 0, 255}, {0xa7, 0xa7, 0xa7, 255}, {255, 0, 0, 255},
                    {0x50, 0xc1, 0x02, 255}, {0, 0, 255, 255}, {255, 255, 0, 255}, {0x8d, 0x73, 0x50, 255},
                    {0xff, 0xab, 0xb6, 255}, {0xfe, 0x87, 0x1f, 255}, {0x6e, 0x2d, 0x79, 255},
                    {0, 255, 255, 255, 255}, {255, 5, 136},
                });
        }
        if (format == Format.GRAPHIC_PY || format == Format.GRAPHIC_NS_PY
            || format == Format.GRAPHIC_CG_PY || format == Format.GRAPHIC_G3_PY) {
            return new NamedColors(
                new String[]{"", "black", "red", "green", "blue", "yellow", "cyan", "magenta"},
                new int[][]{
                    {255, 255, 255, 255}, {0, 0, 0, 255}, {255, 0, 0, 255}, {0x50, 0xc1, 0x02, 255},
                    {0, 0, 255, 255}, {255, 255, 0, 255}, {0, 255, 255, 255, 255}, {255, 5, 136},
                });
        }
        return new NamedColors(new String[0], new int[0][]);
    }

    private String paletteBlock(Format format, List<int[]> palette, int r, int g, int b, int a) {
        boolean rgb565Ints = format == Format.NSP_CX_PY || format == Format.NSP_NS_PY
            || format == Format.GINT_CG_PY || format == Format.GINT_G3_PY;
        boolean graphic = format == Format.GRAPHIC_PY || format == Format.GRAPHIC_NS_PY
            || format == Format.GRAPHIC_CG_PY || format == Format.GRAPHIC_G3_PY;
        boolean kandinsky = format == Format.KANDINSKY_PY || format == Format.KANDINSKY_CG_PY;
        boolean casioplot = format == Format.CASIOPLOT_CG_PY || format == Format.CASIOPLOT_G3_PY;

        NamedColors named = namedColors(format);
        int[][] candidates = new int[named.values().length][];
        for (int k = 0; k < candidates.length; k++) {
            int[] v = named.values()[k];
            // simplPixel_RGBA reads exactly four components: a shorter row reads the missing ones
            // as zero, a longer one has its tail ignored.
            candidates[k] = new int[]{
                PixelBuffer.simplify(v.length > 0 ? v[0] : 0, r),
                PixelBuffer.simplify(v.length > 1 ? v[1] : 0, g),
                PixelBuffer.simplify(v.length > 2 ? v[2] : 0, b),
                PixelBuffer.simplify(v.length > 3 ? v[3] : 0, a),
            };
        }

        StringBuilder py = new StringBuilder();
        py.append("#palette for your image\n");
        py.append('#').append(palette.size()).append(' ')
          .append(format == Format.NSP_CX_PY || format == Format.NSP_NS_PY ? "RGB-565" : "RGB-888")
          .append(" colors\n");
        py.append("palette = (\n");

        StringBuilder line = new StringBuilder();
        for (int[] color : palette) {
            String value;
            if (rgb565Ints) {
                value = color2int(color, 5, 6, 5, 0) + ",";
            } else if (format == Format.HPPRIME_PY) {
                value = color2int(color, 8, 8, 8, 0) + ",";
            } else {
                value = "(" + color[0] + "," + color[1] + "," + color[2] + "),";
                // Shortest spelling wins, ties going to the alternative.
                int namedIndex = indexOf(color, candidates);
                if (namedIndex >= 0) {
                    String alt = "\"" + named.names()[namedIndex] + "\",";
                    if (alt.length() <= value.length()) {
                        value = alt;
                    }
                }
                if (graphic) {
                    String alt = color2int(color, 5, 6, 5, 0) + ",";
                    if (alt.length() <= value.length()) {
                        value = alt;
                    }
                } else if (kandinsky) {
                    String alt = String.format("\"#%02x%02x%02x\",", color[0], color[1], color[2]);
                    if (alt.length() <= value.length()) {
                        value = alt;
                    }
                }
            }

            if ((casioplot && line.length() + value.length() > 256)
                || (graphic && line.length() + value.length() > 128)) {
                py.append(line).append('\n');
                line.setLength(0);
            }
            line.append(value);
        }
        py.append(line).append('\n');
        py.append(")\n\n");
        return py.toString();
    }

    /** Port of {@code indexOfArrayInArray} over four-component colours. */
    private static int indexOf(int[] color, int[][] candidates) {
        outer:
        for (int i = 0; i < candidates.length; i++) {
            for (int k = 0; k < 4; k++) {
                if (candidates[i][k] != color[k]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String sampleCall(Format format, int width, int transparentIndex) {
        String call = "draw_image(image, 0, 0, " + width
            + ", palette, zoomx=1, zoomy=1, itransp=" + transparentIndex + ")\n";
        StringBuilder py = new StringBuilder("#image drawing code sample\n");
        switch (format) {
            case HPPRIME_PY -> {
                py.append("from hpprime import eval\n");
                py.append("draw_image(0, image, 0, 0, ").append(width)
                  .append(", palette, zoomx=1, zoomy=1, itransp=").append(transparentIndex).append(")\n");
                py.append("eval(\"wait()\")\n");
            }
            case NSP_CX_PY, NSP_NS_PY -> {
                py.append("from nsp import Texture, waitKeypress\n");
                py.append("layer = Texture(320, 240, 0)\n");
                py.append("layer.fill(0)\n");
                py.append("draw_image(layer, image, 0, 0, ").append(width)
                  .append(", palette, zoomx=1, zoomy=1, itransp=").append(transparentIndex).append(")\n");
                py.append("layer.display()\n");
                py.append("waitKeypress()\n");
            }
            case TI_GRAPHICS_PY -> {
                py.append("from ti_system import disp_wait\n");
                py.append("draw_image(image, 0, 30, ").append(width)
                  .append(", palette, zoomx=1, zoomy=1, itransp=").append(transparentIndex).append(")\n");
                py.append("disp_wait()\n");
            }
            case TI_DRAW_CE_PY -> {
                py.append("from ti_draw import show_draw\n");
                py.append("draw_image(image, 0, 30, ").append(width)
                  .append(", palette, zoomx=1, zoomy=1, itransp=").append(transparentIndex).append(")\n");
                py.append("show_draw()\n");
            }
            case TI_DRAW_CX_PY -> {
                py.append("from ti_draw import use_buffer, paint_buffer\n");
                py.append("use_buffer()\n");
                py.append(call);
                py.append("paint_buffer()\n");
            }
            case CASIOPLOT_CG_PY, CASIOPLOT_G3_PY -> {
                py.append("from casioplot import show_screen\n");
                py.append(call);
                py.append("show_screen()\n");
            }
            default -> py.append(call);
        }
        return py.toString();
    }

    // --- micro:bit ---------------------------------------------------------------------------

    /** The 5x5 LED display: ten brightness levels, written as digits in a colon-separated string. */
    private String microbitScript(BufferedImage image, Format format, Target target) {
        PixelBuffer px = PixelBuffer.of(image);
        StringBuilder py = new StringBuilder(CREDIT);

        if (format == Format.MICROBIT_PY) {
            if (target == Target.TI_8X_PYTHON) {
                py.append("from microbit import *\n");
                py.append("from mb_disp import display, Image\n");
            } else {
                py.append("from microbit import display, Image\n");
            }
        } else if (target == Target.NSPIRE_CX2) {
            py.append("from ti_innovator import send\n");
        } else if (target == Target.TI_8X_PYTHON) {
            py.append("from ti_hub import send\n");
        }
        py.append('\n');

        if (format == Format.TI_HUB_MB_PY) {
            py.append("#function to send the micro:bit Python code to run\n");
            py.append("def send_microbit(cmd):\n");
            py.append("  send(\"\\x04\")\n");
            py.append("  send(cmd)\n");
            py.append("  send(\"\\x05\")\n");
            py.append('\n');
        }

        py.append("def draw_mb_image(img):\n");
        if (format == Format.MICROBIT_PY) {
            py.append("  display.show(Image(img))\n");
        } else if (format == Format.TI_HUB_MB_PY) {
            py.append("  send_microbit('display.show(Image(\"'+img+'\"))')\n");
        }
        py.append('\n');

        py.append("#your image data\n");
        py.append('#').append(px.width()).append('x').append(px.height())
          .append(" 10-shades of gray pixels\n");
        py.append("image = \"");
        for (int y = 0; y < px.height(); y++) {
            for (int x = 0; x < px.width(); x++) {
                int gray = px.simplified(y * px.width() + x, 8, 8, 8, 1)[1];
                // Inverted: the LEDs light up where the image is dark.
                py.append(9 - Math.round(gray * 9 / 255.0));
            }
            if (y < px.height() - 1) {
                py.append(':');
            }
        }
        py.append("\"\n\n");
        py.append("#image drawing code sample\n");
        py.append("draw_mb_image(image)\n");
        return py.toString();
    }

    // --- TI-Innovator RGB array ---------------------------------------------------------------

    /** A strip of addressable RGB LEDs: four bytes per lit pixel, address then colour. */
    private String rgbArrayScript(BufferedImage image, Target target) {
        PixelBuffer px = PixelBuffer.of(image);

        StringBuilder im = new StringBuilder();
        for (int y = 0; y < px.height(); y++) {
            for (int x = 0; x < px.width(); x++) {
                int j = y * px.width() + x;
                int[] color = px.simplified(j, 8, 8, 8, 1);
                if (color[3] > 0) {
                    // The strip snakes in groups of eight, so the address is the index with its
                    // low three bits flipped.
                    im.append((char) (j ^ 7))
                      .append((char) color[0]).append((char) color[1]).append((char) color[2]);
                }
            }
        }

        StringBuilder py = new StringBuilder(CREDIT);
        py.append(target == Target.TI_8X_PYTHON
            ? "from rgb_arr import rgb_array\n\n"
            : "from ti_hub import rgb_array\n\n");
        py.append("def draw_rgbarr_image(rgbarr, img):\n");
        py.append("  for i in range(len(img) // 4):\n");
        py.append("    rgbarr.set(img[i * 4], img[i*4 + 1], img[i*4 + 2], img[i*4 + 3])\n");
        py.append('\n');
        py.append("#your image data\n");
        py.append('#').append(px.width()).append('x').append(px.height()).append(" RGB-888 pixels\n");
        py.append("image = ")
          .append(EncoderUtils.stringToPythonBytes(im, 0, target != Target.TI_8X_ONLINE))
          .append("\n\n");
        py.append("#image drawing code sample\n");
        py.append("rgbarr = rgb_array()\n");
        py.append("draw_rgbarr_image(rgbarr, image)\n");
        return py.toString();
    }
}
