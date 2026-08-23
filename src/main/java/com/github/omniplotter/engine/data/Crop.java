package com.github.omniplotter.engine.data;

/**
 * A rectangle of the source image, in source pixels.
 *
 * <p>Source pixels and not fractions: the window draws the rectangle over a scaled preview, and
 * keeping the authoritative copy in the coordinate system of the actual image means the value does
 * not drift as the window is resized.
 */
public record Crop(int x, int y, int width, int height) {

    public Crop {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A crop needs a positive size, got " + width + "x" + height);
        }
    }

    /**
     * The largest centred rectangle of {@code image} that has the canvas's proportions.
     *
     * <p>This is what "fill" means: rather than fitting the whole picture inside the canvas and
     * padding the rest with white, throw away the part that does not fit. For a portrait photograph
     * on a 384×192 screen that is the difference between a stripe of image between two white bands
     * and a picture.
     */
    public static Crop cover(int imageWidth, int imageHeight, int canvasWidth, int canvasHeight) {
        // Comparing the two aspect ratios as a cross-multiplication keeps it in integers.
        boolean tooWide = (long) imageWidth * canvasHeight > (long) canvasWidth * imageHeight;
        int width = tooWide ? Math.max(1, imageHeight * canvasWidth / canvasHeight) : imageWidth;
        int height = tooWide ? imageHeight : Math.max(1, imageWidth * canvasHeight / canvasWidth);
        return new Crop((imageWidth - width) / 2, (imageHeight - height) / 2, width, height);
    }

    /**
     * This rectangle confined to an image of the given size, or {@code null} if it falls entirely
     * outside it.
     *
     * <p>A stored rectangle outlives the image it was drawn on — the queue keeps one per job and
     * the settings survive a restart — so every use of one has to survive being handed a smaller
     * picture than it was made for.
     */
    public Crop clampedTo(int imageWidth, int imageHeight) {
        int left = Math.max(0, Math.min(x, imageWidth));
        int top = Math.max(0, Math.min(y, imageHeight));
        int right = Math.min(imageWidth, x + width);
        int bottom = Math.min(imageHeight, y + height);
        if (right <= left || bottom <= top) {
            return null;
        }
        return new Crop(left, top, right - left, bottom - top);
    }

    /** Whether this covers the whole of an image that size, in which case cropping is a no-op. */
    public boolean isWhole(int imageWidth, int imageHeight) {
        return x <= 0 && y <= 0 && width >= imageWidth && height >= imageHeight;
    }

    /** Reads {@code x,y,width,height}, the spelling the command line accepts. */
    public static Crop parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("A crop is x,y,width,height — got: " + value);
        }
        try {
            return new Crop(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A crop is four whole numbers — got: " + value);
        }
    }

    @Override
    public String toString() {
        return x + "," + y + "," + width + "," + height;
    }
}
