package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
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

    private static void fill(ShortProcessor processor, int value) {
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                processor.set(x, y, value);
            }
        }
    }
}
