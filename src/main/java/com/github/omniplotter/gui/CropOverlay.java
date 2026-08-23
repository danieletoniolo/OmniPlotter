package com.github.omniplotter.gui;

import com.github.omniplotter.engine.data.Crop;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * The crop rectangle, drawn over the source preview.
 *
 * <p>The rectangle it edits is kept in source pixels rather than in the coordinates it is drawn in.
 * The preview is scaled to whatever width the window has left over, so screen coordinates are not
 * a place to keep anything: resizing the window would move the crop.
 *
 * <p>It is mouse-transparent while switched off. The window accepts images dropped anywhere on it,
 * and a pane sitting over the source card would otherwise swallow the drop that lands on the very
 * thing it is drawing on.
 */
public class CropOverlay extends Pane {

    /** How close to a corner counts as grabbing it, in screen pixels. */
    private static final double HANDLE = 12;

    /** Below this the rectangle stops being something you can see or grab. */
    private static final int MINIMUM = 4;

    private enum Grab { NONE, MOVE, NW, NE, SW, SE, DRAW }

    private final ImageView view;

    /** The four bands around the selection: top, bottom, left, right. */
    private final Rectangle[] shade = {new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle()};
    private final Rectangle border = new Rectangle();
    private final Rectangle[] handles = {new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle()};

    private Runnable onChange = () -> {};
    private int imageWidth;
    private int imageHeight;
    private Crop crop;

    /** Width over height the rectangle is locked to, or zero when it is free. */
    private double aspect;

    /**
     * Whether the rectangle can be edited, as opposed to merely seen.
     *
     * <p>The two are separate. A cropped image shows what is being discarded whichever image is
     * selected, because otherwise the crop is invisible and the preview looks wrong for no stated
     * reason. Editing is a tool the user switches on, and never switches itself on.
     */
    private boolean editing;

    private Grab grab = Grab.NONE;
    private double grabX;
    private double grabY;
    private Crop grabbed;

    public CropOverlay(ImageView view) {
        this.view = view;
        setMouseTransparent(true);
        getStyleClass().add("crop-overlay");

        for (Rectangle band : shade) {
            band.setFill(Color.rgb(0, 0, 0, 0.45));
            band.setMouseTransparent(true);
        }
        border.getStyleClass().add("crop-border");
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.WHITE);
        border.setStrokeWidth(1.5);
        border.setMouseTransparent(true);
        for (Rectangle handle : handles) {
            handle.getStyleClass().add("crop-handle");
            handle.setWidth(HANDLE);
            handle.setHeight(HANDLE);
            handle.setFill(Color.WHITE);
            handle.setStroke(Color.rgb(0, 0, 0, 0.5));
            handle.setMouseTransparent(true);
        }
        getChildren().addAll(shade);
        getChildren().add(border);
        getChildren().addAll(handles);

        setOnMousePressed(e -> {
            grabbed = crop;
            grabX = e.getX();
            grabY = e.getY();
            grab = grabAt(e.getX(), e.getY());
            if (grab == Grab.DRAW) {
                grabbed = null;
            }
            e.consume();
        });
        setOnMouseDragged(e -> {
            drag(e.getX(), e.getY());
            e.consume();
        });
        setOnMouseReleased(e -> {
            grab = Grab.NONE;
            // Only now: re-running the conversion for every pixel of a drag would make the
            // rectangle lag behind the pointer on any image big enough to want cropping.
            onChange.run();
            e.consume();
        });
        setOnMouseMoved(e -> setCursor(cursorFor(grabAt(e.getX(), e.getY()))));
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Switches editing on. While off the pane is invisible and lets every event through.
     *
     * <p>Switching on draws nothing. It used to start with a rectangle around the whole image,
     * which looked harmless — a rectangle containing everything crops nothing, so the preview did
     * not move — but it left the image carrying a crop it had never been given. Since having a crop
     * is what makes this tool re-arm when that image comes back, merely opening the tool once was
     * enough to make it follow you around the queue.
     */
    public void setActive(boolean active) {
        this.editing = active;
        setMouseTransparent(!active);
        redraw();
    }

    public boolean isActive() {
        return editing;
    }

    /** The image now being shown, in its own pixels. Resets a rectangle that cannot apply to it. */
    public void setImage(int width, int height, Crop existing) {
        this.imageWidth = width;
        this.imageHeight = height;
        this.crop = existing == null || width <= 0 ? null : existing.clampedTo(width, height);
        redraw();
    }

    /** Locks the rectangle to the canvas proportions, which is what filling needs it to be. */
    public void setAspect(double aspect) {
        this.aspect = aspect;
        if (crop != null && aspect > 0) {
            crop = constrain(crop);
            redraw();
        }
    }

    public Crop crop() {
        return crop;
    }

    public void clear() {
        crop = null;
        redraw();
        onChange.run();
    }

    // --- interaction ------------------------------------------------------------------------

    private Grab grabAt(double x, double y) {
        if (crop == null) {
            return Grab.DRAW;
        }
        double left = toScreenX(crop.x());
        double top = toScreenY(crop.y());
        double right = toScreenX(crop.x() + crop.width());
        double bottom = toScreenY(crop.y() + crop.height());

        if (near(x, left) && near(y, top)) {
            return Grab.NW;
        }
        if (near(x, right) && near(y, top)) {
            return Grab.NE;
        }
        if (near(x, left) && near(y, bottom)) {
            return Grab.SW;
        }
        if (near(x, right) && near(y, bottom)) {
            return Grab.SE;
        }
        return x > left && x < right && y > top && y < bottom ? Grab.MOVE : Grab.DRAW;
    }

    private void drag(double x, double y) {
        if (imageWidth <= 0) {
            return;
        }
        switch (grab) {
            case MOVE -> {
                if (grabbed == null) {
                    return;
                }
                int dx = (int) Math.round((x - grabX) / scale());
                int dy = (int) Math.round((y - grabY) / scale());
                int left = clamp(grabbed.x() + dx, 0, imageWidth - grabbed.width());
                int top = clamp(grabbed.y() + dy, 0, imageHeight - grabbed.height());
                crop = new Crop(left, top, grabbed.width(), grabbed.height());
            }
            case NW, NE, SW, SE -> {
                if (grabbed == null) {
                    return;
                }
                int anchorX = grab == Grab.NW || grab == Grab.SW ? grabbed.x() + grabbed.width() : grabbed.x();
                int anchorY = grab == Grab.NW || grab == Grab.NE ? grabbed.y() + grabbed.height() : grabbed.y();
                crop = between(anchorX, anchorY, toImageX(x), toImageY(y));
            }
            case DRAW -> crop = between(toImageX(grabX), toImageY(grabY), toImageX(x), toImageY(y));
            default -> {
                return;
            }
        }
        redraw();
    }

    /** The rectangle spanned by two corners, squared up to the aspect and kept inside the image. */
    private Crop between(int x1, int y1, int x2, int y2) {
        int left = clamp(Math.min(x1, x2), 0, imageWidth);
        int top = clamp(Math.min(y1, y2), 0, imageHeight);
        int width = Math.max(MINIMUM, clamp(Math.max(x1, x2), 0, imageWidth) - left);
        int height = Math.max(MINIMUM, clamp(Math.max(y1, y2), 0, imageHeight) - top);
        return constrain(new Crop(left, top, Math.min(width, imageWidth - left),
            Math.min(height, imageHeight - top)));
    }

    /**
     * Trims a rectangle to the locked proportions.
     *
     * <p>Always trims, never grows: growing could push it outside the image, and then the correction
     * for that would fight with this one on every drag.
     */
    private Crop constrain(Crop rectangle) {
        if (aspect <= 0) {
            return rectangle;
        }
        int width = rectangle.width();
        int height = rectangle.height();
        if ((double) width / height > aspect) {
            width = Math.max(MINIMUM, (int) Math.round(height * aspect));
        } else {
            height = Math.max(MINIMUM, (int) Math.round(width / aspect));
        }
        int left = Math.min(rectangle.x(), Math.max(0, imageWidth - width));
        int top = Math.min(rectangle.y(), Math.max(0, imageHeight - height));
        return new Crop(left, top, Math.min(width, imageWidth - left), Math.min(height, imageHeight - top));
    }

    private static Cursor cursorFor(Grab grab) {
        return switch (grab) {
            case NW -> Cursor.NW_RESIZE;
            case NE -> Cursor.NE_RESIZE;
            case SW -> Cursor.SW_RESIZE;
            case SE -> Cursor.SE_RESIZE;
            case MOVE -> Cursor.MOVE;
            default -> Cursor.CROSSHAIR;
        };
    }

    // --- geometry ---------------------------------------------------------------------------

    /**
     * Where the image actually sits inside the box it was given.
     *
     * <p>{@code ImageView} preserves the aspect ratio, so a picture the wrong shape for the card is
     * centred with a gap on two sides, and none of the coordinates on screen mean anything until
     * that gap is accounted for. Pulled out as a value with no JavaFX in it because it is the piece
     * most likely to be subtly wrong, and this way it can be checked without a running toolkit.
     *
     * @param scale screen pixels per image pixel
     */
    record Fit(double scale, double offsetX, double offsetY) {

        static Fit of(double boxWidth, double boxHeight, int imageWidth, int imageHeight) {
            if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0 || boxHeight <= 0) {
                return new Fit(1, 0, 0);
            }
            double scale = Math.min(boxWidth / imageWidth, boxHeight / imageHeight);
            return new Fit(scale, (boxWidth - imageWidth * scale) / 2,
                (boxHeight - imageHeight * scale) / 2);
        }

        double toScreenX(double imageX) {
            return offsetX + imageX * scale;
        }

        double toScreenY(double imageY) {
            return offsetY + imageY * scale;
        }

        int toImageX(double screenX) {
            return (int) Math.round((screenX - offsetX) / scale);
        }

        int toImageY(double screenY) {
            return (int) Math.round((screenY - offsetY) / scale);
        }
    }

    /** Recomputed rather than remembered: resizing the window changes it. */
    private Fit fit() {
        return Fit.of(getWidth(), getHeight(), imageWidth, imageHeight);
    }

    private double scale() {
        return fit().scale();
    }

    private double offsetX() {
        return fit().offsetX();
    }

    private double offsetY() {
        return fit().offsetY();
    }

    private double toScreenX(int imageX) {
        return fit().toScreenX(imageX);
    }

    private double toScreenY(int imageY) {
        return fit().toScreenY(imageY);
    }

    private int toImageX(double screenX) {
        return fit().toImageX(screenX);
    }

    private int toImageY(double screenY) {
        return fit().toImageY(screenY);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= HANDLE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void layoutChildren() {
        redraw();
    }

    private static void band(Rectangle rectangle, double x, double y, double width, double height) {
        rectangle.setX(x);
        rectangle.setY(y);
        rectangle.setWidth(Math.max(0, width));
        rectangle.setHeight(Math.max(0, height));
    }

    private void redraw() {
        boolean drawable = crop != null && imageWidth > 0 && getWidth() > 0;
        for (Rectangle band : shade) {
            band.setVisible(drawable);
        }
        border.setVisible(drawable);
        // Handles only while editing: they are the part that invites a drag, and inviting one when
        // the tool is off would promise something the pane will not accept.
        for (Rectangle handle : handles) {
            handle.setVisible(drawable && editing);
        }
        if (!drawable) {
            return;
        }

        double left = toScreenX(crop.x());
        double top = toScreenY(crop.y());
        double width = crop.width() * scale();
        double height = crop.height() * scale();

        border.setX(left);
        border.setY(top);
        border.setWidth(width);
        border.setHeight(height);

        // Four bands rather than one shape with a hole in it: subtracting shapes allocates a new
        // node on every frame of a drag, and the part being kept is the part left undimmed either
        // way.
        double imageLeft = offsetX();
        double imageTop = offsetY();
        double imageRight = imageLeft + imageWidth * scale();
        double imageBottom = imageTop + imageHeight * scale();
        band(shade[0], imageLeft, imageTop, imageRight - imageLeft, top - imageTop);
        band(shade[1], imageLeft, top + height, imageRight - imageLeft, imageBottom - (top + height));
        band(shade[2], imageLeft, top, left - imageLeft, height);
        band(shade[3], left + width, top, imageRight - (left + width), height);

        double[][] corners = {{left, top}, {left + width, top}, {left, top + height},
            {left + width, top + height}};
        for (int i = 0; i < handles.length; i++) {
            handles[i].setX(corners[i][0] - HANDLE / 2);
            handles[i].setY(corners[i][1] - HANDLE / 2);
        }
    }
}
