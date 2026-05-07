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

    @Test
    public void serializesChannelMetadata() {
        DagIR dag = new DagIR(1, 2,
                Arrays.asList(
                        new DagLine("line_A", Collections.<DagNode>emptyList(), 2),
                        new DagLine("line_B", Collections.<DagNode>emptyList(), 3)),
                Collections.<Combiner>emptyList(),
                "line_A",
                "native");

        String json = DagIRSerializer.toJson(dag);
        DagIR roundTripped = DagIRSerializer.fromJson(json);

        assertEquals(dag, roundTripped);
        assertEquals(2, roundTripped.primaryChannel);
        assertEquals(3, roundTripped.lines.get(1).sourceChannel);
        assertTrue(json.indexOf("\"primaryChannel\":2") >= 0);
        assertTrue(json.indexOf("\"sourceChannel\":3") >= 0);
    }

    @Test
    public void oldDagJsonDefaultsToChannelOne() {
        String json = "{\"version\":1,\"executionTier\":\"native\","
                + "\"lines\":[{\"id\":\"line_A\",\"ops\":[]}],"
                + "\"combiners\":[],\"output\":\"line_A\"}";

        DagIR dag = DagIRSerializer.fromJson(json);

        assertEquals(1, dag.primaryChannel);
        assertEquals(1, dag.lines.get(0).sourceChannel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidPrimaryChannel() {
        String json = "{\"version\":1,\"primaryChannel\":0,\"executionTier\":\"native\","
                + "\"lines\":[{\"id\":\"line_A\",\"sourceChannel\":1,\"ops\":[]}],"
                + "\"combiners\":[],\"output\":\"line_A\"}";

        DagIRSerializer.fromJson(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidSourceChannel() {
        String json = "{\"version\":1,\"primaryChannel\":1,\"executionTier\":\"native\","
                + "\"lines\":[{\"id\":\"line_A\",\"sourceChannel\":0,\"ops\":[]}],"
                + "\"combiners\":[],\"output\":\"line_A\"}";

        DagIRSerializer.fromJson(json);
    }

    private static DagIR sampleDag() {
        DagLine gaussianLine = new DagLine("line_1",
                Collections.singletonList(new DagNode(
                        "n1", OpType.GAUSSIAN_BLUR, "sigma=2 stack")), 1);
        DagLine medianLine = new DagLine("line_2",
                Collections.singletonList(new DagNode(
                        "n2", OpType.MEDIAN, "radius=2 stack")), 1);
        Combiner combiner = new Combiner("combined",
                CombinerOp.ADD,
                Arrays.asList("line_1", "line_2"));

        return new DagIR(1,
                1,
                Arrays.asList(gaussianLine, medianLine),
                Collections.singletonList(combiner),
                "combined",
                "native");
    }
}
