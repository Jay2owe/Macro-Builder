package macro.builder.image;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import macro.builder.image.dag.Combiner;
import macro.builder.image.dag.CombinerOp;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.dag.DagRejectedException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FilterExecutorChannelDagTest {

    @Test
    public void subtractsAuxiliaryChannelFromPrimaryChannel() throws Exception {
        ImagePlus source = twoChannelHyperstack();
        DagIR dag = subtractDag(1, 2);

        ImagePlus result = FilterExecutor.runDagThreadSafe(source, dag);

        assertEquals(1, result.getNChannels());
        assertEquals(2, result.getNSlices());
        assertEquals(2, result.getNFrames());
        assertEquals(4, result.getStackSize());
        for (int s = 1; s <= result.getStackSize(); s++) {
            assertEquals(7.0f, result.getStack().getProcessor(s).getf(0), 0.0001f);
            assertEquals(14.0f, result.getStack().getProcessor(s).getf(1), 0.0001f);
        }
    }

    @Test
    public void rejectsUnavailableBranchSourceChannel() throws Exception {
        try {
            FilterExecutor.runDagThreadSafe(twoChannelHyperstack(), subtractDag(1, 3));
            fail("Expected unavailable channel to be rejected");
        } catch (DagRejectedException e) {
            assertTrue(e.getMessage().indexOf("Branch source channel C3 is not available; image has C=2") >= 0);
        }
    }

    private static DagIR subtractDag(int firstChannel, int secondChannel) {
        return new DagIR(1,
                1,
                Arrays.asList(
                        new DagLine("line_A", Collections.<DagNode>emptyList(), firstChannel),
                        new DagLine("line_B", Collections.<DagNode>emptyList(), secondChannel)),
                Collections.singletonList(new Combiner("combined",
                        CombinerOp.SUBTRACT,
                        Arrays.asList("line_A", "line_B"))),
                "combined",
                "native");
    }

    private static ImagePlus twoChannelHyperstack() {
        int width = 2;
        int height = 1;
        int channels = 2;
        int slices = 2;
        int frames = 2;
        ImageStack stack = new ImageStack(width, height);
        for (int t = 1; t <= frames; t++) {
            for (int z = 1; z <= slices; z++) {
                stack.addSlice("C1 Z" + z + " T" + t, processor(width, height, 10.0f, 20.0f));
                stack.addSlice("C2 Z" + z + " T" + t, processor(width, height, 3.0f, 6.0f));
            }
        }
        ImagePlus imp = new ImagePlus("two-channel", stack);
        imp.setDimensions(channels, slices, frames);
        imp.setOpenAsHyperStack(true);
        return imp;
    }

    private static FloatProcessor processor(int width, int height, float first, float second) {
        FloatProcessor fp = new FloatProcessor(width, height);
        fp.setf(0, first);
        fp.setf(1, second);
        return fp;
    }
}
