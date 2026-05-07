package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.Duplicator;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import macro.builder.image.FilterExecutor;
import macro.builder.image.dag.IjmToDagLoader;

import java.util.ArrayList;
import java.util.List;

public final class ThresholdShootoutRunner {

    public List<ShootoutResult> run(ImagePlus source, String macro, ShootoutSettings settings) {
        return run(source, macro, settings, null);
    }

    public List<ShootoutResult> run(
            ImagePlus source,
            String macro,
            ShootoutSettings settings,
            FilterExecutor.Progress progress) {
        return run(source, macro, settings, 1, progress);
    }

    public List<ShootoutResult> run(
            ImagePlus source,
            String macro,
            ShootoutSettings settings,
            int primaryChannel,
            FilterExecutor.Progress progress) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        ImagePlus processed = duplicateForMacro(source, macro, primaryChannel);
        if (processed == null) {
            List<ShootoutResult> rows = new ArrayList<ShootoutResult>();
            rows.add(ShootoutResult.failure(settings.countingMode, "Macro", null, "Could not duplicate source image"));
            return rows;
        }

        try {
            FilterExecutor.runThreadSafe(processed, macro == null ? "" : macro, progress);
            Range range = measureRange(processed);
            return runThresholds(processed, range, settings);
        } catch (RuntimeException ex) {
            List<ShootoutResult> rows = new ArrayList<ShootoutResult>();
            rows.add(ShootoutResult.failure(settings.countingMode, "Macro", null, cleanMessage(ex)));
            return rows;
        } finally {
            processed.flush();
        }
    }

    public static List<String> defaultAutoMethods() {
        return ShootoutSettings.defaultAutoMethods();
    }

    private static List<ShootoutResult> runThresholds(ImagePlus processed, Range range, ShootoutSettings settings) {
        List<ShootoutResult> rows = new ArrayList<ShootoutResult>();

        if (usesAuto(settings.thresholdMode)) {
            List<String> methods = settings.autoMethods.isEmpty()
                    ? ShootoutSettings.defaultAutoMethods()
                    : settings.autoMethods;
            for (String method : methods) {
                rows.add(runAutoVariant(processed, range, settings, method));
            }
        }

        if (usesFixed(settings.thresholdMode)) {
            for (Double value : settings.fixedThresholds) {
                rows.add(runFixedVariant(processed, range, settings, value.doubleValue()));
            }
        }

        return rows;
    }

    private static ShootoutResult runAutoVariant(
            ImagePlus processed,
            Range range,
            ShootoutSettings settings,
            String method) {
        try {
            ThresholdWindow window = autoThresholdWindow(processed, range, settings.darkBackground, method);
            ImagePlus mask = createMask(processed, method + " mask", window.lower, window.upper);
            ObjectCounter.CountSummary count = ObjectCounter.count(mask, settings);
            return ShootoutResult.success(
                    settings.countingMode,
                    method,
                    Double.valueOf(window.displayValue),
                    range.minimum,
                    range.maximum,
                    mask,
                    count);
        } catch (RuntimeException ex) {
            return ShootoutResult.failure(
                    settings.countingMode,
                    method,
                    null,
                    range.minimum,
                    range.maximum,
                    cleanMessage(ex));
        }
    }

    private static ShootoutResult runFixedVariant(
            ImagePlus processed,
            Range range,
            ShootoutSettings settings,
            double value) {
        String label = fixedLabel(value);
        try {
            ImagePlus mask = createMask(processed, label + " mask", value, range.maximum);
            ObjectCounter.CountSummary count = ObjectCounter.count(mask, settings);
            return ShootoutResult.success(
                    settings.countingMode,
                    label,
                    Double.valueOf(value),
                    range.minimum,
                    range.maximum,
                    mask,
                    count);
        } catch (RuntimeException ex) {
            return ShootoutResult.failure(
                    settings.countingMode,
                    label,
                    Double.valueOf(value),
                    range.minimum,
                    range.maximum,
                    cleanMessage(ex));
        }
    }

    private static ThresholdWindow autoThresholdWindow(
            ImagePlus image,
            Range range,
            boolean darkBackground,
            String method) {
        AutoThresholder.Method thresholdMethod = AutoThresholder.Method.valueOf(method);
        int[] histogram = buildHistogram(image, range);
        int thresholdBin = new AutoThresholder().getThreshold(thresholdMethod, histogram);
        if (thresholdBin < 0) {
            throw new IllegalArgumentException("No threshold found for " + method);
        }

        double thresholdValue = nativeValueForBin(range, thresholdBin);
        if (darkBackground) {
            double lower = nativeValueForBin(range, thresholdBin + 1);
            return new ThresholdWindow(lower, range.maximum, lower);
        }
        return new ThresholdWindow(range.minimum, thresholdValue, thresholdValue);
    }

    private static ImagePlus createMask(ImagePlus image, String title, double lower, double upper) {
        ImageStack sourceStack = image.getStack();
        ImageStack maskStack = new ImageStack(image.getWidth(), image.getHeight());
        int width = image.getWidth();
        int height = image.getHeight();

        for (int s = 1; s <= sourceStack.getSize(); s++) {
            ImageProcessor source = sourceStack.getProcessor(s);
            ByteProcessor mask = new ByteProcessor(width, height);
            byte[] pixels = (byte[]) mask.getPixels();
            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double value = source.getPixelValue(x, y);
                    if (isFinite(value) && value >= lower && value <= upper) {
                        pixels[index] = (byte) 255;
                    }
                    index++;
                }
            }
            maskStack.addSlice(sourceStack.getSliceLabel(s), mask);
        }

        ImagePlus maskImage = new ImagePlus(title, maskStack);
        copyDimensions(image, maskImage);
        return maskImage;
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
                    if (!isFinite(value)) {
                        continue;
                    }
                    if (value < minimum) {
                        minimum = value;
                    }
                    if (value > maximum) {
                        maximum = value;
                    }
                }
            }
        }
        if (minimum == Double.POSITIVE_INFINITY) {
            return new Range(0.0, 0.0);
        }
        return new Range(minimum, maximum);
    }

    private static int[] buildHistogram(ImagePlus image, Range range) {
        int[] histogram = new int[256];
        ImageStack stack = image.getStack();
        for (int s = 1; s <= stack.getSize(); s++) {
            ImageProcessor processor = stack.getProcessor(s);
            int width = processor.getWidth();
            int height = processor.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double value = processor.getPixelValue(x, y);
                    if (isFinite(value)) {
                        histogram[binFor(value, range)]++;
                    }
                }
            }
        }
        return histogram;
    }

    private static int binFor(double value, Range range) {
        if (range.maximum <= range.minimum) {
            return 0;
        }
        int bin = (int) Math.floor((value - range.minimum) * 255.0 / (range.maximum - range.minimum));
        if (bin < 0) {
            return 0;
        }
        if (bin > 255) {
            return 255;
        }
        return bin;
    }

    private static double nativeValueForBin(Range range, int bin) {
        if (range.maximum <= range.minimum) {
            return range.minimum;
        }
        if (bin <= 0) {
            return range.minimum;
        }
        if (bin >= 255) {
            return range.maximum;
        }
        return range.minimum + (range.maximum - range.minimum) * bin / 255.0;
    }

    private static ImagePlus duplicateForMacro(ImagePlus source, String macro, int primaryChannel) {
        if (IjmToDagLoader.loadEmbeddedDag(macro) != null) {
            return duplicate(source);
        }
        return FilterExecutor.duplicateChannel(
                source,
                Math.max(1, primaryChannel),
                "Macro Builder Count Shootout Source");
    }

    private static ImagePlus duplicate(ImagePlus source) {
        ImagePlus copy = new Duplicator().run(source,
                1, Math.max(1, source.getNChannels()),
                1, Math.max(1, source.getNSlices()),
                1, Math.max(1, source.getNFrames()));
        if (copy != null) {
            copy.setTitle("Macro Builder Count Shootout Source");
        }
        return copy;
    }

    private static void copyDimensions(ImagePlus source, ImagePlus target) {
        int channels = Math.max(1, source.getNChannels());
        int slices = Math.max(1, source.getNSlices());
        int frames = Math.max(1, source.getNFrames());
        if (channels * slices * frames == target.getStackSize()) {
            target.setDimensions(channels, slices, frames);
            if (source.isHyperStack()) {
                target.setOpenAsHyperStack(true);
            }
        }
        if (source.getCalibration() != null) {
            target.setCalibration(source.getCalibration().copy());
        }
    }

    private static boolean usesAuto(ShootoutSettings.ThresholdMode mode) {
        return mode == ShootoutSettings.ThresholdMode.AUTO_METHODS
                || mode == ShootoutSettings.ThresholdMode.AUTO_AND_FIXED;
    }

    private static boolean usesFixed(ShootoutSettings.ThresholdMode mode) {
        return mode == ShootoutSettings.ThresholdMode.FIXED_VALUES
                || mode == ShootoutSettings.ThresholdMode.AUTO_AND_FIXED;
    }

    private static String fixedLabel(double value) {
        if (value == Math.rint(value)) {
            return "Fixed " + Long.toString(Math.round(value));
        }
        return "Fixed " + Double.toString(value);
    }

    private static String cleanMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown error";
        }
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return message.trim();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Range {
        final double minimum;
        final double maximum;

        Range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    private static final class ThresholdWindow {
        final double lower;
        final double upper;
        final double displayValue;

        ThresholdWindow(double lower, double upper, double displayValue) {
            this.lower = lower;
            this.upper = upper;
            this.displayValue = displayValue;
        }
    }
}
