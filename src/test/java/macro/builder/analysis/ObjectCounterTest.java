package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import macro.builder.analysis.ObjectCounter.CountSummary;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ObjectCounterTest {

    @Test
    public void countsSeparated2DObjects() {
        ByteProcessor mask = new ByteProcessor(6, 5);
        set(mask, 1, 1);
        set(mask, 1, 2);
        set(mask, 4, 1);
        set(mask, 4, 2);
        set(mask, 5, 2);

        CountSummary summary = ObjectCounter.count(new ImagePlus("mask", mask), settings(CountingMode.PARTICLES_2D, 0, Double.POSITIVE_INFINITY));

        assertEquals(2, summary.count);
        assertEquals(2.5, summary.meanSize, 0.0001);
        assertEquals(5.0, summary.totalForeground, 0.0001);
        assertEquals(5.0 / 30.0, summary.coverage, 0.0001);
    }

    @Test
    public void detectsAcceptedObjectCentroidsAndPixels() {
        ByteProcessor mask = new ByteProcessor(6, 5);
        set(mask, 1, 1);
        set(mask, 1, 2);
        set(mask, 4, 1);
        set(mask, 4, 2);
        set(mask, 5, 2);

        List<DetectedObject> objects = ObjectCounter.detect(
                new ImagePlus("mask", mask),
                settings(CountingMode.PARTICLES_2D, 0, Double.POSITIVE_INFINITY));

        assertEquals(2, objects.size());
        assertEquals(2, objects.get(0).area);
        assertEquals(1.0, objects.get(0).centroidX, 0.0001);
        assertEquals(1.5, objects.get(0).centroidY, 0.0001);
        assertEquals(3, objects.get(1).pixels.length);
    }

    @Test
    public void filtersSmall2DObjects() {
        ByteProcessor mask = new ByteProcessor(6, 5);
        set(mask, 1, 1);
        set(mask, 1, 2);
        set(mask, 2, 2);
        set(mask, 5, 4);

        CountSummary summary = ObjectCounter.count(new ImagePlus("mask", mask), settings(CountingMode.PARTICLES_2D, 2, Double.POSITIVE_INFINITY));

        assertEquals(1, summary.count);
        assertEquals(3.0, summary.meanSize, 0.0001);
        assertEquals(3.0, summary.totalForeground, 0.0001);
        assertEquals(3.0 / 30.0, summary.coverage, 0.0001);
    }

    @Test
    public void filtersLarge2DObjects() {
        ByteProcessor mask = new ByteProcessor(6, 5);
        set(mask, 1, 1);
        set(mask, 1, 2);
        set(mask, 2, 2);
        set(mask, 5, 4);

        CountSummary summary = ObjectCounter.count(new ImagePlus("mask", mask), settings(CountingMode.PARTICLES_2D, 0, 2));

        assertEquals(1, summary.count);
        assertEquals(1.0, summary.meanSize, 0.0001);
        assertEquals(1.0, summary.totalForeground, 0.0001);
        assertEquals(1.0 / 30.0, summary.coverage, 0.0001);
    }

    @Test
    public void countsOne3DObjectSpanningSlices() {
        ImagePlus mask = stack(5, 5, 3);
        set(mask, 2, 2, 0);
        set(mask, 2, 2, 1);
        set(mask, 3, 3, 2);

        CountSummary summary = ObjectCounter.count(mask, settings(CountingMode.OBJECTS_3D, 0, Double.POSITIVE_INFINITY));

        assertEquals(1, summary.count);
        assertEquals(3.0, summary.meanSize, 0.0001);
        assertEquals(3.0, summary.totalForeground, 0.0001);
        assertEquals(3.0 / 75.0, summary.coverage, 0.0001);
    }

    @Test
    public void countsSeparated3DObjects() {
        ImagePlus mask = stack(5, 5, 3);
        set(mask, 0, 0, 0);
        set(mask, 0, 1, 0);
        set(mask, 4, 4, 2);
        set(mask, 4, 3, 2);

        CountSummary summary = ObjectCounter.count(mask, settings(CountingMode.OBJECTS_3D, 0, Double.POSITIVE_INFINITY));

        assertEquals(2, summary.count);
        assertEquals(2.0, summary.meanSize, 0.0001);
        assertEquals(4.0, summary.totalForeground, 0.0001);
        assertEquals(4.0 / 75.0, summary.coverage, 0.0001);
    }

    private static ShootoutSettings settings(CountingMode countingMode, double minSize, double maxSize) {
        return new ShootoutSettings(
                countingMode,
                ThresholdMode.AUTO_METHODS,
                Collections.<String>emptyList(),
                Collections.<Double>emptyList(),
                minSize,
                maxSize,
                true);
    }

    private static ImagePlus stack(int width, int height, int depth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice(new ByteProcessor(width, height));
        }
        return new ImagePlus("stack", stack);
    }

    private static void set(ByteProcessor processor, int x, int y) {
        processor.set(x, y, 255);
    }

    private static void set(ImagePlus image, int x, int y, int z) {
        image.getStack().getProcessor(z + 1).set(x, y, 255);
    }
}
