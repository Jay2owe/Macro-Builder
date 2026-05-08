package macro.builder.image.variation;

import ij.IJ;
import ij.ImagePlus;

import java.util.Locale;

/**
 * Projects how much working memory a variant run will consume and decides
 * whether the user should be forced into ROI mode.
 *
 * <p>Mirrors the Auto Threshold "Try all" {@code nSlices > 25} guard but
 * computed on bytes: {@code projectedBytes = sourceBytes * variantCount * 1.3}.
 * The 1.3 multiplier covers the per-line {@code cloneChannelStack} working
 * copies inside {@link macro.builder.image.FilterExecutor#runDagThreadSafe}.
 *
 * <p>Budget threshold is 25% of {@link IJ#maxMemory()} — conservative on
 * purpose because {@code IJ.maxMemory()} reports the JVM {@code -Xmx} ceiling,
 * not currently-available memory. Other Fiji windows, plugin caches, and
 * preview tiles compete for the rest.
 */
public final class MemoryEstimator {

    /** Per-line clone overhead inside {@code runDagThreadSafe}. */
    public static final double OVERHEAD_FACTOR = 1.3;

    /** Headroom threshold above which ROI mode is forced. */
    public static final double BUDGET_FRACTION = 0.25;

    private MemoryEstimator() {}

    /**
     * Estimate using {@link IJ#maxMemory()} as the heap ceiling.
     */
    public static MemoryEstimate estimate(ImagePlus source, int variantCount) {
        return estimate(source, variantCount, IJ.maxMemory());
    }

    /**
     * Estimate against an explicit heap ceiling. Package-public so tests can
     * mock {@code IJ.maxMemory()} without touching the static.
     */
    public static MemoryEstimate estimate(ImagePlus source, int variantCount, long maxHeapBytes) {
        if (variantCount < 1) {
            throw new IllegalArgumentException("variantCount must be >= 1, was " + variantCount);
        }
        if (maxHeapBytes < 1L) {
            throw new IllegalArgumentException("maxHeapBytes must be >= 1, was " + maxHeapBytes);
        }
        long sourceBytes = computeSourceBytes(source);
        long projected = (long) (sourceBytes * (double) variantCount * OVERHEAD_FACTOR);
        double headroom = (double) projected / (double) maxHeapBytes;
        boolean exceeds = headroom > BUDGET_FRACTION;
        String message = format(variantCount, sourceBytes, projected, maxHeapBytes, headroom, exceeds);
        return new MemoryEstimate(sourceBytes, projected, maxHeapBytes, headroom, exceeds, message);
    }

    /** Bytes for one full copy of the image stack, accounting for bit depth. */
    static long computeSourceBytes(ImagePlus imp) {
        if (imp == null) return 0L;
        long w = imp.getWidth();
        long h = imp.getHeight();
        long slices = imp.getStackSize();
        if (slices < 1) slices = 1;
        int bitDepth = imp.getBitDepth();
        int bytesPerPixel;
        if (bitDepth == 24) {
            // 24-bit RGB is stored in ColorProcessor as int (4 bytes/pixel).
            bytesPerPixel = 4;
        } else if (bitDepth <= 0) {
            bytesPerPixel = 1;
        } else {
            bytesPerPixel = bitDepth / 8;
            if (bytesPerPixel < 1) bytesPerPixel = 1;
        }
        return w * h * slices * (long) bytesPerPixel;
    }

    private static String format(int variantCount, long sourceBytes, long projected,
                                 long maxHeap, double headroom, boolean exceeds) {
        String verdict = exceeds ? " — ROI mode required" : " — fits in budget";
        return String.format(Locale.US,
                "%d variants × %s = %s (%.0f%% of %s heap)%s",
                variantCount,
                formatGiB(sourceBytes),
                formatGiB(projected),
                headroom * 100.0,
                formatGiB(maxHeap),
                verdict);
    }

    private static String formatGiB(long bytes) {
        double gib = bytes / (double) (1L << 30);
        return String.format(Locale.US, "%.1f GiB", gib);
    }
}
