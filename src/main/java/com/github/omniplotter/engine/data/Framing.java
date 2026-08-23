package com.github.omniplotter.engine.data;

/**
 * How the source is fitted to the canvas before anything else happens.
 *
 * <p>Separate from {@code keepRatio}, which stays exactly what the reference means by it. That flag
 * chooses between fitting the whole picture inside the canvas and stretching it; this chooses what
 * the picture is in the first place. By the time the resize looks at the image, a filled or cropped
 * source already has the right proportions, and the reference's own logic does the rest unchanged.
 *
 * @param fill throw away what does not fit instead of padding the canvas with white
 * @param crop an explicit rectangle, which wins over {@code fill}; {@code null} for the whole image
 */
public record Framing(boolean fill, Crop crop) {

    /** The reference's behaviour: the whole image, letterboxed if it does not fit. */
    public static final Framing DEFAULT = new Framing(false, null);

    public Framing withFill(boolean fill) {
        return new Framing(fill, crop);
    }

    public Framing withCrop(Crop crop) {
        return new Framing(fill, crop);
    }

    /**
     * The rectangle to take from an image of this size, or {@code null} to take all of it.
     *
     * <p>The one place the decision lives, so the pipeline and the window's overlay cannot disagree
     * about which pixels are being converted.
     */
    public Crop rectangleFor(int imageWidth, int imageHeight, int canvasWidth, int canvasHeight) {
        if (crop != null) {
            Crop confined = crop.clampedTo(imageWidth, imageHeight);
            return confined == null || confined.isWhole(imageWidth, imageHeight) ? null : confined;
        }
        if (!fill) {
            return null;
        }
        Crop covering = Crop.cover(imageWidth, imageHeight, canvasWidth, canvasHeight);
        return covering.isWhole(imageWidth, imageHeight) ? null : covering;
    }
}
