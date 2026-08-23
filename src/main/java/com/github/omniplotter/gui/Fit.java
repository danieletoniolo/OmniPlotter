package com.github.omniplotter.gui;

/**
 * Where an image actually sits inside the box it was given.
 *
 * <p>{@code ImageView} preserves the aspect ratio, so a picture the wrong shape for the card is
 * centred with a gap on two sides, and no coordinate on screen means anything until that gap is
 * accounted for. Every overlay drawn over a preview needs the same conversion, and getting it
 * subtly wrong is invisible — the rectangle simply selects the wrong pixels by a sliver.
 *
 * <p>A value with no JavaFX in it, so it can be checked without a running toolkit.
 *
 * @param scale screen pixels per image pixel
 */
public record Fit(double scale, double offsetX, double offsetY) {

    public static Fit of(double boxWidth, double boxHeight, int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0 || boxHeight <= 0) {
            return new Fit(1, 0, 0);
        }
        double scale = Math.min(boxWidth / imageWidth, boxHeight / imageHeight);
        return new Fit(scale, (boxWidth - imageWidth * scale) / 2,
            (boxHeight - imageHeight * scale) / 2);
    }

    public double toScreenX(double imageX) {
        return offsetX + imageX * scale;
    }

    public double toScreenY(double imageY) {
        return offsetY + imageY * scale;
    }

    public int toImageX(double screenX) {
        return (int) Math.round((screenX - offsetX) / scale);
    }

    public int toImageY(double screenY) {
        return (int) Math.round((screenY - offsetY) / scale);
    }
}
