package macro.builder.analysis;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class LiveMaskBuilderDefaultPinTest {

    @Test
    public void buildThresholdsEverySliceForFullStackCounting() {
        ImagePlus source = twoSliceSource();

        ImagePlus mask = LiveMaskBuilder.build(source, 10.0, 100.0);
        ObjectCounter.CountSummary count = ObjectCounter.count(mask, settings());

        assertEquals(2, mask.getStackSize());
        assertEquals(2, count.count);
        assertEquals(255, mask.getStack().getProcessor(1).get(0, 0));
        assertEquals(255, mask.getStack().getProcessor(2).get(3, 3));
    }

    private static ImagePlus twoSliceSource() {
        ImageStack stack = new ImageStack(4, 4);
        ByteProcessor sliceOne = new ByteProcessor(4, 4);
        ByteProcessor sliceTwo = new ByteProcessor(4, 4);
        sliceOne.set(0, 0, 50);
        sliceTwo.set(3, 3, 75);
        stack.addSlice("slice 1", sliceOne);
        stack.addSlice("slice 2", sliceTwo);
        return new ImagePlus("two-slice", stack);
    }

    private static ShootoutSettings settings() {
        return new ShootoutSettings(
                ShootoutSettings.CountingMode.OBJECTS_3D,
                ShootoutSettings.ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(10.0)),
                ShootoutSettings.DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true);
    }
}
