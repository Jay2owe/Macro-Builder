package macro.builder.image;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.GaussianBlur3D;
import ij.process.Blitter;
import ij.process.ImageProcessor;

/**
 * Runs the Diffuse Object Filter directly on image pixels.
 * <p>
 * The bundled preset builds a Difference-of-Gaussians by duplicating the input
 * twice ({@code DoG_small} and {@code DoG_big}), blurring each duplicate with
 * the preset's 3D Gaussian sigmas, subtracting the big blur from the small blur
 * slice-by-slice, then applying a 3D median. This implementation mirrors that
 * sequence without showing images or touching {@code WindowManager}; the source
 * image's stack is replaced only once the output stack is complete.
 * <p>
 * Peak memory is estimated as input + two blur duplicates + one subtraction
 * result. If that exceeds 256 MiB, subtraction falls back to an in-place
 * slice-by-slice pass on the small-blur stack instead of materialising a third
 * full working stack.
 */
public final class DiffuseObjectFilter {

    private DiffuseObjectFilter() {}

    /** Values mirror src/main/resources/named-filters/diffuse_object_filter.ijm. */
    private static final double SMALL_SIGMA_XY = 2.0;
    private static final double SMALL_SIGMA_Z = 1.0;
    private static final double BIG_SIGMA_XY = 15.0;
    private static final double BIG_SIGMA_Z = 4.0;
    private static final float MEDIAN_RADIUS_X = 1.0f;
    private static final float MEDIAN_RADIUS_Y = 1.0f;
    private static final float MEDIAN_RADIUS_Z = 1.0f;
    private static final long MEMORY_LIMIT_BYTES = 256L * 1024L * 1024L;

    /**
     * True when {@code macroContent} is the bundled Diffuse Object filter - uses
     * the distinctive {@code DoG_small}/{@code DoG_big}/{@code Subtract create stack}
     * triplet that no other bundled preset emits.
     */
    public static boolean matches(String macroContent) {
        if (macroContent == null) return false;
        return macroContent.contains("DoG_small")
                && macroContent.contains("DoG_big")
                && macroContent.contains("Subtract create stack");
    }

    public static void apply(ImagePlus imp, String macroContent) {
        if (imp == null || imp.getStack() == null || imp.getStackSize() == 0) return;

        int channels = Math.max(1, imp.getNChannels());
        int slices = Math.max(1, imp.getNSlices());
        int frames = Math.max(1, imp.getNFrames());
        boolean openAsHyperStack = imp.isHyperStack();
        Calibration calibration = imp.getCalibration() == null ? null : imp.getCalibration().copy();

        ImagePlus small = duplicateStack(imp, "DoG_small");
        ImagePlus big = duplicateStack(imp, "DoG_big");

        GaussianBlur3D.blur(small, SMALL_SIGMA_XY, SMALL_SIGMA_XY, SMALL_SIGMA_Z);
        GaussianBlur3D.blur(big, BIG_SIGMA_XY, BIG_SIGMA_XY, BIG_SIGMA_Z);

        ImageStack dogStack = estimatedPeakBytes(imp) > MEMORY_LIMIT_BYTES
                ? subtractInPlace(small.getStack(), big.getStack())
                : subtractToNewStack(small.getStack(), big.getStack());
        boolean dogUsesSmallStack = dogStack == small.getStack();
        big.flush();
        if (!dogUsesSmallStack) {
            small.flush();
        }

        ImageStack medianStack = ij.plugin.Filters3D.filter(
                dogStack,
                ij.plugin.Filters3D.MEDIAN,
                MEDIAN_RADIUS_X,
                MEDIAN_RADIUS_Y,
                MEDIAN_RADIUS_Z);
        if (medianStack != null) {
            dogStack = medianStack;
        }

        imp.setStack(dogStack);
        if (channels * slices * frames == dogStack.getSize()) {
            imp.setDimensions(channels, slices, frames);
            if (openAsHyperStack) imp.setOpenAsHyperStack(true);
        }
        if (calibration != null) {
            imp.setCalibration(calibration);
        }
    }

    private static ImagePlus duplicateStack(ImagePlus source, String title) {
        ImageStack src = source.getStack();
        ImageStack copy = new ImageStack(source.getWidth(), source.getHeight());
        for (int s = 1; s <= src.getSize(); s++) {
            copy.addSlice(src.getSliceLabel(s), src.getProcessor(s).duplicate());
        }
        ImagePlus out = new ImagePlus(title, copy);
        if (source.getCalibration() != null) {
            out.setCalibration(source.getCalibration().copy());
        }
        int channels = Math.max(1, source.getNChannels());
        int slices = Math.max(1, source.getNSlices());
        int frames = Math.max(1, source.getNFrames());
        if (channels * slices * frames == copy.getSize()) {
            out.setDimensions(channels, slices, frames);
            if (source.isHyperStack()) out.setOpenAsHyperStack(true);
        }
        return out;
    }

    private static ImageStack subtractToNewStack(ImageStack small, ImageStack big) {
        ImageStack out = new ImageStack(small.getWidth(), small.getHeight());
        for (int s = 1; s <= small.getSize(); s++) {
            ImageProcessor result = small.getProcessor(s).duplicate();
            result.copyBits(big.getProcessor(s), 0, 0, Blitter.SUBTRACT);
            out.addSlice(small.getSliceLabel(s), result);
        }
        return out;
    }

    private static ImageStack subtractInPlace(ImageStack small, ImageStack big) {
        for (int s = 1; s <= small.getSize(); s++) {
            small.getProcessor(s).copyBits(big.getProcessor(s), 0, 0, Blitter.SUBTRACT);
        }
        return small;
    }

    private static long estimatedPeakBytes(ImagePlus imp) {
        long pixels = (long) imp.getWidth() * (long) imp.getHeight() * (long) imp.getStackSize();
        long bytesPerStack = pixels * bytesPerPixel(imp.getBitDepth());
        return bytesPerStack * 4L;
    }

    private static int bytesPerPixel(int bitDepth) {
        switch (bitDepth) {
            case 8: return 1;
            case 16: return 2;
            case 24: return 4;
            case 32: return 4;
            default: return 4;
        }
    }
}
