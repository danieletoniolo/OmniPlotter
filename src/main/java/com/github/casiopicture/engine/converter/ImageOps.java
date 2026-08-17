package com.github.casiopicture.engine.converter;

import com.github.casiopicture.engine.util.Palette;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The image operators the reference drives ImageMagick with, reimplemented in Java.
 *
 * <p>img2calc builds a {@code convert} command line per format (tmp/index.html:597-636) and runs it
 * through a WebAssembly ImageMagick. Rather than ship that 4 MB binary, each operator it uses is
 * implemented here with matching semantics. Behaviour was checked against a real ImageMagick 7
 * rather than assumed — several operators do not do the obvious thing:
 *
 * <ul>
 *   <li>{@code -depth 5} <em>rescales</em> to the nearest of 32 evenly spread levels
 *       (0, 33, 74, 107, ...), it does not mask off low bits (0, 32, 72, 104, ...).</li>
 *   <li>{@code -extent} without an explicit gravity anchors at the top-left, not the centre.</li>
 *   <li>{@code -gravity NorthEast -splice 1x0} adds its column at the <em>right</em> edge.</li>
 *   <li>{@code -colorspace Gray} applies Rec.709 luma to the encoded values directly, with no
 *       linear-light round trip: pure red becomes 54, not 127.</li>
 * </ul>
 *
 * <p>Every image is handled as non-premultiplied ARGB, since alpha is meaningful throughout: it
 * carries the ink for the monochrome formats and marks skipped pixels for zpic.
 */
public final class ImageOps {

    private ImageOps() {}

    /** Converts to non-premultiplied ARGB, the working format for every operator here. */
    public static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** True when any pixel is not fully opaque — the reference's {@code window.transparent}. */
    public static boolean hasTransparency(BufferedImage src) {
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                if ((src.getRGB(x, y) >>> 24) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- geometry ---------------------------------------------------------------------------

    /**
     * Resamples to fit a target box.
     *
     * @param exact      ignore the source aspect ratio and stretch to exactly {@code w x h} — the
     *                   {@code !} suffix, used when "keep image ratio" is off
     * @param shrinkOnly leave an image that already fits alone — the {@code >} suffix, used when
     *                   "enlarge smaller" is off
     */
    public static BufferedImage resize(BufferedImage src, int w, int h, boolean exact, boolean shrinkOnly) {
        int sw = src.getWidth();
        int sh = src.getHeight();

        if (shrinkOnly && sw <= w && sh <= h) {
            return src;
        }

        int tw;
        int th;
        if (exact) {
            tw = w;
            th = h;
        } else {
            double scale = Math.min((double) w / sw, (double) h / sh);
            tw = Math.max(1, (int) Math.round(sw * scale));
            th = Math.max(1, (int) Math.round(sh * scale));
        }
        if (tw == sw && th == sh) {
            return src;
        }
        return lanczos(src, tw, th);
    }

    private static final double LANCZOS_SUPPORT = 3.0;

    private static double lanczosWeight(double x) {
        x = Math.abs(x);
        if (x < 1e-9) {
            return 1.0;
        }
        if (x >= LANCZOS_SUPPORT) {
            return 0.0;
        }
        double pix = Math.PI * x;
        return (Math.sin(pix) / pix) * (Math.sin(pix / LANCZOS_SUPPORT) / (pix / LANCZOS_SUPPORT));
    }

    /**
     * Separable Lanczos-3 resampling, ImageMagick's default {@code -resize} filter.
     *
     * <p>Colour is weighted by alpha so that transparent pixels do not bleed their RGB into their
     * neighbours, then un-weighted afterwards.
     */
    private static BufferedImage lanczos(BufferedImage src, int tw, int th) {
        int sw = src.getWidth();
        int sh = src.getHeight();

        float[] r = new float[sw * sh];
        float[] g = new float[sw * sh];
        float[] b = new float[sw * sh];
        float[] a = new float[sw * sh];
        for (int y = 0, i = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++, i++) {
                int p = src.getRGB(x, y);
                float alpha = ((p >>> 24) & 0xFF) / 255f;
                a[i] = alpha;
                r[i] = ((p >> 16) & 0xFF) * alpha;
                g[i] = ((p >> 8) & 0xFF) * alpha;
                b[i] = (p & 0xFF) * alpha;
            }
        }

        float[] hr = new float[tw * sh];
        float[] hg = new float[tw * sh];
        float[] hb = new float[tw * sh];
        float[] ha = new float[tw * sh];
        resampleAxis(r, g, b, a, sw, sh, hr, hg, hb, ha, tw, true);

        float[] vr = new float[tw * th];
        float[] vg = new float[tw * th];
        float[] vb = new float[tw * th];
        float[] va = new float[tw * th];
        resampleAxis(hr, hg, hb, ha, tw, sh, vr, vg, vb, va, th, false);

        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0, i = 0; y < th; y++) {
            for (int x = 0; x < tw; x++, i++) {
                float alpha = clamp01(va[i]);
                int ai = Math.round(alpha * 255);
                int ri;
                int gi;
                int bi;
                if (ai == 0) {
                    ri = gi = bi = 0;
                } else {
                    ri = clampByte(Math.round(vr[i] / alpha));
                    gi = clampByte(Math.round(vg[i] / alpha));
                    bi = clampByte(Math.round(vb[i] / alpha));
                }
                out.setRGB(x, y, (ai << 24) | (ri << 16) | (gi << 8) | bi);
            }
        }
        return out;
    }

    /** One pass of the separable filter; {@code horizontal} picks which axis is being scaled. */
    private static void resampleAxis(float[] sr, float[] sg, float[] sb, float[] sa,
                                     int sw, int sh,
                                     float[] dr, float[] dg, float[] db, float[] da,
                                     int target, boolean horizontal) {
        int source = horizontal ? sw : sh;
        int other = horizontal ? sh : sw;
        double scale = (double) target / source;
        // Downscaling widens the filter footprint so the whole source range is averaged in.
        double filterScale = scale < 1.0 ? 1.0 / scale : 1.0;
        double support = LANCZOS_SUPPORT * filterScale;

        for (int t = 0; t < target; t++) {
            double center = (t + 0.5) / scale;
            int from = Math.max(0, (int) Math.floor(center - support));
            int to = Math.min(source - 1, (int) Math.ceil(center + support));

            int n = to - from + 1;
            double[] weights = new double[n];
            double total = 0;
            for (int s = from; s <= to; s++) {
                double weight = lanczosWeight((s + 0.5 - center) / filterScale);
                weights[s - from] = weight;
                total += weight;
            }
            if (total == 0) {
                weights[Math.min(n - 1, Math.max(0, (int) center - from))] = 1;
                total = 1;
            }

            for (int o = 0; o < other; o++) {
                double ar = 0;
                double ag = 0;
                double ab = 0;
                double aa = 0;
                for (int s = from; s <= to; s++) {
                    int idx = horizontal ? o * sw + s : s * sw + o;
                    double weight = weights[s - from];
                    ar += sr[idx] * weight;
                    ag += sg[idx] * weight;
                    ab += sb[idx] * weight;
                    aa += sa[idx] * weight;
                }
                int out = horizontal ? o * target + t : t * (sw) + o;
                dr[out] = (float) (ar / total);
                dg[out] = (float) (ag / total);
                db[out] = (float) (ab / total);
                da[out] = (float) (aa / total);
            }
        }
    }

    /** {@code -extent}: pad or crop to an exact canvas, anchored at the top-left. */
    public static BufferedImage extent(BufferedImage src, int w, int h, Color background) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int fill = background == null ? 0 : background.getRGB();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, x < src.getWidth() && y < src.getHeight() ? src.getRGB(x, y) : fill);
            }
        }
        return out;
    }

    /**
     * {@code -gravity NorthEast -splice 1x0}: inserts one background column at the right edge.
     *
     * <p>The TI variable formats resize to one pixel narrower than the canvas and then splice this
     * column back on, so the rightmost column is always blank.
     */
    public static BufferedImage spliceColumnRight(BufferedImage src, Color background) {
        return extent(src, src.getWidth() + 1, src.getHeight(), background);
    }

    // --- colour -----------------------------------------------------------------------------

    /** {@code -background <c> -flatten}: composites over an opaque background. */
    public static BufferedImage flatten(BufferedImage src, Color background) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int br = background.getRed();
        int bg = background.getGreen();
        int bb = background.getBlue();
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int alpha = (p >>> 24) & 0xFF;
                int r = ((p >> 16) & 0xFF) * alpha / 255 + br * (255 - alpha) / 255;
                int g = ((p >> 8) & 0xFF) * alpha / 255 + bg * (255 - alpha) / 255;
                int b = (p & 0xFF) * alpha / 255 + bb * (255 - alpha) / 255;
                out.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /**
     * {@code -channel <c> -depth <n>}: reduces each channel to {@code 2^n} evenly spread levels.
     *
     * <p>A rescale, not a truncation. With 5 bits, 36 becomes 33 rather than 32.
     */
    public static BufferedImage channelDepth(BufferedImage src, int rBits, int gBits, int bBits, int aBits) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int a = depth((p >>> 24) & 0xFF, aBits);
                int r = depth((p >> 16) & 0xFF, rBits);
                int g = depth((p >> 8) & 0xFF, gBits);
                int b = depth(p & 0xFF, bBits);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static int depth(int value, int bits) {
        if (bits >= 8) {
            return value;
        }
        int levels = (1 << bits) - 1;
        return (int) Math.round(Math.round(value * levels / 255.0) * 255.0 / levels);
    }

    /** {@code -colorspace Gray}: Rec.709 luma over the encoded values. */
    public static BufferedImage grayscale(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int luma = clampByte((int) Math.round(
                    0.212656 * ((p >> 16) & 0xFF)
                  + 0.715158 * ((p >> 8) & 0xFF)
                  + 0.072186 * (p & 0xFF)));
                out.setRGB(x, y, (p & 0xFF000000) | (luma << 16) | (luma << 8) | luma);
            }
        }
        return out;
    }

    /** {@code -auto-level}: linearly stretches the used range to fill 0..255. */
    public static BufferedImage autoLevel(BufferedImage src) {
        int min = 255;
        int max = 0;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                for (int shift : new int[]{16, 8, 0}) {
                    int v = (p >> shift) & 0xFF;
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
        }
        if (min >= max) {
            return src;
        }

        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        double span = max - min;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int r = clampByte((int) Math.round((((p >> 16) & 0xFF) - min) * 255 / span));
                int g = clampByte((int) Math.round((((p >> 8) & 0xFF) - min) * 255 / span));
                int b = clampByte((int) Math.round(((p & 0xFF) - min) * 255 / span));
                out.setRGB(x, y, (p & 0xFF000000) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /**
     * {@code -posterize <n>}: snaps each channel to {@code n} evenly spread levels.
     *
     * @param dither diffuse the rounding error into neighbouring pixels. ImageMagick does this by
     *               default; the reference turns it off with {@code +dither} for some formats and
     *               leaves it on for others, which visibly changes the result at low level counts.
     */
    public static BufferedImage posterize(BufferedImage src, int levels, boolean dither) {
        if (levels < 2) {
            return src;
        }
        int steps = levels - 1;
        return map(src, dither, c -> new int[]{
            quantizeLevel(c[0], steps), quantizeLevel(c[1], steps), quantizeLevel(c[2], steps), c[3],
        });
    }

    private static int quantizeLevel(int value, int steps) {
        return (int) Math.round(Math.round(value * steps / 255.0) * 255.0 / steps);
    }

    /**
     * Applies a colour mapping across an image, optionally diffusing the error it introduces.
     *
     * <p>Without dithering this is a plain per-pixel substitution. With it, Floyd-Steinberg carries
     * each pixel's rounding error into its not-yet-visited neighbours, so a smooth gradient reduced
     * to a handful of colours keeps its shading as a fine pattern instead of collapsing into bands.
     * That is the difference between a recognisable photo and a silhouette on the 1-bit formats.
     *
     * <p>ImageMagick's own default is Riemersma diffusion along a Hilbert curve. The visual
     * character is the same; the exact pattern is not, so the parity test compares these formats by
     * local average rather than pixel by pixel.
     */
    private static BufferedImage map(BufferedImage src, boolean dither, java.util.function.UnaryOperator<int[]> mapper) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        if (!dither) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = src.getRGB(x, y);
                    int[] mapped = mapper.apply(new int[]{
                        (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, (p >>> 24) & 0xFF});
                    out.setRGB(x, y, (clampByte(mapped[3]) << 24) | (clampByte(mapped[0]) << 16)
                        | (clampByte(mapped[1]) << 8) | clampByte(mapped[2]));
                }
            }
            return out;
        }

        // Working copy carrying accumulated error, so it can go outside 0..255 between steps.
        float[][] buf = new float[w * h][];
        for (int y = 0, i = 0; y < h; y++) {
            for (int x = 0; x < w; x++, i++) {
                int p = src.getRGB(x, y);
                buf[i] = new float[]{(p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, (p >>> 24) & 0xFF};
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float[] current = buf[i];
                int[] mapped = mapper.apply(new int[]{
                    clampByte(Math.round(current[0])), clampByte(Math.round(current[1])),
                    clampByte(Math.round(current[2])), clampByte(Math.round(current[3]))});
                out.setRGB(x, y, (clampByte(mapped[3]) << 24) | (clampByte(mapped[0]) << 16)
                    | (clampByte(mapped[1]) << 8) | clampByte(mapped[2]));

                float[] error = new float[4];
                for (int c = 0; c < 4; c++) {
                    error[c] = current[c] - mapped[c];
                }
                spread(buf, w, h, x + 1, y, error, 7 / 16f);
                spread(buf, w, h, x - 1, y + 1, error, 3 / 16f);
                spread(buf, w, h, x, y + 1, error, 5 / 16f);
                spread(buf, w, h, x + 1, y + 1, error, 1 / 16f);
            }
        }
        return out;
    }

    private static void spread(float[][] buf, int w, int h, int x, int y, float[] error, float weight) {
        if (x < 0 || x >= w || y < 0 || y >= h) {
            return;
        }
        float[] target = buf[y * w + x];
        for (int c = 0; c < 4; c++) {
            target[c] += error[c] * weight;
        }
    }

    /**
     * {@code -colorspace RGB}: converts sRGB-encoded values to linear light.
     *
     * <p>ImageMagick's "RGB" means linear RGB, so this darkens midtones considerably — 128 becomes
     * 55. The micro:bit pipeline runs it before negating, and the brightness digits the encoder
     * emits come out of the result.
     */
    public static BufferedImage toLinearRgb(BufferedImage src) {
        int[] lut = new int[256];
        for (int v = 0; v < 256; v++) {
            double c = v / 255.0;
            double linear = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
            lut[v] = clampByte((int) Math.round(linear * 255));
        }

        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p & 0xFF000000)
                    | (lut[(p >> 16) & 0xFF] << 16) | (lut[(p >> 8) & 0xFF] << 8) | lut[p & 0xFF]);
            }
        }
        return out;
    }

    /**
     * {@code -negate} while {@code -channel R} is still in effect: inverts red only.
     *
     * <p>{@code -channel} is a setting, not a one-shot modifier, so it keeps applying to every
     * later operator. The micro:bit chain sets red then negates, and green and blue come through
     * untouched — which matters, because the encoder reads brightness off green.
     */
    public static BufferedImage negateRed(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p & 0xFF00FFFF) | ((255 - ((p >> 16) & 0xFF)) << 16));
            }
        }
        return out;
    }

    /** {@code -negate}: inverts the colour channels, leaving alpha alone. */
    public static BufferedImage negate(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p & 0xFF000000) | (~p & 0x00FFFFFF));
            }
        }
        return out;
    }

    /** {@code -channel R -evaluate set 0%}: forces one colour channel to a fixed value. */
    public static BufferedImage setRed(BufferedImage src, int value) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p & 0xFF00FFFF) | (clampByte(value) << 16));
            }
        }
        return out;
    }

    /** {@code -transparent <c>}: clears alpha on pixels matching a colour, keeping their RGB. */
    public static BufferedImage makeTransparent(BufferedImage src, Color color) {
        int match = color.getRGB() & 0x00FFFFFF;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p & 0x00FFFFFF) == match ? p & 0x00FFFFFF : p);
            }
        }
        return out;
    }

    /** {@code -remap <palette>}: snaps every pixel to its nearest palette entry. */
    public static BufferedImage remap(BufferedImage src, Palette palette, boolean dither) {
        return map(src, dither, c -> palette.get(palette.nearest(c)));
    }

    // --- quantisation -------------------------------------------------------------------------

    /**
     * {@code -colors <n> +dither}: reduces to at most {@code n} colours by median cut, no dithering.
     *
     * <p>Splits the colour cube along whichever axis of the most populous box spans furthest, until
     * there are {@code n} boxes, then replaces each box by the mean of the pixels in it. Alpha
     * participates as a fourth axis so that transparent regions are not merged into opaque ones.
     */
    public static BufferedImage quantize(BufferedImage src, int colors, boolean dither) {
        if (colors < 1) {
            return src;
        }

        Map<Integer, Integer> histogram = new HashMap<>();
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                histogram.merge(src.getRGB(x, y), 1, Integer::sum);
            }
        }
        if (histogram.size() <= colors) {
            return src;
        }

        List<Integer> allColors = new ArrayList<>(histogram.keySet());
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(allColors, histogram));

        while (boxes.size() < colors) {
            Box widest = null;
            int at = -1;
            for (int i = 0; i < boxes.size(); i++) {
                Box box = boxes.get(i);
                if (box.colors.size() > 1 && (widest == null || box.range() > widest.range())) {
                    widest = boxes.get(i);
                    at = i;
                }
            }
            if (widest == null) {
                break;   // every box holds a single colour; nothing left to split
            }
            List<Box> split = widest.split(histogram);
            boxes.remove(at);
            boxes.addAll(split);
        }

        Map<Integer, Integer> mapping = new HashMap<>();
        for (Box box : boxes) {
            int mean = box.mean(histogram);
            for (int color : box.colors) {
                mapping.put(color, mean);
            }
        }

        List<int[]> chosen = new ArrayList<>();
        for (Box box : boxes) {
            int mean = box.mean(histogram);
            chosen.add(new int[]{(mean >> 16) & 0xFF, (mean >> 8) & 0xFF, mean & 0xFF, (mean >>> 24) & 0xFF});
        }
        // Dithering moves pixels off their original colours, so the exact histogram mapping no
        // longer applies and the nearest chosen colour is used instead.
        return map(src, dither, c -> dither ? nearest(c, chosen) : unpack(mapping.get(pack(c))));
    }

    private static int pack(int[] c) {
        return (c[3] << 24) | (c[0] << 16) | (c[1] << 8) | c[2];
    }

    private static int[] unpack(int argb) {
        return new int[]{(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF};
    }

    private static int[] nearest(int[] c, List<int[]> candidates) {
        int[] best = candidates.get(0);
        long bestDistance = Long.MAX_VALUE;
        for (int[] candidate : candidates) {
            long d = 0;
            for (int i = 0; i < 4; i++) {
                long delta = candidate[i] - c[i];
                d += delta * delta;
            }
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /** One cell of the median-cut partition, holding the distinct colours that fall inside it. */
    private static final class Box {
        final List<Integer> colors;
        private final int[] min = {255, 255, 255, 255};
        private final int[] max = {0, 0, 0, 0};

        Box(List<Integer> colors, Map<Integer, Integer> histogram) {
            this.colors = colors;
            for (int color : colors) {
                for (int c = 0; c < 4; c++) {
                    int v = component(color, c);
                    min[c] = Math.min(min[c], v);
                    max[c] = Math.max(max[c], v);
                }
            }
        }

        int longestAxis() {
            int axis = 0;
            for (int c = 1; c < 4; c++) {
                if (max[c] - min[c] > max[axis] - min[axis]) {
                    axis = c;
                }
            }
            return axis;
        }

        int range() {
            int axis = longestAxis();
            return max[axis] - min[axis];
        }

        List<Box> split(Map<Integer, Integer> histogram) {
            int axis = longestAxis();
            List<Integer> sorted = new ArrayList<>(colors);
            sorted.sort((a, b) -> Integer.compare(component(a, axis), component(b, axis)));

            // Cut at the weighted median, so both halves carry a similar number of pixels.
            long total = 0;
            for (int color : sorted) {
                total += histogram.get(color);
            }
            long half = total / 2;
            long running = 0;
            int cut = 0;
            for (int i = 0; i < sorted.size() - 1; i++) {
                running += histogram.get(sorted.get(i));
                cut = i + 1;
                if (running >= half) {
                    break;
                }
            }

            return List.of(
                new Box(new ArrayList<>(sorted.subList(0, cut)), histogram),
                new Box(new ArrayList<>(sorted.subList(cut, sorted.size())), histogram));
        }

        int mean(Map<Integer, Integer> histogram) {
            long[] sums = new long[4];
            long weight = 0;
            for (int color : colors) {
                int count = histogram.get(color);
                weight += count;
                for (int c = 0; c < 4; c++) {
                    sums[c] += (long) component(color, c) * count;
                }
            }
            int a = (int) Math.round((double) sums[3] / weight);
            int r = (int) Math.round((double) sums[0] / weight);
            int g = (int) Math.round((double) sums[1] / weight);
            int b = (int) Math.round((double) sums[2] / weight);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int component(int argb, int c) {
            return switch (c) {
                case 0 -> (argb >> 16) & 0xFF;
                case 1 -> (argb >> 8) & 0xFF;
                case 2 -> argb & 0xFF;
                default -> (argb >>> 24) & 0xFF;
            };
        }
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }
}
