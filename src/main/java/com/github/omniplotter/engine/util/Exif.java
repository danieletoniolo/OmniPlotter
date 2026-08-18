package com.github.omniplotter.engine.util;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * The one EXIF tag that changes what an image looks like.
 *
 * <p>A phone does not rotate the sensor data when the phone is turned; it writes the picture the
 * way the sensor saw it and records which way up that was. Every viewer honours the tag, so nobody
 * notices until something reads the pixels directly — at which point a photograph taken in portrait
 * arrives on its side, and the canvas it is fitted into is the wrong shape for it.
 *
 * <p>Only JPEG is parsed. It is the format phones produce, and it is the one whose reader hands
 * back the raw pixels without looking at the tag.
 */
public final class Exif {

    private Exif() {}

    private static final int ORIENTATION_TAG = 0x0112;

    /**
     * The recorded orientation, from 1 to 8.
     *
     * <p>Returns 1 — the value meaning "already the right way up" — for anything that is not a
     * JPEG, carries no EXIF, or is damaged enough that walking it would be guesswork. A missing
     * tag and an unreadable one lead to the same place: leave the pixels alone.
     */
    public static int orientation(byte[] data) {
        if (data == null || data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int at = 2;
        while (at + 4 <= data.length) {
            if ((data[at] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = data[at + 1] & 0xFF;
            // Start of scan: everything after this is entropy-coded pixel data, not segments.
            if (marker == 0xDA || marker == 0xD9) {
                return 1;
            }
            int length = ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
            if (length < 2 || at + 2 + length > data.length) {
                return 1;
            }
            if (marker == 0xE1 && startsWithExif(data, at + 4)) {
                return readOrientation(data, at + 4 + 6);
            }
            at += 2 + length;
        }
        return 1;
    }

    /** Turns the image the right way up, or hands back the one it was given. */
    public static BufferedImage applyOrientation(BufferedImage image, byte[] source) {
        return rotate(image, orientation(source));
    }

    /**
     * The eight orientations as the transforms that undo them.
     *
     * <p>Written as matrices rather than composed from rotations and flips: the composition order
     * of {@link AffineTransform} is the opposite of the order it reads in, and every one of these
     * was easier to verify as {@code x' = m00·x + m01·y + m02}.
     */
    public static BufferedImage rotate(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };

        // Five and up exchange the axes, so the canvas has to as well.
        boolean swapped = orientation >= 5;
        BufferedImage out = new BufferedImage(swapped ? h : w, swapped ? w : h,
            image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : image.getType());

        Graphics2D g = out.createGraphics();
        g.drawImage(image, transform, null);
        g.dispose();
        return out;
    }

    private static boolean startsWithExif(byte[] data, int at) {
        return at + 6 <= data.length
            && data[at] == 'E' && data[at + 1] == 'x' && data[at + 2] == 'i' && data[at + 3] == 'f'
            && data[at + 4] == 0 && data[at + 5] == 0;
    }

    /** Walks the first IFD of the TIFF structure that starts at {@code tiff}. */
    private static int readOrientation(byte[] data, int tiff) {
        if (tiff + 8 > data.length) {
            return 1;
        }
        boolean bigEndian = data[tiff] == 'M' && data[tiff + 1] == 'M';
        if (!bigEndian && !(data[tiff] == 'I' && data[tiff + 1] == 'I')) {
            return 1;
        }

        int ifd = tiff + (int) readNumber(data, tiff + 4, 4, bigEndian);
        if (ifd + 2 > data.length || ifd < tiff) {
            return 1;
        }
        int entries = (int) readNumber(data, ifd, 2, bigEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > data.length) {
                return 1;
            }
            if ((int) readNumber(data, entry, 2, bigEndian) == ORIENTATION_TAG) {
                // A SHORT sits in the first two bytes of the four-byte value field, whichever end
                // of it that is.
                int value = (int) readNumber(data, entry + 8, 2, bigEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static long readNumber(byte[] data, int at, int bytes, boolean bigEndian) {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            int b = data[at + (bigEndian ? i : bytes - 1 - i)] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }
}
