package com.github.omniplotter.engine.converter;

import com.github.omniplotter.engine.util.Palette;

import java.awt.image.IndexColorModel;

public final class IndexColorModelFactory {

    private IndexColorModelFactory() {}

    /**
     * Builds an {@link IndexColorModel} over a palette, alpha included.
     *
     * <p>Alpha is not optional here: {@code pal8ci.png} carries the same RGB twice, once
     * transparent and once opaque, and dropping alpha would collapse two distinct indices into one.
     */
    public static IndexColorModel create(Palette palette) {
        int size = palette.size();
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        byte[] a = new byte[size];

        for (int i = 0; i < size; i++) {
            int[] c = palette.get(i);
            r[i] = (byte) c[0];
            g[i] = (byte) c[1];
            b[i] = (byte) c[2];
            a[i] = (byte) c[3];
        }

        return new IndexColorModel(8, size, r, g, b, a);
    }
}
