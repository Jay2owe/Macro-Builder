package macro.builder.api;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import macro.builder.analysis.DetectedObject;
import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutRun;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MacroBuilderCountingApiTest {

    @Test
    public void publicApiRunsSingleImageThresholdShootout() {
        ByteProcessor processor = new ByteProcessor(6, 1);
        processor.set(1, 0, 200);
        processor.set(2, 0, 210);
        processor.set(4, 0, 250);
        ImagePlus source = new ImagePlus("source", processor);
        ShootoutSettings settings = fixedSettings(200.0);

        List<ShootoutResult> rows = MacroBuilderCounting.runShootout(source, "", settings);
        try {
            assertEquals(1, rows.size());
            assertTrue(rows.get(0).isSuccess());
            assertEquals("Fixed 200", rows.get(0).variant);
            assertEquals(2, rows.get(0).countSummary.count);
            assertEquals(3.0, rows.get(0).countSummary.totalForeground, 0.0001);
            assertNotNull(rows.get(0).maskPreview);
        } finally {
            MacroBuilderCounting.closeMaskPreviews(rows);
        }
    }

    @Test
    public void publicApiRunsOneVariantFromRetainedContext() {
        ByteProcessor processor = new ByteProcessor(3, 1);
        processor.set(1, 0, 255);
        ShootoutSettings settings = fixedSettings(100.0);
        ShootoutRun run = MacroBuilderCounting.runShootoutWithContext(
                new ImagePlus("source", processor),
                "",
                settings);

        try {
            ShootoutResult row = MacroBuilderCounting.runOneVariant(
                    run,
                    settings,
                    "Fixed 100",
                    Double.valueOf(100.0));

            assertTrue(row.isSuccess());
            assertEquals(1, row.countSummary.count);
        } finally {
            MacroBuilderCounting.closeShootoutRun(run);
        }
    }

    @Test
    public void publicApiCountsAndDetectsBinaryObjects() {
        ByteProcessor processor = new ByteProcessor(5, 1);
        processor.set(0, 0, 255);
        processor.set(2, 0, 255);
        processor.set(3, 0, 255);
        ImagePlus mask = new ImagePlus("mask", processor);

        ObjectCounter.CountSummary count = MacroBuilderCounting.countObjects(mask, fixedSettings(1.0));
        List<DetectedObject> objects = MacroBuilderCounting.detectObjects(mask, fixedSettings(1.0));

        assertEquals(2, count.count);
        assertEquals(3.0, count.totalForeground, 0.0001);
        assertEquals(2, objects.size());
        assertEquals(2, objects.get(1).area);
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
}
