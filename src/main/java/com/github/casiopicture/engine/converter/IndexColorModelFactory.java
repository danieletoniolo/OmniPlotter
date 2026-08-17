package com.github.casiopicture.engine.converter;

import java.awt.Color;
import java.awt.image.IndexColorModel;
import java.util.List;

public class IndexColorModelFactory {
    public static IndexColorModel create(List<Color> colors) {
        int size = colors.size();
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];

        for (int i = 0; i < size; i++) {
            Color color = colors.get(i);
            r[i] = (byte) color.getRed();
            g[i] = (byte) color.getGreen();
            b[i] = (byte) color.getBlue();
        }

        return new IndexColorModel(8, size, r, g, b);
    }
}
