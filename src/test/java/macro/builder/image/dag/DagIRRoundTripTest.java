package macro.builder.image.dag;

import macro.builder.image.FilterMacroParser.OpType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DagIRRoundTripTest {

    @Test
    public void serializesAndDeserializesDagJson() {
        DagIR dag = sampleDag();

        String json = DagIRSerializer.toJson(dag);
        DagIR roundTripped = DagIRSerializer.fromJson(json);

        assertEquals(dag, roundTripped);
    }

    @Test
    public void loadsEmbeddedDagFromEmittedIjm() {
        DagIR dag = sampleDag();

        String ijm = DagToIjmEmitter.emit(dag);
        DagIR embedded = IjmToDagLoader.loadEmbeddedDag(ijm);

        assertNotNull(embedded);
        assertEquals(dag, embedded);
        assertEquals(dag, IjmToDagLoader.load(ijm));
        assertTrue(ijm.indexOf("// @ihf-dag v1 executionTier=native") >= 0);
        assertTrue(ijm.indexOf("run(\"Gaussian Blur...\", \"sigma=2 stack\");") >= 0);
    }

    private static DagIR sampleDag() {
        DagLine gaussianLine = new DagLine("line_1",
                Collections.singletonList(new DagNode(
                        "n1", OpType.GAUSSIAN_BLUR, "sigma=2 stack")));
        DagLine medianLine = new DagLine("line_2",
                Collections.singletonList(new DagNode(
                        "n2", OpType.MEDIAN, "radius=2 stack")));
        Combiner combiner = new Combiner("combined",
                CombinerOp.ADD,
                Arrays.asList("line_1", "line_2"));

        return new DagIR(1,
                Arrays.asList(gaussianLine, medianLine),
                Collections.singletonList(combiner),
                "combined",
                "native");
    }
}
