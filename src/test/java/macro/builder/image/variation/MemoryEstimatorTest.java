package macro.builder.image.variation;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MemoryEstimatorTest {

    private static final long GIB = 1L << 30;

    @Test
    public void projectedBytesMatchesFormulaWithinOnePercent() {
        // 1024×1024×100-slice 16-bit × 5 variants × 1.3 overhead.
        // Single ShortProcessor shared across all 100 slices keeps the test
        // allocation at ~2 MiB rather than the full 200 MiB working set.
        ImagePlus imp = sharedShortStack(1024, 1024, 100);

        long expected = 1024L * 1024L * 100L * 2L * 5L * 1300L / 1000L;
        MemoryEstimate est = MemoryEstimator.estimate(imp, 5, 32L * GIB);

        assertEquals(1024L * 1024L * 100L * 2L, est.sourceBytes);
        long delta = Math.abs(est.projectedBytes - expected);
        assertTrue("projectedBytes " + est.projectedBytes
                        + " differs from " + expected + " by more than 1%",
                delta * 100L <= expected);
    }

    @Test
    public void exceedsBudgetTrueOn4GibHeap() {
        ImagePlus imp = sharedShortStack(1024, 1024, 100);
        MemoryEstimate est = MemoryEstimator.estimate(imp, 5, 4L * GIB);
        assertTrue("Projected ~1.36 GiB on 4 GiB heap should exceed 25% budget",
                est.exceedsBudget);
    }

    @Test
    public void exceedsBudgetFalseOn32GibHeap() {
        ImagePlus imp = sharedShortStack(1024, 1024, 100);
        MemoryEstimate est = MemoryEstimator.estimate(imp, 5, 32L * GIB);
        assertFalse("Projected ~1.36 GiB on 32 GiB heap should fit in 25% budget",
                est.exceedsBudget);
    }

    @Test
    public void rgb24bitImageCountsFourBytesPerPixel() {
        ImagePlus imp = sharedColorStack(64, 64, 1);
        MemoryEstimate est = MemoryEstimator.estimate(imp, 1, 32L * GIB);
        assertEquals(64L * 64L * 1L * 4L, est.sourceBytes);
    }

    @Test
    public void thirtyTwoBitFloatCountsFourBytesPerPixel() {
        ImagePlus imp = sharedFloatStack(32, 32, 4);
        MemoryEstimate est = MemoryEstimator.estimate(imp, 1, 32L * GIB);
        assertEquals(32L * 32L * 4L * 4L, est.sourceBytes);
    }

    @Test
    public void eightBitImageCountsOneBytePerPixel() {
        ImagePlus imp = sharedByteStack(16, 16, 8);
        MemoryEstimate est = MemoryEstimator.estimate(imp, 1, 32L * GIB);
        assertEquals(16L * 16L * 8L * 1L, est.sourceBytes);
    }

    @Test
    public void headroomFractionMatchesProjectedOverHeap() {
        ImagePlus imp = sharedShortStack(1024, 1024, 100);
        long heap = 8L * GIB;
        MemoryEstimate est = MemoryEstimator.estimate(imp, 5, heap);
        double expected = (double) est.projectedBytes / (double) heap;
        assertEquals(expected, est.headroomFraction, 1e-12);
    }

    @Test
    public void humanReadableContainsVariantCountTotalGiBPercentageAndVerdict() {
        ImagePlus imp = sharedShortStack(1024, 1024, 100);
        MemoryEstimate est = MemoryEstimator.estimate(imp, 9, 32L * GIB);
        String msg = est.humanReadable;

        assertTrue("missing variant count: " + msg, msg.contains("9 variants"));
        assertTrue("missing GiB total: " + msg, msg.contains("GiB"));
        assertTrue("missing percentage: " + msg, msg.matches(".*\\b\\d+%.*"));
        assertTrue("missing heap mention: " + msg, msg.contains("heap"));
        // 9 × 1024² × 100 × 2 bytes × 1.3 = ~2.44 GiB out of 32 GiB → ~8% → fits
        assertFalse(est.exceedsBudget);
        assertTrue("missing fits-in-budget verdict: " + msg, msg.contains("fits"));
    }

    @Test
    public void humanReadableMarksRoiModeWhenExceedingBudget() {
        ImagePlus imp = sharedShortStack(1024, 1024, 100);
        MemoryEstimate est = MemoryEstimator.estimate(imp, 9, 4L * GIB);
        assertTrue(est.exceedsBudget);
        assertTrue("missing ROI mode verdict: " + est.humanReadable,
                est.humanReadable.contains("ROI mode required"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroVariantCount() {
        MemoryEstimator.estimate(sharedShortStack(4, 4, 1), 0, GIB);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveHeap() {
        MemoryEstimator.estimate(sharedShortStack(4, 4, 1), 1, 0L);
    }

private static ImagePlus sharedShortStack(int w, int h, int slices) {
        ShortProcessor sp = new ShortProcessor(w, h);
        ImageStack stack = new ImageStack(w, h);
        for (int i = 0; i < slices; i++) stack.addSlice(sp);
        return new ImagePlus("short-test", stack);
    }

    private static ImagePlus sharedByteStack(int w, int h, int slices) {
        ByteProcessor bp = new ByteProcessor(w, h);
        ImageStack stack = new ImageStack(w, h);
        for (int i = 0; i < slices; i++) stack.addSlice(bp);
        return new ImagePlus("byte-test", stack);
    }

    private static ImagePlus sharedFloatStack(int w, int h, int slices) {
        FloatProcessor fp = new FloatProcessor(w, h);
        ImageStack stack = new ImageStack(w, h);
        for (int i = 0; i < slices; i++) stack.addSlice(fp);
        return new ImagePlus("float-test", stack);
    }

    private static ImagePlus sharedColorStack(int w, int h, int slices) {
        ColorProcessor cp = new ColorProcessor(w, h);
        ImageStack stack = new ImageStack(w, h);
        for (int i = 0; i < slices; i++) stack.addSlice(cp);
        return new ImagePlus("color-test", stack);
    }
}
