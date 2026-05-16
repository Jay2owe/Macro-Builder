package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Measurements;
import ij.plugin.Duplicator;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import macro.builder.image.FilterExecutor;
import macro.builder.image.ParallelContext;
import macro.builder.image.dag.IjmToDagLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ThresholdShootoutRunner {
    private static final int HISTOGRAM_BINS = 256;
    private static final int SLICE_PARALLEL_THRESHOLD = 4;

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
        ShootoutRun run = runWithContext(source, macro, settings, primaryChannel, progress);
        try {
            return new ArrayList<ShootoutResult>(run.results);
        } finally {
            closeProcessed(run.context);
        }
    }

    public ShootoutRun runWithContext(ImagePlus source, String macro, ShootoutSettings settings) {
        return runWithContext(source, macro, settings, null);
    }

    public ShootoutRun runWithContext(
            ImagePlus source,
            String macro,
            ShootoutSettings settings,
            FilterExecutor.Progress progress) {
        return runWithContext(source, macro, settings, 1, progress);
    }

    public ShootoutRun runWithContext(
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
            return new ShootoutRun(null, rows);
        }

        boolean transferProcessed = false;
        try {
            FilterExecutor.runThreadSafe(processed, macro == null ? "" : macro, progress);
            ShootoutContext context = buildContext(processed);
            List<ShootoutResult> rows = runThresholds(context, settings);
            transferProcessed = true;
            return new ShootoutRun(context, rows);
        } catch (RuntimeException ex) {
            List<ShootoutResult> rows = new ArrayList<ShootoutResult>();
            rows.add(ShootoutResult.failure(settings.countingMode, "Macro", null, cleanMessage(ex)));
            return new ShootoutRun(null, rows);
        } finally {
            if (!transferProcessed) {
                closeImageQuietly(processed);
            }
        }
    }

    public static List<String> defaultAutoMethods() {
        return ShootoutSettings.defaultAutoMethods();
    }

    private static List<ShootoutResult> runThresholds(ShootoutContext context, ShootoutSettings settings) {
        List<ShootoutResult> rows = new ArrayList<ShootoutResult>();

        if (usesAuto(settings.thresholdMode)) {
            List<String> methods = settings.autoMethods.isEmpty()
                    ? ShootoutSettings.defaultAutoMethods()
                    : settings.autoMethods;
            for (String method : methods) {
                rows.add(runAutoVariant(context, settings, method));
            }
        }

        if (usesFixed(settings.thresholdMode)) {
            for (Double value : settings.fixedThresholds) {
                rows.add(runFixedVariant(context, settings, value.doubleValue()));
            }
        }

        if (usesGrid(settings.thresholdMode)) {
            List<ShootoutResult> gridRows = new ArrayList<ShootoutResult>();
            List<Double> thresholds = gridThresholds(context, settings.gridSteps);
            for (Double value : thresholds) {
                gridRows.add(runGridVariant(context, settings, value.doubleValue()));
            }
            rows.addAll(withRecommendedPlateau(withGridFragility(gridRows, settings)));
        }

        if (settings.groundTruthReference != null) {
            return withRecommendedReferenceWinner(rows);
        }
        return rows;
    }

    private static ShootoutResult runAutoVariant(
            ShootoutContext context,
            ShootoutSettings settings,
            String method) {
        try {
            ThresholdWindow window = autoThresholdWindow(context, settings.darkBackground, method);
            ImagePlus mask = createMask(context.processed, method + " mask", window.lower, window.upper);
            ObjectCounter.CountSummary count = ObjectCounter.count(mask, settings);
            ShootoutResult result = ShootoutResult.success(
                    settings.countingMode,
                    method,
                    Double.valueOf(window.displayValue),
                    context.rangeMin,
                    context.rangeMax,
                    mask,
                    count);
            return withFragility(withQualityScores(withGroundTruthScore(result, settings), context), context, settings);
        } catch (RuntimeException ex) {
            return ShootoutResult.failure(
                    settings.countingMode,
                    method,
                    null,
                    context.rangeMin,
                    context.rangeMax,
                    cleanMessage(ex));
        }
    }

    private static ShootoutResult runFixedVariant(
            ShootoutContext context,
            ShootoutSettings settings,
            double value) {
        String label = fixedLabel(value);
        try {
            ImagePlus mask = createMask(context.processed, label + " mask", value, context.rangeMax);
            ObjectCounter.CountSummary count = ObjectCounter.count(mask, settings);
            ShootoutResult result = ShootoutResult.success(
                    settings.countingMode,
                    label,
                    Double.valueOf(value),
                    context.rangeMin,
                    context.rangeMax,
                    mask,
                    count);
            return withFragility(withQualityScores(withGroundTruthScore(result, settings), context), context, settings);
        } catch (RuntimeException ex) {
            return ShootoutResult.failure(
                    settings.countingMode,
                    label,
                    Double.valueOf(value),
                    context.rangeMin,
                    context.rangeMax,
                    cleanMessage(ex));
        }
    }

    private static ShootoutResult runGridVariant(
            ShootoutContext context,
            ShootoutSettings settings,
            double value) {
        String label = gridLabel(value);
        try {
            ImagePlus mask = createMask(context.processed, label + " mask", value, context.rangeMax);
            ObjectCounter.CountSummary count = ObjectCounter.count(mask, settings);
            ShootoutResult result = ShootoutResult.success(
                    settings.countingMode,
                    label,
                    Double.valueOf(value),
                    context.rangeMin,
                    context.rangeMax,
                    mask,
                    count);
            return withQualityScores(withGroundTruthScore(result, settings), context);
        } catch (RuntimeException ex) {
            return ShootoutResult.failure(
                    settings.countingMode,
                    label,
                    Double.valueOf(value),
                    context.rangeMin,
                    context.rangeMax,
                    cleanMessage(ex));
        }
    }

    private static List<ShootoutResult> withRecommendedPlateau(List<ShootoutResult> gridRows) {
        if (gridRows == null || gridRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> rowIndexes = new ArrayList<Integer>();
        List<Double> thresholds = new ArrayList<Double>();
        List<Integer> counts = new ArrayList<Integer>();
        for (int i = 0; i < gridRows.size(); i++) {
            ShootoutResult row = gridRows.get(i);
            if (row != null && row.isSuccess() && row.thresholdValue != null && row.countSummary != null) {
                rowIndexes.add(Integer.valueOf(i));
                thresholds.add(row.thresholdValue);
                counts.add(Integer.valueOf(row.countSummary.count));
            }
        }
        if (rowIndexes.isEmpty()) {
            return gridRows;
        }

        double[] thresholdValues = new double[thresholds.size()];
        int[] countValues = new int[counts.size()];
        for (int i = 0; i < thresholds.size(); i++) {
            thresholdValues[i] = thresholds.get(i).doubleValue();
            countValues[i] = counts.get(i).intValue();
        }

        int plateauIndex = PlateauFinder.findPlateauIndex(thresholdValues, countValues);
        if (plateauIndex < 0) {
            return gridRows;
        }

        List<ShootoutResult> updated = new ArrayList<ShootoutResult>(gridRows);
        int rowIndex = rowIndexes.get(plateauIndex).intValue();
        updated.set(rowIndex, updated.get(rowIndex).withRecommendation(PlateauFinder.DEFAULT_REASON));
        return updated;
    }

    private static List<ShootoutResult> withGridFragility(
            List<ShootoutResult> gridRows,
            ShootoutSettings settings) {
        if (gridRows == null || gridRows.isEmpty()) {
            return Collections.emptyList();
        }
        if (settings == null || !settings.runFragilityChecks) {
            return gridRows;
        }

        List<ShootoutResult> updated = new ArrayList<ShootoutResult>(gridRows.size());
        for (int i = 0; i < gridRows.size(); i++) {
            ShootoutResult row = gridRows.get(i);
            if (row == null || !row.isSuccess() || row.countSummary == null) {
                updated.add(row);
                continue;
            }
            int[] samples = neighbourGridCounts(gridRows, i);
            updated.add(row.withFragility(
                    FragilityProbe.scoreFrom(samples, row.countSummary.count),
                    samples));
        }
        return updated;
    }

    private static int[] neighbourGridCounts(List<ShootoutResult> gridRows, int index) {
        List<Integer> counts = new ArrayList<Integer>(4);
        addNeighbourCount(counts, gridRows, index - 1);
        addNeighbourCount(counts, gridRows, index + 1);
        addNeighbourCount(counts, gridRows, index - 2);
        addNeighbourCount(counts, gridRows, index + 2);
        int[] out = new int[counts.size()];
        for (int i = 0; i < counts.size(); i++) {
            out[i] = counts.get(i).intValue();
        }
        return out;
    }

    private static void addNeighbourCount(List<Integer> counts, List<ShootoutResult> rows, int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        ShootoutResult row = rows.get(index);
        if (row != null && row.isSuccess() && row.countSummary != null) {
            counts.add(Integer.valueOf(row.countSummary.count));
        }
    }

    private static ShootoutResult withGroundTruthScore(ShootoutResult result, ShootoutSettings settings) {
        if (result == null || settings == null || settings.groundTruthReference == null || !result.isSuccess()) {
            return result;
        }
        GroundTruthScorer.ScoreSummary score =
                GroundTruthScorer.score(result.maskPreview, settings.groundTruthReference, settings);
        return result.withGroundTruthScore(score);
    }

    private static ShootoutResult withQualityScores(ShootoutResult result, ShootoutContext context) {
        if (result == null || context == null || !result.isSuccess() || result.thresholdValue == null) {
            return result;
        }
        double threshold = result.thresholdValue.doubleValue();
        return result.withQualityScores(
                HistogramQualityScorer.separation(context.histogram, threshold, context),
                HistogramQualityScorer.distinctness(context.histogram, threshold, context));
    }

    private static ShootoutResult withFragility(
            ShootoutResult result,
            ShootoutContext context,
            ShootoutSettings settings) {
        if (result == null
                || context == null
                || settings == null
                || !settings.runFragilityChecks
                || !result.isSuccess()
                || result.thresholdValue == null
                || result.countSummary == null) {
            return result;
        }
        int[] samples = FragilityProbe.probe(context, settings, result.thresholdValue.doubleValue());
        return result.withFragility(
                FragilityProbe.scoreFrom(samples, result.countSummary.count),
                samples);
    }

    private static List<ShootoutResult> withRecommendedReferenceWinner(List<ShootoutResult> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        int bestIndex = -1;
        double bestF1 = Double.NEGATIVE_INFINITY;
        double bestRecall = Double.NEGATIVE_INFINITY;
        double bestPrecision = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < rows.size(); i++) {
            ShootoutResult row = rows.get(i);
            if (row == null || !row.isSuccess() || !isFinite(row.f1)) {
                continue;
            }
            if (row.f1 > bestF1
                    || (row.f1 == bestF1 && row.recall > bestRecall)
                    || (row.f1 == bestF1 && row.recall == bestRecall && row.precision > bestPrecision)) {
                bestIndex = i;
                bestF1 = row.f1;
                bestRecall = row.recall;
                bestPrecision = row.precision;
            }
        }
        if (bestIndex < 0) {
            return rows;
        }
        List<ShootoutResult> updated = new ArrayList<ShootoutResult>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ShootoutResult row = rows.get(i);
            if (row == null) {
                updated.add(null);
            } else if (i == bestIndex) {
                updated.add(row.withRecommendation("highest agreement with your reference"));
            } else {
                updated.add(row.withoutRecommendation());
            }
        }
        return updated;
    }

    static List<Double> gridThresholds(ShootoutContext context, int steps) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        int count = Math.max(1, steps);
        double span = context.rangeMax - context.rangeMin;
        if (!isFinite(span) || span <= 0.0 || count < 2) {
            return Collections.singletonList(Double.valueOf(context.rangeMin));
        }

        List<Double> values = new ArrayList<Double>(count);
        for (int i = 0; i < count; i++) {
            values.add(Double.valueOf(context.rangeMin + span * i / (double) (count - 1)));
        }
        return values;
    }

    private static ThresholdWindow autoThresholdWindow(
            ShootoutContext context,
            boolean darkBackground,
            String method) {
        AutoThresholder.Method thresholdMethod = AutoThresholder.Method.valueOf(method);
        int[] histogram = context.histogram.clone();
        int thresholdBin = new AutoThresholder().getThreshold(thresholdMethod, histogram);
        if (thresholdBin < 0) {
            throw new IllegalArgumentException("No threshold found for " + method);
        }

        Range range = new Range(context.rangeMin, context.rangeMax);
        double thresholdValue = nativeValueForBin(range, thresholdBin);
        if (darkBackground) {
            double lower = nativeValueForBin(range, thresholdBin + 1);
            return new ThresholdWindow(lower, context.rangeMax, lower);
        }
        return new ThresholdWindow(context.rangeMin, thresholdValue, thresholdValue);
    }

    static ImagePlus createMask(ImagePlus image, String title, double lower, double upper) {
        ImageStack sourceStack = image.getStack();
        ImageStack maskStack = new ImageStack(image.getWidth(), image.getHeight());
        int nSlices = sourceStack.getSize();
        ImageProcessor[] masks = new ImageProcessor[nSlices];

        if (nSlices < SLICE_PARALLEL_THRESHOLD || ParallelContext.isNested()) {
            for (int s = 1; s <= nSlices; s++) {
                masks[s - 1] = createMaskProcessor(sourceStack.getProcessor(s), lower, upper);
            }
        } else {
            createMasksInParallel(sourceStack, masks, lower, upper);
        }

        for (int s = 1; s <= nSlices; s++) {
            maskStack.addSlice(sourceStack.getSliceLabel(s), masks[s - 1]);
        }

        ImagePlus maskImage = new ImagePlus(title, maskStack);
        copyDimensions(image, maskImage);
        return maskImage;
    }

    private static void createMasksInParallel(
            final ImageStack sourceStack,
            final ImageProcessor[] masks,
            final double lower,
            final double upper) {
        int nSlices = sourceStack.getSize();
        int nThreads = Math.min(nSlices, Runtime.getRuntime().availableProcessors());
        ExecutorService slicePool = Executors.newFixedThreadPool(nThreads);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        try {
            for (int s = 1; s <= nSlices; s++) {
                final int slice = s;
                futures.add(slicePool.submit(new Runnable() {
                    @Override public void run() {
                        ParallelContext.enterParallel();
                        try {
                            masks[slice - 1] = createMaskProcessor(sourceStack.getProcessor(slice), lower, upper);
                        } finally {
                            ParallelContext.exitParallel();
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        } finally {
            slicePool.shutdown();
        }
    }

    private static ImageProcessor createMaskProcessor(ImageProcessor source, double lower, double upper) {
        Object pixels = source.getPixels();
        int width = source.getWidth();
        int height = source.getHeight();
        if (pixels instanceof byte[]) {
            return maskFromByte(width, height, (byte[]) pixels, lower, upper);
        }
        if (pixels instanceof short[]) {
            return maskFromShort(width, height, (short[]) pixels, lower, upper);
        }
        if (pixels instanceof float[]) {
            return maskFromFloat(width, height, (float[]) pixels, lower, upper);
        }
        return maskFromProcessor(source, lower, upper);
    }

    private static ByteProcessor maskFromByte(
            int width,
            int height,
            byte[] input,
            double lower,
            double upper) {
        byte[] output = new byte[input.length];
        int lo = lowerByteBound(lower);
        int hi = upperByteBound(upper);
        if (lo <= hi) {
            for (int i = 0; i < input.length; i++) {
                int value = input[i] & 0xff;
                if (value >= lo && value <= hi) {
                    output[i] = (byte) 255;
                }
            }
        }
        return new ByteProcessor(width, height, output, null);
    }

    private static ByteProcessor maskFromShort(
            int width,
            int height,
            short[] input,
            double lower,
            double upper) {
        byte[] output = new byte[input.length];
        int lo = lowerShortBound(lower);
        int hi = upperShortBound(upper);
        if (lo <= hi) {
            for (int i = 0; i < input.length; i++) {
                int value = input[i] & 0xffff;
                if (value >= lo && value <= hi) {
                    output[i] = (byte) 255;
                }
            }
        }
        return new ByteProcessor(width, height, output, null);
    }

    private static ByteProcessor maskFromFloat(
            int width,
            int height,
            float[] input,
            double lower,
            double upper) {
        byte[] output = new byte[input.length];
        if (isFinite(lower) && isFinite(upper) && lower <= upper) {
            for (int i = 0; i < input.length; i++) {
                float value = input[i];
                if (isFinite(value) && value >= lower && value <= upper) {
                    output[i] = (byte) 255;
                }
            }
        }
        return new ByteProcessor(width, height, output, null);
    }

    private static ByteProcessor maskFromProcessor(ImageProcessor source, double lower, double upper) {
        int width = source.getWidth();
        int height = source.getHeight();
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
        return mask;
    }

    private static ShootoutContext buildContext(ImagePlus processed) {
        Range range = measureRange(processed);
        int[] histogram = buildHistogram(processed, range);
        return new ShootoutContext(
                processed,
                histogram,
                range.minimum,
                range.maximum,
                containsFloatProcessor(processed));
    }

    private static Range measureRange(ImagePlus image) {
        ImageStack stack = image.getStack();
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        boolean foundFiniteRange = false;
        for (int s = 1; s <= stack.getSize(); s++) {
            ImageProcessor processor = stack.getProcessor(s);
            processor.resetRoi();
            ImageStatistics stats;
            try {
                stats = ImageStatistics.getStatistics(processor, Measurements.MIN_MAX, null);
            } catch (RuntimeException ex) {
                return measureRangeFallback(image);
            }
            if (!hasUsableRange(stats.min, stats.max)) {
                continue;
            }
            foundFiniteRange = true;
            if (stats.min < minimum) {
                minimum = stats.min;
            }
            if (stats.max > maximum) {
                maximum = stats.max;
            }
        }
        if (!foundFiniteRange) {
            return measureRangeFallback(image);
        }
        return new Range(minimum, maximum);
    }

    private static Range measureRangeFallback(ImagePlus image) {
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

    private static int[] buildHistogram(ImagePlus image, Range range) {
        int[] histogram = new int[HISTOGRAM_BINS];
        ImageStack stack = image.getStack();
        for (int s = 1; s <= stack.getSize(); s++) {
            ImageProcessor processor = stack.getProcessor(s);
            processor.resetRoi();
            Object pixels = processor.getPixels();
            if (pixels instanceof float[]) {
                addFloatHistogram(histogram, (float[]) pixels, range);
            } else if (pixels instanceof byte[] || pixels instanceof short[] || pixels instanceof int[]) {
                try {
                    addNativeHistogram(histogram, processor.getHistogram(), range);
                } catch (RuntimeException ex) {
                    addProcessorHistogram(histogram, processor, range);
                }
            } else {
                addProcessorHistogram(histogram, processor, range);
            }
        }
        return histogram;
    }

    private static void addNativeHistogram(int[] histogram, int[] nativeHistogram, Range range) {
        if (nativeHistogram == null) {
            return;
        }
        for (int i = 0; i < nativeHistogram.length; i++) {
            int count = nativeHistogram[i];
            if (count != 0) {
                histogram[binFor(i, range)] += count;
            }
        }
    }

    private static void addFloatHistogram(int[] histogram, float[] pixels, Range range) {
        for (int i = 0; i < pixels.length; i++) {
            float value = pixels[i];
            if (isFinite(value)) {
                histogram[binFor(value, range)]++;
            }
        }
    }

    private static void addProcessorHistogram(int[] histogram, ImageProcessor processor, Range range) {
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

    private static int binFor(double value, Range range) {
        if (range.maximum <= range.minimum) {
            return 0;
        }
        int bin = (int) Math.floor((value - range.minimum) * (HISTOGRAM_BINS - 1.0)
                / (range.maximum - range.minimum));
        if (bin < 0) {
            return 0;
        }
        if (bin >= HISTOGRAM_BINS) {
            return HISTOGRAM_BINS - 1;
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
        if (bin >= HISTOGRAM_BINS - 1) {
            return range.maximum;
        }
        return range.minimum + (range.maximum - range.minimum) * bin / (HISTOGRAM_BINS - 1.0);
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

    private static boolean usesGrid(ShootoutSettings.ThresholdMode mode) {
        return mode == ShootoutSettings.ThresholdMode.AUTO_GRID;
    }

    private static String fixedLabel(double value) {
        if (value == Math.rint(value)) {
            return "Fixed " + Long.toString(Math.round(value));
        }
        return "Fixed " + Double.toString(value);
    }

    private static String gridLabel(double value) {
        return "Grid " + shortNumber(value);
    }

    private static String shortNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1000000000000000.0) {
            return Long.toString(Math.round(value));
        }
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        while (formatted.indexOf('.') >= 0 && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
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

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean hasUsableRange(double minimum, double maximum) {
        return isFinite(minimum)
                && isFinite(maximum)
                && minimum <= maximum
                && minimum != Double.MAX_VALUE
                && maximum != -Double.MAX_VALUE;
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

    private static int lowerByteBound(double value) {
        if (!isFinite(value)) {
            return 256;
        }
        if (value <= 0.0) {
            return 0;
        }
        if (value > 255.0) {
            return 256;
        }
        return (int) Math.ceil(value);
    }

    private static int upperByteBound(double value) {
        if (!isFinite(value)) {
            return -1;
        }
        if (value < 0.0) {
            return -1;
        }
        if (value >= 255.0) {
            return 255;
        }
        return (int) Math.floor(value);
    }

    private static int lowerShortBound(double value) {
        if (!isFinite(value)) {
            return 65536;
        }
        if (value <= 0.0) {
            return 0;
        }
        if (value > 65535.0) {
            return 65536;
        }
        return (int) Math.ceil(value);
    }

    private static int upperShortBound(double value) {
        if (!isFinite(value)) {
            return -1;
        }
        if (value < 0.0) {
            return -1;
        }
        if (value >= 65535.0) {
            return 65535;
        }
        return (int) Math.floor(value);
    }

    private static void closeProcessed(ShootoutContext context) {
        if (context != null) {
            closeImageQuietly(context.processed);
        }
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
