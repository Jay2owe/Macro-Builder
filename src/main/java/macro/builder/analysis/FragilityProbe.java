package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.Duplicator;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

public final class FragilityProbe {

    private static final double SMALL_THRESHOLD_WIGGLE = 0.05;
    private static final double LARGE_THRESHOLD_WIGGLE = 0.10;
    private static final double INTENSITY_JITTER = 1.01;

    private FragilityProbe() {
    }

    public static int[] probe(ImagePlus processed, ShootoutSettings settings, double centreThreshold) {
        if (processed == null) {
            throw new IllegalArgumentException("processed must not be null");
        }
        Range range = measureRange(processed);
        return probe(new ShootoutContext(
                processed,
                new int[0],
                range.minimum,
                range.maximum,
                containsFloatProcessor(processed)), settings, centreThreshold);
    }

    public static int[] probe(ShootoutContext context, ShootoutSettings settings, double centreThreshold) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        if (!isFinite(centreThreshold)) {
            return new int[0];
        }

        double span = context.rangeMax - context.rangeMin;
        if (!isFinite(span) || span < 0.0) {
            span = 0.0;
        }

        List<Integer> counts = new ArrayList<Integer>(6);
        addThresholdSample(counts, context, settings, centreThreshold, -SMALL_THRESHOLD_WIGGLE * span);
        addThresholdSample(counts, context, settings, centreThreshold, SMALL_THRESHOLD_WIGGLE * span);
        addThresholdSample(counts, context, settings, centreThreshold, -LARGE_THRESHOLD_WIGGLE * span);
        addThresholdSample(counts, context, settings, centreThreshold, LARGE_THRESHOLD_WIGGLE * span);
        counts.add(Integer.valueOf(countShifted(context.processed, settings, centreThreshold)));
        counts.add(Integer.valueOf(countJittered(context.processed, settings, centreThreshold)));
        return toIntArray(counts);
    }

    public static double scoreFrom(int[] counts, int centre) {
        int min = centre;
        int max = centre;
        if (counts != null) {
            for (int count : counts) {
                if (count < min) {
                    min = count;
                }
                if (count > max) {
                    max = count;
                }
            }
        }
        if (centre <= 0) {
            return max == min ? 0.0 : 1.0;
        }
        return Math.min(1.0, Math.max(0.0, (max - min) / (double) centre));
    }

    static int countAtThreshold(ImagePlus image, ShootoutSettings settings, double threshold) {
        if (image == null || settings == null || !isFinite(threshold)) {
            return 0;
        }
        double lower = settings.darkBackground ? threshold : -Double.MAX_VALUE;
        double upper = settings.darkBackground ? Double.MAX_VALUE : threshold;
        ImagePlus mask = null;
        try {
            mask = ThresholdShootoutRunner.createMask(image, "Fragility probe mask", lower, upper);
            return ObjectCounter.count(mask, settings).count;
        } finally {
            closeImageQuietly(mask);
        }
    }

    private static void addThresholdSample(
            List<Integer> counts,
            ShootoutContext context,
            ShootoutSettings settings,
            double centreThreshold,
            double delta) {
        counts.add(Integer.valueOf(countAtThreshold(
                context.processed,
                settings,
                centreThreshold + delta)));
    }

    private static int countShifted(ImagePlus processed, ShootoutSettings settings, double centreThreshold) {
        ImagePlus shifted = duplicate(processed, "Fragility 1px shift");
        try {
            ImageStack stack = shifted.getStack();
            for (int s = 1; s <= stack.getSize(); s++) {
                stack.getProcessor(s).translate(1.0, 0.0);
            }
            return countAtThreshold(shifted, settings, centreThreshold);
        } finally {
            closeImageQuietly(shifted);
        }
    }

    private static int countJittered(ImagePlus processed, ShootoutSettings settings, double centreThreshold) {
        ImagePlus jittered = duplicate(processed, "Fragility 1% brightness jitter");
        try {
            ImageStack stack = jittered.getStack();
            for (int s = 1; s <= stack.getSize(); s++) {
                multiplyPixels(stack.getProcessor(s), INTENSITY_JITTER);
            }
            return countAtThreshold(jittered, settings, centreThreshold);
        } finally {
            closeImageQuietly(jittered);
        }
    }

    private static ImagePlus duplicate(ImagePlus source, String title) {
        ImagePlus copy = new Duplicator().run(source,
                1, Math.max(1, source.getNChannels()),
                1, Math.max(1, source.getNSlices()),
                1, Math.max(1, source.getNFrames()));
        if (copy == null) {
            throw new IllegalArgumentException("Could not duplicate image for fragility check");
        }
        copy.setTitle(title);
        return copy;
    }

    private static void multiplyPixels(ImageProcessor processor, double factor) {
        Object pixels = processor.getPixels();
        if (pixels instanceof byte[]) {
            byte[] values = (byte[]) pixels;
            for (int i = 0; i < values.length; i++) {
                int value = values[i] & 0xff;
                values[i] = (byte) Math.min(255, Math.round(value * factor));
            }
            return;
        }
        if (pixels instanceof short[]) {
            short[] values = (short[]) pixels;
            for (int i = 0; i < values.length; i++) {
                int value = values[i] & 0xffff;
                values[i] = (short) Math.min(65535, Math.round(value * factor));
            }
            return;
        }
        if (pixels instanceof float[]) {
            float[] values = (float[]) pixels;
            for (int i = 0; i < values.length; i++) {
                float value = values[i];
                if (isFinite(value)) {
                    values[i] = (float) (value * factor);
                }
            }
            return;
        }
        processor.multiply(factor);
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

    private static boolean containsFloatProcessor(ImagePlus image) {
        ImageStack stack = image.getStack();
        for (int s = 1; s <= stack.getSize(); s++) {
            if (stack.getProcessor(s).getPixels() instanceof float[]) {
                return true;
            }
        }
        return false;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i).intValue();
        }
        return out;
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

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static final class Range {
        final double minimum;
        final double maximum;

        Range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }
}
