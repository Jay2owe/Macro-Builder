package macro.builder.image.dag;

import macro.builder.image.FilterMacroParser.OpType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void serializesBranchNames() {
        DagIR dag = new DagIR(1, 1,
                Arrays.asList(
                        new DagLine("line_A", "Objects", Collections.<DagNode>emptyList(), 1),
                        new DagLine("line_B", "Marker mask", Collections.<DagNode>emptyList(), 2)),
                Collections.<Combiner>emptyList(),
                "line_A",
                "native");

        String json = DagIRSerializer.toJson(dag);
        DagIR roundTripped = DagIRSerializer.fromJson(json);

        assertEquals(dag, roundTripped);
        assertEquals("Objects", roundTripped.lines.get(0).name);
        assertEquals("Marker mask", roundTripped.lines.get(1).name);
        assertTrue(json.indexOf("\"name\":\"Marker mask\"") >= 0);
    }

    @Test
    public void emittedIjmDuplicatesBranchSourceChannel() {
        DagIR dag = new DagIR(1, 1,
                Arrays.asList(
                        new DagLine("line_A", Collections.<DagNode>emptyList(), 1),
                        new DagLine("line_B", Collections.<DagNode>emptyList(), 2)),
                Collections.singletonList(new Combiner("combined",
                        CombinerOp.SUBTRACT,
                        Arrays.asList("line_A", "line_B"))),
                "combined",
                "native");

        String ijm = DagToIjmEmitter.emit(dag);

        assertTrue(ijm.indexOf("getDimensions(width, height, channels, slices, frames);") >= 0);
        assertTrue(ijm.indexOf("getPixelSize(mb_unit, mb_pixel_width, mb_pixel_height, mb_voxel_depth);") >= 0);
        assertTrue(ijm.indexOf("function mb_restore_calibration()") >= 0);
        assertTrue(ijm.indexOf("line_range = \"channels=1-1 slices=1-\" + slices + \" frames=1-\" + frames;") >= 0);
        assertTrue(ijm.indexOf("line_range = \"channels=2-2 slices=1-\" + slices + \" frames=1-\" + frames;") >= 0);
        assertTrue(ijm.indexOf("run(\"Duplicate...\", \"title=line_B duplicate \" + line_range);") >= 0);
    }

    @Test
    public void readableIjmForSimpleLinearFilterOmitsInternalScaffolding() {
        DagLine line = new DagLine("line_A",
                Arrays.asList(
                        new DagNode("node_1", OpType.UNKNOWN, "selectImage(\"Macro Builder Recorder Sample\");"),
                        new DagNode("node_2", OpType.GAUSSIAN_BLUR, "sigma=2 stack"),
                        new DagNode("node_3", OpType.GAUSSIAN_BLUR, "sigma=7.04 stack")),
                1);
        DagIR dag = new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<Combiner>emptyList(),
                "line_A",
                "native");

        String ijm = DagToIjmEmitter.emitReadable(dag);

        assertEquals("run(\"Gaussian Blur...\", \"sigma=2 stack\");\n"
                + "run(\"Gaussian Blur...\", \"sigma=7.04 stack\");\n", ijm);
        assertEquals(ijm, DagToIjmEmitter.emitExecutable(dag));
        assertFalse(ijm.contains("@ihf-dag"));
        assertFalse(ijm.contains("source_id"));
        assertFalse(ijm.contains("Duplicate"));
        assertFalse(ijm.contains("mb_restore_calibration"));
        assertFalse(ijm.contains("UNKNOWN"));
    }

    @Test
    public void roundTripsCombinerWithMoreThanTwoInputs() {
        DagIR dag = new DagIR(1,
                Arrays.asList(
                        new DagLine("line_A", Collections.<DagNode>emptyList()),
                        new DagLine("line_B", Collections.<DagNode>emptyList()),
                        new DagLine("line_C", Collections.<DagNode>emptyList())),
                Collections.singletonList(new Combiner("combined",
                        CombinerOp.AND,
                        Arrays.asList("line_A", "line_B", "line_C"))),
                "combined",
                "native");

        String ijm = DagToIjmEmitter.emit(dag);
        DagIR roundTripped = DagIRSerializer.fromJson(DagIRSerializer.toJson(dag));
        DagIR embedded = IjmToDagLoader.loadEmbeddedDag(ijm);

        assertEquals(dag, roundTripped);
        assertEquals(dag, embedded);
        assertEquals(Arrays.asList("line_A", "line_B", "line_C"),
                roundTripped.combiners.get(0).inputs);
        assertTrue(ijm.indexOf("imageCalculator(\"AND create\", \"line_A\", \"line_B\");") >= 0);
        assertTrue(ijm.indexOf("rename(\"combined_1\");") >= 0);
        assertTrue(ijm.indexOf("setVoxelSize(mb_pixel_width, mb_pixel_height, mb_voxel_depth, mb_unit);") >= 0);
        assertTrue(ijm.indexOf("imageCalculator(\"AND create\", \"combined_1\", \"line_C\");") >= 0);
        assertTrue(ijm.indexOf("rename(\"combined\");") >= 0);
        assertTrue(ijm.indexOf("selectImage(\"combined\");") >= 0);
    }

    @Test
    public void oldDagJsonDefaultsToChannelOne() {
        String json = "{\"version\":1,\"executionTier\":\"native\","
                + "\"lines\":[{\"id\":\"line_A\",\"ops\":[]}],"
                + "\"combiners\":[],\"output\":\"line_A\"}";

        DagIR dag = DagIRSerializer.fromJson(json);

        assertEquals(1, dag.primaryChannel);
        assertEquals(1, dag.lines.get(0).sourceChannel);
        assertEquals("", dag.lines.get(0).name);
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
