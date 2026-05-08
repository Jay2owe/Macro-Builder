package macro.builder.image.variation;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.Duplicator;

import java.awt.Rectangle;

/**
 * Crops an {@link ImagePlus} to a user-drawn {@link Roi} while preserving the
 * hyperstack layout (C × Z × T) and calibration.
 *
 * <p>This is the spatial half of ROI mode. It runs after the user has drawn a
 * rectangle on the source image in {@link RoiPromptDialog} and before
 * variants are dispatched. {@code ImagePlus.crop("stack")} is unreliable for
 * hyperstacks across ImageJ versions, so we go through {@link Duplicator}
 * with explicit C/Z/T bounds — that path honours the ROI on the source for
 * spatial cropping while keeping channels intact.
 *
 * <p>The source image's ROI is restored before this method returns, so
 * subsequent caller code sees the image unchanged.
 */
public final class RoiCropper {

    private RoiCropper() {}

    /**
     * Returns a new {@link ImagePlus} containing only the rectangle covered by
     * {@code roi}, with the same channel/Z/T layout, calibration, and per-channel
     * display ranges as {@code source}.
     *
     * <p>If {@code roi} is null the source is returned unchanged.
     */
    public static ImagePlus cropToRoi(ImagePlus source, Roi roi) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (roi == null) {
            return source;
        }

        Rectangle bounds = clampToImage(roi.getBounds(),
                source.getWidth(), source.getHeight());
        if (bounds.width <= 0 || bounds.height <= 0) {
            throw new IllegalArgumentException(
                    "ROI bounds are empty for image " + source.getWidth()
                            + "×" + source.getHeight() + ": " + roi.getBounds());
        }

        Roi previousRoi = source.getRoi();
        Roi spatialRoi = new Roi(bounds);
        ImagePlus cropped;
        try {
            source.setRoi(spatialRoi);
            int c = Math.max(1, source.getNChannels());
            int z = Math.max(1, source.getNSlices());
            int t = Math.max(1, source.getNFrames());
            cropped = new Duplicator().run(source, 1, c, 1, z, 1, t);
        } finally {
            if (previousRoi != null) source.setRoi(previousRoi);
            else source.deleteRoi();
        }

        if (source.getCalibration() != null) {
            Calibration cal = source.getCalibration().copy();
            cropped.setCalibration(cal);
        }
        if (source.isHyperStack()) cropped.setOpenAsHyperStack(true);

        copyDisplayRanges(source, cropped);
        return cropped;
    }

    /** Intersect the requested rectangle with the image's pixel grid. */
    private static Rectangle clampToImage(Rectangle in, int w, int h) {
        int x = Math.max(0, in.x);
        int y = Math.max(0, in.y);
        int x2 = Math.min(w, in.x + in.width);
        int y2 = Math.min(h, in.y + in.height);
        return new Rectangle(x, y, Math.max(0, x2 - x), Math.max(0, y2 - y));
    }

    /** Carry per-channel min/max from source to cropped so tile display matches. */
    private static void copyDisplayRanges(ImagePlus source, ImagePlus cropped) {
        if (source instanceof CompositeImage && cropped instanceof CompositeImage) {
            CompositeImage src = (CompositeImage) source;
            CompositeImage dst = (CompositeImage) cropped;
            int channels = Math.min(src.getNChannels(), dst.getNChannels());
            int rememberC = src.getChannel();
            int rememberD = dst.getChannel();
            try {
                for (int ch = 1; ch <= channels; ch++) {
                    src.setPositionWithoutUpdate(ch, src.getSlice(), src.getFrame());
                    dst.setPositionWithoutUpdate(ch, dst.getSlice(), dst.getFrame());
                    dst.setDisplayRange(src.getDisplayRangeMin(), src.getDisplayRangeMax());
                }
            } finally {
                src.setPositionWithoutUpdate(rememberC, src.getSlice(), src.getFrame());
                dst.setPositionWithoutUpdate(rememberD, dst.getSlice(), dst.getFrame());
            }
        } else {
            cropped.setDisplayRange(source.getDisplayRangeMin(), source.getDisplayRangeMax());
        }
    }
}
