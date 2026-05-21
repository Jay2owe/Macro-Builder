package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BackSolver {
    public static final int DEFAULT_GRID_STEPS = 30;
    public static final double SPREAD_FRACTION = 0.25;
    public static final long MASK_CAP_BYTES = 256L * 1024L * 1024L;
    private static final String VARIANT = "Click-fit";

    private BackSolver() {
    }

    public static BackSolverResult solve(
            ShootoutContext context,
            List<int[]> clicks,
            ShootoutSettings settings) {
        return solve(context, clicks, settings, DEFAULT_GRID_STEPS);
    }

    public static BackSolverResult solve(
            ShootoutContext context,
            List<int[]> clicks,
            ShootoutSettings settings,
            int gridSteps) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (context.processed == null) {
            throw new IllegalArgumentException("context.processed must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        List<int[]> safeClicks = immutablePointCopy(clicks);
        if (safeClicks.isEmpty()) {
            throw new IllegalArgumentException("clicks must not be empty");
        }
        ensureWithinMaskBudget(context.processed);

        List<Double> thresholds = ThresholdShootoutRunner.gridThresholds(context, gridSteps);
        if (thresholds.isEmpty()) {
            throw new IllegalArgumentException("no thresholds were available");
        }

        int[] catches = new int[thresholds.size()];
        int[] counts = new int[thresholds.size()];
        for (int i = 0; i < thresholds.size(); i++) {
            double threshold = thresholds.get(i).doubleValue();
            ImagePlus mask = null;
            try {
                mask = LiveMaskBuilder.build(context.processed, threshold, context.rangeMax);
                catches[i] = countCatches(mask, safeClicks);
                counts[i] = ObjectCounter.count(mask, settings).count;
            } finally {
                closeImageQuietly(mask);
            }
        }

        double median = median(counts);
        int best = bestSpreadCheckedIndex(catches, counts, median);
        boolean fallback = false;
        if (best < 0) {
            best = bestCatchIndex(catches, counts, median);
            fallback = true;
        }

        double threshold = thresholds.get(best).doubleValue();
        ImagePlus finalMask = LiveMaskBuilder.build(context.processed, threshold, context.rangeMax);
        finalMask.setTitle("Click-fit mask");
        ObjectCounter.CountSummary count = ObjectCounter.count(finalMask, settings);
        int caught = countCatches(finalMask, safeClicks);
        String reason = "catches " + caught + " of " + safeClicks.size()
                + " clicked objects with a plausible count of " + count.count + ".";
        if (fallback) {
            reason = reason + " count varies a lot; manual verification recommended.";
        }

        return new BackSolverResult(
                threshold,
                caught,
                safeClicks.size(),
                median,
                fallback,
                finalMask,
                count,
                reason);
    }

    public static BackSolverResult solve(
            ImagePlus processed,
            List<int[]> clicks,
            ShootoutSettings settings,
            int gridSteps) {
        return solve(contextFor(processed), clicks, settings, gridSteps);
    }

    public static ShootoutResult toShootoutResult(
            BackSolverResult solved,
            ShootoutContext context,
            ShootoutSettings settings) {
        if (solved == null) {
            throw new IllegalArgumentException("solved must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        return ShootoutResult.success(
                ShootoutResult.Source.CLICK_FIT,
                settings.countingMode,
                VARIANT,
                Double.valueOf(solved.threshold),
                context.rangeMin,
                context.rangeMax,
                solved.maskPreview,
                solved.countSummary)
                .withRecommendation(solved.reason);
    }

    static int countCatches(ImagePlus mask, List<int[]> clicks) {
        if (mask == null) {
            throw new IllegalArgumentException("mask must not be null");
        }
        if (clicks == null || clicks.isEmpty()) {
            return 0;
        }
        int caught = 0;
        for (int i = 0; i < clicks.size(); i++) {
            int[] point = clicks.get(i);
            if (point != null && point.length >= 2 && isForeground(mask, point)) {
                caught++;
            }
        }
        return caught;
    }

    private static boolean isForeground(ImagePlus mask, int[] point) {
        int x = point[0];
        int y = point[1];
        if (x < 0 || y < 0 || x >= mask.getWidth() || y >= mask.getHeight()) {
            return false;
        }
        int z = point.length > 2 ? point[2] : 1;
        int stackIndex = stackIndex(mask, z);
        ImageProcessor processor = mask.getStack().getProcessor(stackIndex);
        return processor.getPixelValue(x, y) > 0.0f;
    }

    private static int stackIndex(ImagePlus mask, int z) {
        int channels = Math.max(1, mask.getNChannels());
        int slices = Math.max(1, mask.getNSlices());
        int frames = Math.max(1, mask.getNFrames());
        if (channels * slices * frames == mask.getStackSize()) {
            int slice = clamp(z, 1, slices);
            return mask.getStackIndex(1, slice, 1);
        }
        return clamp(z, 1, mask.getStackSize());
    }

    private static int bestSpreadCheckedIndex(int[] catches, int[] counts, double median) {
        int best = -1;
        int bestCatch = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double lower = median * (1.0 - SPREAD_FRACTION);
        double upper = median * (1.0 + SPREAD_FRACTION);
        for (int i = 0; i < catches.length; i++) {
            if (counts[i] < lower || counts[i] > upper) {
                continue;
            }
            double distance = Math.abs(counts[i] - median);
            if (catches[i] > bestCatch || (catches[i] == bestCatch && distance < bestDistance)) {
                best = i;
                bestCatch = catches[i];
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int bestCatchIndex(int[] catches, int[] counts, double median) {
        int best = 0;
        int bestCatch = catches[0];
        double bestDistance = Math.abs(counts[0] - median);
        for (int i = 1; i < catches.length; i++) {
            double distance = Math.abs(counts[i] - median);
            if (catches[i] > bestCatch || (catches[i] == bestCatch && distance < bestDistance)) {
                best = i;
                bestCatch = catches[i];
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double median(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        if ((sorted.length & 1) == 1) {
            return sorted[middle];
        }
        return (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    private static ShootoutContext contextFor(ImagePlus processed) {
        if (processed == null) {
            throw new IllegalArgumentException("processed must not be null");
        }
        Range range = measureRange(processed);
        return new ShootoutContext(processed, new int[256], range.minimum, range.maximum, false);
    }

    private static Range measureRange(ImagePlus image) {
        ImageStack stack = image.getStack();
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int s = 1; s <= stack.getSize(); s++) {
            ImageProcessor processor = stack.getProcessor(s);
            int width = processor.getWidth();
            int height = processor.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double value = processor.getPixelValue(x, y);
                    if (isFinite(value)) {
                        if (value < minimum) {
                            minimum = value;
                        }
                        if (value > maximum) {
                            maximum = value;
                        }
                    }
                }
            }
        }
        if (minimum == Double.POSITIVE_INFINITY || maximum == Double.NEGATIVE_INFINITY) {
            return new Range(0.0, 0.0);
        }
        return new Range(minimum, maximum);
    }

    private static void ensureWithinMaskBudget(ImagePlus image) {
        long pixels = (long) image.getWidth()
                * (long) image.getHeight()
                * (long) Math.max(1, image.getStackSize());
        if (pixels > MASK_CAP_BYTES) {
            throw new IllegalArgumentException("Click-fit mask estimate exceeds the 256 MiB cap.");
        }
    }

    private static List<int[]> immutablePointCopy(List<int[]> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<int[]> copy = new ArrayList<int[]>(points.size());
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            if (point == null || point.length < 2) {
                continue;
            }
            int z = point.length > 2 ? point[2] : 1;
            copy.add(new int[]{point[0], point[1], Math.max(1, z)});
        }
        return Collections.unmodifiableList(copy);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void closeImageQuietly(ImagePlus image) {
        if (image == null) {
            return;
        }
        try {
            image.changes = false;
            if (image.getWindow() != null) {
                image.close();
            } else {
                image.flush();
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class Range {
        final double minimum;
        final double maximum;

        Range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    public static final class BackSolverResult {
        public final double threshold;
        public final int caughtClicks;
        public final int clickedObjects;
        public final double medianCount;
        public final boolean spreadFallback;
        public final ImagePlus maskPreview;
        public final ObjectCounter.CountSummary countSummary;
        public final String reason;

        private BackSolverResult(
                double threshold,
                int caughtClicks,
                int clickedObjects,
                double medianCount,
                boolean spreadFallback,
                ImagePlus maskPreview,
                ObjectCounter.CountSummary countSummary,
                String reason) {
            this.threshold = threshold;
            this.caughtClicks = caughtClicks;
            this.clickedObjects = clickedObjects;
            this.medianCount = medianCount;
            this.spreadFallback = spreadFallback;
            this.maskPreview = maskPreview;
            this.countSummary = countSummary;
            this.reason = reason;
        }
    }
}
