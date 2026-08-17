package com.github.casiopicture.engine.encoder;

import com.github.casiopicture.engine.data.ConversionOptions;
import com.github.casiopicture.engine.data.ConversionResult;
import com.github.casiopicture.engine.data.Format;
import com.github.casiopicture.engine.util.EncoderUtils;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PythonEncoder implements FileEncoder {

    /**
     * Private constructor to prevent direct instantiation
     */
    private PythonEncoder() {}

    /**
     * Thread-safe singleton holder pattern
     */
    private static final class Holder {
        static final PythonEncoder INSTANCE = new PythonEncoder();
    }

    /**
     * Get the singleton instance of PythonEncoder
     * @return The {@link PythonEncoder} singleton instance
     */
    public static PythonEncoder getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public ConversionResult encode(BufferedImage image, Format format, String originalFileName, ConversionOptions options) throws IOException {
        String varName = originalFileName.substring(0, originalFileName.lastIndexOf('.')).replaceAll("[^a-zA-Z0-9_]", "_");

        return switch (format) {
            case TI_GRAPHICS_PY, KANDINSKY_PY, CASIO_PICTURE_PY, TI_DRAW_CE_PY, TI_DRAW_CX_PY -> encodePythonRLE(image, format, varName);
            case CASIOPLOT_CG_PY -> encodeCasioPlot(image, varName);
            case GRAPHIC_G3_PY, GRAPHIC_CG_PY -> encodeGraphic(image, "graphic", varName);
            case GINT_G3_PY, GINT_CG_PY -> encodeGraphic(image, "gint", varName);
            case NSP_CX_PY, NSP_NS_PY -> encodeGraphic(image, "nsp", varName);
            case HPPRIME_PY -> encodeGraphic(image, "hpprime", varName);
            case MICROBIT_PY, MICROBIT_SMALL_PY -> encodePythonMB(image, format);
            case TI_HUB_RGBARR_PY -> encodePythonRGBArr(image, varName);
            default -> throw new IllegalArgumentException("Unsupported format for PythonEncoder: " + format);
        };
    }

    /**
     * Extracts palette indices from an image and builds a color palette.
     * @return Object array: [0] = List<Integer> palette, [1] = List<Byte> pixelData
     */
    private Object[] extractPaletteAndPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<Integer> palette = new ArrayList<>();
        List<Byte> pixelData = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(image.getRGB(x, y));
                int color565 = EncoderUtils.packColor565(color);
                int index = palette.indexOf(color565);
                if (index == -1) {
                    index = palette.size();
                    palette.add(color565);
                }
                pixelData.add((byte) index);
            }
        }
        return new Object[]{palette, pixelData};
    }

    @SuppressWarnings("unchecked")
    private ConversionResult encodePythonRLE(BufferedImage image, Format format, String varName) {
        int width = image.getWidth();
        int height = image.getHeight();

        Object[] result = extractPaletteAndPixels(image);
        List<Integer> palette565 = (List<Integer>) result[0];
        List<Byte> pixelData = (List<Byte>) result[1];

        StringBuilder rleHex = EncoderUtils.compressRLE(pixelData);

        StringBuilder sb = new StringBuilder();
        String importStatement = switch (format) {
            case KANDINSKY_PY -> "from kandinsky import *\n";
            case TI_GRAPHICS_PY -> "from ti_graphics import *\n";
            case TI_DRAW_CE_PY, TI_DRAW_CX_PY -> "from ti_draw import *\n";
            default -> "from casio_picture import *\n";
        };
        sb.append(importStatement);

        sb.append(varName).append("_width=").append(width).append("\n");
        sb.append(varName).append("_height=").append(height).append("\n");

        sb.append(varName).append("_palette=b'");
        for (int color565 : palette565) {
            sb.append(String.format("\\x%02x\\x%02x", (color565 >> 8) & 0xFF, color565 & 0xFF));
        }
        sb.append("'\n");

        sb.append(varName).append("_pixels=b'").append(rleHex).append("'\n\n");
        sb.append("img = Picture(data=").append(varName).append("_pixels, palette=").append(varName)
          .append("_palette, width=").append(varName).append("_width, height=").append(varName).append("_height)\n");

        return new ConversionResult(sb.toString().getBytes(), varName + ".py");
    }

    @SuppressWarnings("unchecked")
    private ConversionResult encodeCasioPlot(BufferedImage image, String varName) {
        int width = image.getWidth();
        int height = image.getHeight();

        Object[] result = extractPaletteAndPixels(image);
        List<Integer> palette565 = (List<Integer>) result[0];
        List<Byte> pixelData = (List<Byte>) result[1];

        StringBuilder rleHex = EncoderUtils.compressRLE(pixelData);

        StringBuilder sb = new StringBuilder();
        sb.append("from casioplot import *\n\n");
        sb.append("# ").append(varName).append(" ").append(width).append("x").append(height).append("\n");
        sb.append("palette = [\n");
        for (int color565 : palette565) {
            sb.append("  ").append(color565).append(",\n");
        }
        sb.append("]\n\n");
        sb.append("pixels = b'").append(rleHex).append("'\n\n");
        sb.append("def draw_image():\n");
        sb.append("  set_pixel_h_w(").append(width).append(", ").append(height).append(")\n");
        sb.append("  draw_rle(pixels, palette)\n\n");
        sb.append("draw_image()\n");
        sb.append("show_screen()\n");

        return new ConversionResult(sb.toString().getBytes(), varName + ".py");
    }

    @SuppressWarnings("unchecked")
    private ConversionResult encodeGraphic(BufferedImage image, String module, String varName) {
        int width = image.getWidth();
        int height = image.getHeight();

        Object[] result = extractPaletteAndPixels(image);
        List<Integer> palette = (List<Integer>) result[0];
        List<Byte> pixelData = (List<Byte>) result[1];

        StringBuilder rleHex = EncoderUtils.compressRLE(pixelData);

        StringBuilder sb = new StringBuilder();
        sb.append("from ").append(module).append(" import *\n\n");
        sb.append("# ").append(varName).append(" ").append(width).append("x").append(height).append("\n");
        sb.append("palette = [\n");
        for (int color : palette) {
            sb.append("  ").append(color).append(",\n");
        }
        sb.append("]\n\n");
        sb.append("pixels = b'").append(rleHex).append("'\n\n");
        sb.append("def draw_image():\n");
        sb.append("  # Your drawing logic here, e.g.:\n");
        sb.append("  x, y = 0, 0\n");
        sb.append("  i = 0\n");
        sb.append("  while i < len(pixels):\n");
        sb.append("    run = pixels[i]\n");
        sb.append("    color_idx = pixels[i+1]\n");
        sb.append("    for _ in range(run):\n");
        sb.append("      fill_rect(x, y, 1, 1, palette[color_idx])\n");
        sb.append("      x += 1\n");
        sb.append("      if x >= ").append(width).append(":\n");
        sb.append("        x = 0\n");
        sb.append("        y += 1\n");
        sb.append("    i += 2\n\n");
        sb.append("draw_image()\n");

        return new ConversionResult(sb.toString().getBytes(), varName + ".py");
    }

    private ConversionResult encodePythonMB(BufferedImage image, Format format) {
        StringBuilder sb = new StringBuilder();
        if (format == Format.MICROBIT_PY) {
            sb.append("from microbit import *\n\n");
            sb.append("i = Image('");
        } else { // microbit_small.py
            sb.append("Image.from_string('");
        }

        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                if (y < image.getHeight() && x < image.getWidth()) {
                    Color c = new Color(image.getRGB(x, y));
                    sb.append(c.getRed() < 128 ? "9" : "0");
                } else {
                    sb.append("0");
                }
            }
            if (y < 4) {
                sb.append(":");
            }
        }
        sb.append("')\n");

        if (format == Format.MICROBIT_PY) {
            sb.append("display.show(i)\n");
        }

        return new ConversionResult(sb.toString().getBytes(), "microbit_image.py");
    }

    private ConversionResult encodePythonRGBArr(BufferedImage image, String varName) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(varName).append(" ").append(image.getWidth()).append("x").append(image.getHeight()).append("\n");
        sb.append(varName).append("=[\n");

        for (int y = 0; y < image.getHeight(); y++) {
            sb.append("  [");
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                sb.append("[").append(c.getRed()).append(",").append(c.getGreen()).append(",").append(c.getBlue()).append("]");
                if (x < image.getWidth() - 1) {
                    sb.append(",");
                }
            }
            sb.append("]");
            if (y < image.getHeight() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");

        return new ConversionResult(sb.toString().getBytes(), varName + ".py");
    }
}
