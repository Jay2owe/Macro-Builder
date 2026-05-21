package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import macro.builder.image.dag.Combiner;
import macro.builder.image.dag.CombinerOp;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagToIjmEmitter;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ThresholdShootoutRunnerTest {

    @Test
    public void fixedThresholdUsesNative16BitIntensity() {
        ShortProcessor processor = new ShortProcessor(6, 3);
        fill(processor, 1000);
        processor.set(1, 1, 2000);
        processor.set(2, 1, 2100);
        processor.set(4, 1, 3000);

        ImagePlus source = new ImagePlus("source", processor);
        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(2000.0)),
                0.0,
                Double.POSITIVE_INFINITY,
                true);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(source, "", settings);

        assertEquals(1, rows.size());
        ShootoutResult row = rows.get(0);
        assertTrue(row.isSuccess());
        assertEquals("Fixed 2000", row.variant);
        assertEquals(2000.0, row.thresholdValue.doubleValue(), 0.0001);
        assertEquals(1000.0, row.imageMinimum, 0.0001);
        assertEquals(3000.0, row.imageMaximum, 0.0001);
        assertEquals(2, row.countSummary.count);
        assertEquals(3.0, row.countSummary.totalForeground, 0.0001);
        assertNotNull(row.maskPreview);
        assertEquals(0, row.maskPreview.getProcessor().get(0, 0));
        assertEquals(255, row.maskPreview.getProcessor().get(1, 1));
    }

    @Test
    public void macroRunsOnDuplicateAndLeavesSourceUnchanged() {
        ByteProcessor processor = new ByteProcessor(3, 1);
        processor.set(0, 0, 1);
        processor.set(1, 0, 0);
        processor.set(2, 0, 0);

        ImagePlus source = new ImagePlus("source", processor);
        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(101.0)),
                0.0,
                Double.POSITIVE_INFINITY,
                true);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                source,
                "run(\"Add...\", \"value=100\");",
                settings);

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isSuccess());
        assertEquals(1, rows.get(0).countSummary.count);
        assertEquals(1, source.getProcessor().get(0, 0));
        assertEquals(0, source.getProcessor().get(1, 0));
        assertEquals(0, source.getProcessor().get(2, 0));
    }

    @Test
    public void failedThresholdMethodReturnsFailedRowAndKeepsLaterRows() {
        ByteProcessor processor = new ByteProcessor(4, 1);
        processor.set(0, 0, 0);
        processor.set(1, 0, 0);
        processor.set(2, 0, 255);
        processor.set(3, 0, 255);

        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.AUTO_METHODS,
                Arrays.asList("Otsu", "DefinitelyMissing", "Default"),
                Collections.<Double>emptyList(),
                0.0,
                Double.POSITIVE_INFINITY,
                true);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(new ImagePlus("source", processor), "", settings);

        assertEquals(3, rows.size());
        assertTrue(rows.get(0).isSuccess());
        assertEquals(ShootoutResult.Status.FAILED, rows.get(1).status);
        assertEquals("DefinitelyMissing", rows.get(1).variant);
        assertTrue(rows.get(2).isSuccess());
    }

    @Test
    public void nonDagMacroRunsOnSelectedPrimaryChannelOnly() {
        ImagePlus source = twoChannelImage("source",
                new int[][]{
                        {5, 5, 5},
                        {5, 5, 5},
                        {5, 5, 5}
                },
                new int[][]{
                        {5, 5, 5},
                        {5, 200, 5},
                        {5, 5, 5}
                });

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                source,
                "",
                fixedSettings(100.0),
                2,
                null);

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isSuccess());
        assertEquals(1, rows.get(0).countSummary.count);
    }

    @Test
    public void embeddedDagMacroStillReceivesAuxiliaryChannels() {
        ImagePlus source = twoChannelImage("source",
                new int[][]{
                        {0, 0, 0},
                        {0, 200, 0},
                        {0, 0, 0}
                },
                new int[][]{
                        {0, 0, 0},
                        {0, 200, 0},
                        {0, 0, 0}
                });
        DagIR dag = new DagIR(
                1,
                1,
                Arrays.asList(
                        new DagLine("line_A", Collections.emptyList(), 1),
                        new DagLine("line_B", Collections.emptyList(), 2)),
                Collections.singletonList(new Combiner(
                        "merge_AB",
                        CombinerOp.SUBTRACT,
                        Arrays.asList("line_A", "line_B"))),
                "merge_AB",
                "native");

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                source,
                DagToIjmEmitter.emit(dag),
                fixedSettings(100.0),
                1,
                null);

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isSuccess());
        assertEquals(0, rows.get(0).countSummary.count);
    }

    @Test
    public void typedByteMaskPathMatchesReferenceCount() {
        ByteProcessor processor = new ByteProcessor(3, 2);
        processor.set(0, 0, 0);
        processor.set(1, 0, 100);
        processor.set(2, 0, 101);
        processor.set(0, 1, 50);
        processor.set(1, 1, 200);
        processor.set(2, 1, 99);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                new ImagePlus("byte", processor),
                "",
                fixedSettings(100.0));

        assertEquals(1, rows.size());
        ShootoutResult row = rows.get(0);
        assertTrue(row.isSuccess());
        assertEquals(3.0, row.countSummary.totalForeground, 0.0001);
        assertEquals(255, row.maskPreview.getProcessor().get(1, 0));
        assertEquals(0, row.maskPreview.getProcessor().get(2, 1));
    }

    @Test
    public void typedShortMaskPathHandlesSyntheticStackInParallel() {
        ImageStack stack = new ImageStack(5, 3);
        for (int s = 0; s < 4; s++) {
            ShortProcessor processor = new ShortProcessor(5, 3);
            fill(processor, 1000);
            processor.set(1, 1, 2000 + s);
            processor.set(3, 1, 3000 + s);
            stack.addSlice("slice " + s, processor);
        }

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                new ImagePlus("short stack", stack),
                "",
                fixedSettings(2000.0));

        assertEquals(1, rows.size());
        ShootoutResult row = rows.get(0);
        assertTrue(row.isSuccess());
        assertEquals(8.0, row.countSummary.totalForeground, 0.0001);
        assertEquals(255, row.maskPreview.getStack().getProcessor(1).get(1, 1));
        assertEquals(0, row.maskPreview.getStack().getProcessor(1).get(0, 0));
        assertEquals(4, row.maskPreview.getStackSize());
    }

    @Test
    public void typedFloatMaskPathTreatsNonFinitePixelsAsBackground() {
        FloatProcessor processor = new FloatProcessor(4, 2);
        float[] pixels = (float[]) processor.getPixels();
        pixels[0] = Float.NaN;
        pixels[1] = Float.POSITIVE_INFINITY;
        pixels[2] = Float.NEGATIVE_INFINITY;
        pixels[3] = 1.5f;
        pixels[4] = 2.0f;
        pixels[5] = 3.0f;
        pixels[6] = 0.5f;
        pixels[7] = 1.0f;

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                new ImagePlus("float", processor),
                "",
                fixedSettings(2.0));

        assertEquals(1, rows.size());
        ShootoutResult row = rows.get(0);
        assertTrue(row.isSuccess());
        assertEquals(2.0, row.countSummary.totalForeground, 0.0001);
        assertEquals(0, row.maskPreview.getProcessor().get(0, 0));
        assertEquals(0, row.maskPreview.getProcessor().get(1, 0));
        assertEquals(255, row.maskPreview.getProcessor().get(0, 1));
        assertEquals(255, row.maskPreview.getProcessor().get(1, 1));
    }

    @Test
    public void contextRunRetainsProcessedImageAndCachedHistogram() {
        ByteProcessor processor = new ByteProcessor(2, 1);
        processor.set(0, 0, 1);
        processor.set(1, 0, 2);

        ShootoutRun run = new ThresholdShootoutRunner().runWithContext(
                new ImagePlus("source", processor),
                "run(\"Add...\", \"value=10\");",
                fixedSettings(11.0));

        try {
            assertNotNull(run.context);
            assertEquals(1, run.results.size());
            assertEquals(256, run.context.histogram.length);
            assertEquals(11.0, run.context.rangeMin, 0.0001);
            assertEquals(12.0, run.context.rangeMax, 0.0001);
            assertEquals(11, run.context.processed.getProcessor().get(0, 0));
            assertEquals(12, run.context.processed.getProcessor().get(1, 0));
        } finally {
            if (run.context != null) {
                run.context.processed.flush();
            }
        }
    }

    private static void fill(ShortProcessor processor, int value) {
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                processor.set(x, y, value);
            }
        }
    }

    private static ShootoutSettings fixedSettings(double threshold) {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(threshold)),
                0.0,
                Double.POSITIVE_INFINITY,
                true);
    }

    private static ImagePlus twoChannelImage(String title, int[][] channel1, int[][] channel2) {
        ImageStack stack = new ImageStack(channel1[0].length, channel1.length);
        stack.addSlice("C1", byteProcessor(channel1));
        stack.addSlice("C2", byteProcessor(channel2));
        ImagePlus image = new ImagePlus(title, stack);
        image.setDimensions(2, 1, 1);
        image.setOpenAsHyperStack(true);
        return image;
    }

    private static ByteProcessor byteProcessor(int[][] values) {
        int height = values.length;
        int width = values[0].length;
        ByteProcessor processor = new ByteProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                processor.set(x, y, values[y][x]);
            }
        }
        return processor;
    }
}
