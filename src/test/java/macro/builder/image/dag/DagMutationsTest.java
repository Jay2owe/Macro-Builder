package macro.builder.image.dag;

import macro.builder.image.FilterMacroParser.OpType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class DagMutationsTest {

    @Test
    public void withNodeArgsReplacesArgsAndPreservesEverythingElse() {
        DagIR src = sampleDag();
        DagIR mutated = DagMutations.withNodeArgs(src, "n1", "sigma=4 stack");

        DagNode original = src.lines.get(0).ops.get(0);
        DagNode replaced = mutated.lines.get(0).ops.get(0);
        assertEquals("sigma=4 stack", replaced.args);
        assertEquals(original.id, replaced.id);
        assertEquals(original.type, replaced.type);
        assertEquals(original.commandName, replaced.commandName);
        assertEquals(original.menuPath, replaced.menuPath);

        // Untouched node on the second line is identical.
        assertEquals(src.lines.get(1).ops.get(0), mutated.lines.get(1).ops.get(0));
        // Top-level fields preserved.
        assertEquals(src.version, mutated.version);
        assertEquals(src.primaryChannel, mutated.primaryChannel);
        assertEquals(src.combiners, mutated.combiners);
        assertEquals(src.output, mutated.output);
        assertEquals(src.executionTier, mutated.executionTier);
    }

    @Test
    public void withNodeSubstitutedSwapsTypeAndArgsAndKeepsId() {
        DagIR src = sampleDag();
        DagIR mutated = DagMutations.withNodeSubstituted(
                src, "n1", OpType.MEDIAN, "radius=3 stack");

        DagNode replaced = mutated.lines.get(0).ops.get(0);
        assertEquals("n1", replaced.id);
        assertEquals(OpType.MEDIAN, replaced.type);
        assertEquals("radius=3 stack", replaced.args);
        assertEquals("", replaced.commandName);
        assertEquals("", replaced.menuPath);

        // Other line untouched.
        assertEquals(src.lines.get(1).ops.get(0), mutated.lines.get(1).ops.get(0));
    }

    @Test
    public void withNodeArgsThrowsOnUnknownNodeId() {
        DagIR src = sampleDag();
        try {
            DagMutations.withNodeArgs(src, "missing", "sigma=1 stack");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void withNodeSubstitutedThrowsOnUnknownNodeId() {
        DagIR src = sampleDag();
        try {
            DagMutations.withNodeSubstituted(src, "missing", OpType.MEDIAN, "radius=2 stack");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void mutationsLeaveSourceDagUnchanged() {
        DagIR src = sampleDag();
        DagIR snapshot = sampleDag();
        DagMutations.withNodeArgs(src, "n1", "sigma=99 stack");
        DagMutations.withNodeSubstituted(src, "n2", OpType.MEAN, "radius=5 stack");
        assertEquals(snapshot, src);
        // Same DagLine instances are reused for the untouched line — inner structure
        // is shared safely because every type in the chain is immutable.
        DagIR mutated = DagMutations.withNodeArgs(src, "n1", "sigma=4 stack");
        assertSame(src.lines.get(1), src.lines.get(1));
        assertNotSame(src, mutated);
    }

    @Test
    public void combinerReferencesRemainValidAfterSubstitution() {
        DagIR src = sampleDag();
        DagIR mutated = DagMutations.withNodeSubstituted(
                src, "n2", OpType.MEAN, "radius=4 stack");

        // Combiner still references the original line ids (line_1, line_2).
        assertEquals(src.combiners, mutated.combiners);
        // Substituted node keeps its id, so the line still produces the same image label.
        assertEquals("n2", mutated.lines.get(1).ops.get(0).id);
    }

    @Test
    public void substitutingOutTheOnlyTierTwoNodeFlipsToNative() {
        DagNode tierTwo = new DagNode("nLegacy", OpType.UNKNOWN, "",
                "Some Plugin Command", "Plugins>Some>Path");
        DagIR src = new DagIR(1, 1,
                Collections.singletonList(new DagLine(
                        "line_1",
                        Collections.singletonList(tierTwo),
                        1)),
                Collections.<Combiner>emptyList(),
                "line_1",
                "native");
        // Source must be legacy because the tier-2 node forces it.
        assertEquals("legacy", src.executionTier);

        DagIR mutated = DagMutations.withNodeSubstituted(
                src, "nLegacy", OpType.GAUSSIAN_BLUR, "sigma=2 stack");

        assertEquals("native", mutated.executionTier);
        assertEquals("", mutated.lines.get(0).ops.get(0).commandName);
    }

    @Test
    public void substitutingOneOfManyTierTwoNodesStaysLegacy() {
        DagNode tierTwoA = new DagNode("nLegacyA", OpType.UNKNOWN, "",
                "Plugin A", "Plugins>A");
        DagNode tierTwoB = new DagNode("nLegacyB", OpType.UNKNOWN, "",
                "Plugin B", "Plugins>B");
        DagIR src = new DagIR(1, 1,
                Collections.singletonList(new DagLine(
                        "line_1",
                        Arrays.asList(tierTwoA, tierTwoB),
                        1)),
                Collections.<Combiner>emptyList(),
                "line_1",
                "native");
        assertEquals("legacy", src.executionTier);

        DagIR mutated = DagMutations.withNodeSubstituted(
                src, "nLegacyA", OpType.GAUSSIAN_BLUR, "sigma=2 stack");

        // The other tier-2 node still forces legacy.
        assertEquals("legacy", mutated.executionTier);
    }

    @Test
    public void withNodeArgsPreservesTierTwoCommandName() {
        DagNode tierTwo = new DagNode("nLegacy", OpType.UNKNOWN, "old=1",
                "Some Plugin Command", "Plugins>Some>Path");
        DagIR src = new DagIR(1, 1,
                Collections.singletonList(new DagLine(
                        "line_1",
                        Collections.singletonList(tierTwo),
                        1)),
                Collections.<Combiner>emptyList(),
                "line_1",
                "native");

        DagIR mutated = DagMutations.withNodeArgs(src, "nLegacy", "old=2");

        DagNode replaced = mutated.lines.get(0).ops.get(0);
        assertEquals("old=2", replaced.args);
        assertEquals("Some Plugin Command", replaced.commandName);
        assertEquals("Plugins>Some>Path", replaced.menuPath);
        assertEquals("legacy", mutated.executionTier);
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
        List<DagLine> lines = Arrays.asList(gaussianLine, medianLine);
        return new DagIR(1, 1, lines,
                Collections.singletonList(combiner),
                "combined",
                "native");
    }
}
