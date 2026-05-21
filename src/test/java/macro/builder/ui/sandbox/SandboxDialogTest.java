package macro.builder.ui.sandbox;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class SandboxDialogTest {

    @Test
    public void variationGridSourceUsesPrimaryChannelAndCurrentTimepointOnly() {
        ImagePlus source = twoChannelHyperstack();
        source.setPosition(2, 1, 2);
        DagIR dag = new DagIR(2, 2,
                Collections.singletonList(new DagLine("line_A", Collections.<DagNode>emptyList(), 2)),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");

        ImagePlus raw = SandboxDialog.sourceForVariationGrid(source, dag);

        assertEquals(1, raw.getNChannels());
        assertEquals(2, raw.getNSlices());
        assertEquals(1, raw.getNFrames());
        assertEquals(2, raw.getStackSize());
        assertEquals(212.0f, raw.getStack().getProcessor(1).getf(0), 0.0001f);
        assertEquals(222.0f, raw.getStack().getProcessor(2).getf(0), 0.0001f);
    }

    private static ImagePlus twoChannelHyperstack() {
        int width = 1;
        int height = 1;
        ImageStack stack = new ImageStack(width, height);
        for (int t = 1; t <= 2; t++) {
            for (int z = 1; z <= 2; z++) {
                stack.addSlice("C1 Z" + z + " T" + t, processor(100 * t + 10 * z + 1));
                stack.addSlice("C2 Z" + z + " T" + t, processor(100 * t + 10 * z + 2));
            }
        }
        ImagePlus image = new ImagePlus("two-channel", stack);
        image.setDimensions(2, 2, 2);
        image.setOpenAsHyperStack(true);
        return image;
    }

    private static FloatProcessor processor(float value) {
        FloatProcessor processor = new FloatProcessor(1, 1);
        processor.setf(0, value);
        return processor;
    }
}
