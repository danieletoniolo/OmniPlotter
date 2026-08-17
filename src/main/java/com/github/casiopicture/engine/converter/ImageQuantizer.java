package com.github.casiopicture.engine.converter;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.Random;

/**
 * A small k-means based quantizer used as a fallback replacement for the original ImageQuantizer implementation.
 * This is intentionally simple and aims to provide reasonable results for small palettes.
 */
public class ImageQuantizer {

    public ImageQuantizer() {}

    public static BufferedImage quantize(BufferedImage source, int colors) {
        if (colors <= 0) return source;
        int maxColors = Math.min(colors, 256);

        int w = source.getWidth();
        int h = source.getHeight();
        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);

        // Build initial centroids by sampling
        int k = maxColors;
        int[][] centroids = new int[k][3];
        Random rnd = new Random(12345);
        for (int i = 0; i < k; i++) {
            int rgb = pixels[rnd.nextInt(pixels.length)];
            centroids[i][0] = (rgb >> 16) & 0xFF;
            centroids[i][1] = (rgb >> 8) & 0xFF;
            centroids[i][2] = rgb & 0xFF;
        }

        int[] assignments = new int[pixels.length];

        // Run a few k-means iterations
        int maxIter = 8;
        for (int iter = 0; iter < maxIter; iter++) {
            long[] sumsR = new long[k];
            long[] sumsG = new long[k];
            long[] sumsB = new long[k];
            int[] counts = new int[k];

            // assignment step
            for (int i = 0; i < pixels.length; i++) {
                int rgb = pixels[i];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int best = 0;
                double bestDist = Double.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    double dr = r - centroids[c][0];
                    double dg = g - centroids[c][1];
                    double db = b - centroids[c][2];
                    double d = dr * dr + dg * dg + db * db;
                    if (d < bestDist) {
                        bestDist = d;
                        best = c;
                    }
                }
                assignments[i] = best;
                sumsR[best] += r;
                sumsG[best] += g;
                sumsB[best] += b;
                counts[best]++;
            }

            // update step
            boolean changed = false;
            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) {
                    int nr = (int) (sumsR[c] / counts[c]);
                    int ng = (int) (sumsG[c] / counts[c]);
                    int nb = (int) (sumsB[c] / counts[c]);
                    if (nr != centroids[c][0] || ng != centroids[c][1] || nb != centroids[c][2]) {
                        centroids[c][0] = nr;
                        centroids[c][1] = ng;
                        centroids[c][2] = nb;
                        changed = true;
                    }
                } else {
                    // reinitialize empty centroid
                    int rgb = pixels[rnd.nextInt(pixels.length)];
                    centroids[c][0] = (rgb >> 16) & 0xFF;
                    centroids[c][1] = (rgb >> 8) & 0xFF;
                    centroids[c][2] = rgb & 0xFF;
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // Build palette arrays
        byte[] rArr = new byte[k];
        byte[] gArr = new byte[k];
        byte[] bArr = new byte[k];
        for (int i = 0; i < k; i++) {
            rArr[i] = (byte) centroids[i][0];
            gArr[i] = (byte) centroids[i][1];
            bArr[i] = (byte) centroids[i][2];
        }

        IndexColorModel icm = new IndexColorModel(8, k, rArr, gArr, bArr);
        BufferedImage indexed = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, icm);
        WritableRaster raster = indexed.getRaster();

        // Map pixels to palette indices
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int paletteIndex = assignments[idx];
                raster.setSample(x, y, 0, paletteIndex);
            }
        }

        return indexed;
    }
}
