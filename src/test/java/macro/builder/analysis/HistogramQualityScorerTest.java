package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistogramQualityScorerTest {

    @Test
    public void bimodalValleyScoresHighSeparation() {
        int[] histogram = bimodalGaussian(64.0, 192.0, 6.0);
        ShootoutContext context = context(histogram, 0.0, 255.0, false);

        double separation = HistogramQualityScorer.separation(histogram, 128.0, context);

        assertTrue("Expected a strong valley split, got " + separation, separation > 0.95);
    }

    @Test
    public void uniformHistogramScoresLowSeparation() {
        int[] histogram = new int[256];
        Arrays.fill(histogram, 100);
        ShootoutContext context = context(histogram, 0.0, 255.0, false);

        for (int threshold = 32; threshold <= 224; threshold += 32) {
            double separation = HistogramQualityScorer.separation(histogram, threshold, context);
            assertTrue("Uniform threshold " + threshold + " scored " + separation, separation < 0.2);
        }
    }

    @Test
    public void singleGaussianScoresLowDistinctness() {
        int[] histogram = gaussian(128.0, 34.0);
        ShootoutContext context = context(histogram, 0.0, 255.0, false);

        double distinctness = HistogramQualityScorer.distinctness(histogram, 128.0, context);

        assertTrue("Single group scored " + distinctness, distinctness < 0.45);
    }

    @Test
    public void emptyAndUnsplitHistogramsScoreZero() {
        int[] empty = new int[256];
        ShootoutContext emptyContext = context(empty, 0.0, 255.0, false);
        assertEquals(0.0, HistogramQualityScorer.separation(empty, 128.0, emptyContext), 1e-12);
        assertEquals(0.0, HistogramQualityScorer.distinctness(empty, 128.0, emptyContext), 1e-12);

        int[] singleBin = new int[256];
        singleBin[12] = 100;
        ShootoutContext singleContext = context(singleBin, 0.0, 255.0, false);
        assertEquals(0.0, HistogramQualityScorer.separation(singleBin, 0.0, singleContext), 1e-12);
        assertEquals(0.0, HistogramQualityScorer.distinctness(singleBin, 300.0, singleContext), 1e-12);
    }

    @Test
    public void floatContextUsesTheProvidedBinnedHistogram() {
        int[] histogram = bimodalGaussian(40.0, 220.0, 4.0);
        ShootoutContext context = context(histogram, -2.0, 2.0, true);

        double separation = HistogramQualityScorer.separation(histogram, 0.0, context);

        assertTrue(separation > 0.95);
    }

    @Test
    public void runnerStoresQualityScoresOnSuccessfulThresholdRows() {
        ByteProcessor processor = new ByteProcessor(20, 10);
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                processor.set(x, y, x < 10 ? 40 : 220);
            }
        }
        ShootoutSettings settings = new ShootoutSettings(
                ShootoutSettings.CountingMode.PARTICLES_2D,
                ShootoutSettings.ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(128.0)),
                0.0,
                Double.POSITIVE_INFINITY,
                true);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                new ImagePlus("two groups", processor),
                "",
                settings);

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).separationScore > 0.95);
        assertTrue(rows.get(0).distinctnessScore > 0.95);
    }

    private static ShootoutContext context(
            int[] histogram,
            double minimum,
            double maximum,
            boolean isFloat) {
        return new ShootoutContext(
                new ImagePlus("histogram", new ByteProcessor(1, 1)),
                histogram,
                minimum,
                maximum,
                isFloat);
    }

    private static int[] bimodalGaussian(double leftMean, double rightMean, double sigma) {
        int[] histogram = new int[256];
        int[] left = gaussian(leftMean, sigma);
        int[] right = gaussian(rightMean, sigma);
        for (int i = 0; i < histogram.length; i++) {
            histogram[i] = left[i] + right[i];
        }
        return histogram;
    }

    private static int[] gaussian(double mean, double sigma) {
        int[] histogram = new int[256];
        for (int i = 0; i < histogram.length; i++) {
            double z = (i - mean) / sigma;
            histogram[i] = (int) Math.round(10000.0 * Math.exp(-0.5 * z * z));
        }
        return histogram;
    }
}
