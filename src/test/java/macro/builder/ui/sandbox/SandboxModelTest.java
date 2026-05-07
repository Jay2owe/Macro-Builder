package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.Combiner;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SandboxModelTest {

    @Test
    public void partialDagPreservesLegacyCommandMetadata() {
        DagNode legacy = new DagNode("node_1", OpType.UNKNOWN, "alpha=1",
                "Legacy Command", "Plugins > Legacy Command");
        DagIR dag = new DagIR(1,
                Collections.singletonList(new DagLine("line_A", Collections.singletonList(legacy))),
                Collections.emptyList(),
                "line_A",
                "legacy");
        SandboxModel model = SandboxModel.fromDag(dag);
        model.selectNode(model.lines.get(0).nodes.get(0));

        DagIR partial = model.toPartialDag();

        assertEquals("Legacy Command", partial.lines.get(0).ops.get(0).commandName);
        assertEquals("Plugins > Legacy Command", partial.lines.get(0).ops.get(0).menuPath);
        assertEquals("legacy", partial.executionTier);
    }

    @Test
    public void ctrlClickTogglesBranchSelection() {
        SandboxModel model = modelWithLines(4);

        model.selectLine(model.lines.get(0), false, false);
        model.selectLine(model.lines.get(2), true, false);

        assertEquals(Arrays.asList(model.lines.get(0), model.lines.get(2)),
                model.selectedLinesInVisualOrder());

        model.selectLine(model.lines.get(0), true, false);

        assertEquals(Collections.singletonList(model.lines.get(2)),
                model.selectedLinesInVisualOrder());

        model.selectLine(model.lines.get(2), true, false);

        assertTrue(model.selectedLinesInVisualOrder().isEmpty());
    }

    @Test
    public void shiftClickSelectsRangeFromAnchor() {
        SandboxModel model = modelWithLines(4);

        model.selectLine(model.lines.get(1), false, false);
        model.selectLine(model.lines.get(3), false, true);

        assertEquals(Arrays.asList(model.lines.get(1), model.lines.get(2), model.lines.get(3)),
                model.selectedLinesInVisualOrder());

        model.selectLine(model.lines.get(0), false, true);

        assertEquals(Arrays.asList(model.lines.get(0), model.lines.get(1)),
                model.selectedLinesInVisualOrder());
    }

    @Test
    public void mergeSelectedBranchesUsesVisualOrder() {
        SandboxModel model = modelWithLines(4);

        model.selectLine(model.lines.get(3), false, false);
        model.selectLine(model.lines.get(0), true, false);
        model.selectLine(model.lines.get(2), true, false);
        model.addCombinerForSelectedLines();

        assertEquals(1, model.combiners.size());
        assertEquals(Arrays.asList("line_A", "line_C", "line_D"),
                model.combiners.get(0).inputs);
        assertEquals(model.combiners.get(0), model.selected);
        assertTrue(model.selectedLinesInVisualOrder().isEmpty());

        DagIR dag = model.toDag();
        Combiner savedCombiner = dag.combiners.get(0);
        assertEquals(Arrays.asList("line_A", "line_C", "line_D"), savedCombiner.inputs);
        assertEquals(savedCombiner.id, dag.output);
    }

    @Test
    public void mergeFallsBackToFirstTwoBranchesWhenSelectionIsIncomplete() {
        SandboxModel model = modelWithLines(3);

        model.selectLine(model.lines.get(2), false, false);
        model.addCombinerForSelectedLines();

        assertEquals(1, model.combiners.size());
        assertEquals(Arrays.asList("line_A", "line_B"), model.combiners.get(0).inputs);
    }

    private static SandboxModel modelWithLines(int lineCount) {
        SandboxModel model = new SandboxModel();
        for (int i = 0; i < lineCount; i++) {
            model.addLine();
        }
        return model;
    }
}
