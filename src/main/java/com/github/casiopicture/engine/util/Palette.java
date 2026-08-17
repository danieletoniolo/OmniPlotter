package com.github.casiopicture.engine.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A fixed colour table loaded from one of the palette images, e.g. {@code palcp.png}.
 *
 * <p>The browser tool draws the palette onto a canvas and reads back every pixel
 * ({@code global_paletteArray}, tmp/index.html:522), so the table is the whole image in row-major
 * order — not just its first row. That distinction is the whole point of this class:
 * {@code palcp.png} is 4x2 (8 colours) and {@code pal8ci.png} is 4x4 (16 colours), and reading only
 * row 0 yields 4 in both cases.
 *
 * <p>Alpha is part of the entry, not decoration: {@code pal8ci.png} holds {@code (0,0,255)} twice,
 * once transparent and once opaque, and they are different palette indices.
 */
public final class Palette {

    private final int[][] colors;

    private Palette(int[][] colors) {
        this.colors = colors;
    }

    /** Loads a palette image from the classpath, in row-major order, alpha included. */
    public static Palette load(String resourceName) throws IOException {
        try (InputStream in = Palette.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Palette resource not found: " + resourceName);
            }
            BufferedImage image = ImageIO.read(in);
            List<int[]> entries = new ArrayList<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    entries.add(new int[]{
                        (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF,
                    });
                }
            }
            return new Palette(entries.toArray(new int[0][]));
        }
    }

    public int size() {
        return colors.length;
    }

    public int[] get(int index) {
        return colors[index];
    }

    /**
     * Index of the closest entry by Manhattan distance over all four channels.
     *
     * <p>Port of {@code nearestiRGB} (tmp/index.html:1775). Two details are load-bearing: the metric
     * is Manhattan and includes alpha (not Euclidean over RGB), and the initial bound of
     * {@code 4*255+1} with a strict {@code <} means the first entry wins any tie.
     *
     * <p>Returns -1 for a {@code null} colour — the case {@link PixelBuffer#raw} produces past the
     * end of an image, where the reference compares against {@code undefined}, every distance is
     * NaN, and no candidate ever beats the bound. Callers fold that -1 into the output byte exactly
     * as the reference does.
     */
    public int nearest(int[] rgba) {
        if (rgba == null) {
            return -1;
        }
        int min = 4 * 255 + 1;
        int imin = -1;
        for (int i = 0; i < colors.length; i++) {
            int[] c = colors[i];
            int v = Math.abs(c[0] - rgba[0]) + Math.abs(c[1] - rgba[1])
                  + Math.abs(c[2] - rgba[2]) + Math.abs(c[3] - rgba[3]);
            if (v < min) {
                imin = i;
                min = v;
            }
        }
        return imin;
    }
}
