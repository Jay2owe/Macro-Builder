package macro.builder.ui.sandbox;

import macro.builder.image.dag.CombinerOp;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MergeEditorDialogTest {

    @Test
    public void validationRequiresTwoDistinctInputs() {
        assertFalse(MergeEditorDialog.hasEnoughInputs(null));
        assertFalse(MergeEditorDialog.hasEnoughInputs(Collections.singletonList("line_A")));
        assertFalse(MergeEditorDialog.hasEnoughInputs(Arrays.asList("line_A", "line_A")));

        assertTrue(MergeEditorDialog.hasEnoughInputs(Arrays.asList("line_A", "line_B")));
    }

    @Test
    public void applySelectionUpdatesOperationAndInputs() {
        SandboxModel.CombinerNode combiner = new SandboxModel.CombinerNode("combiner_1",
                CombinerOp.AND,
                Arrays.asList("line_A", "line_B"));

        boolean changed = MergeEditorDialog.applySelection(combiner,
                CombinerOp.OR,
                Arrays.asList("line_B", "line_C"));

        assertTrue(changed);
        assertEquals(CombinerOp.OR, combiner.op);
        assertEquals(Arrays.asList("line_B", "line_C"), combiner.inputs);

        assertFalse(MergeEditorDialog.applySelection(combiner,
                CombinerOp.OR,
                Arrays.asList("line_B", "line_C")));
    }

    @Test
    public void selectedInputsKeepExistingOrderAndAppendNewBranches() {
        List<String> ordered = MergeEditorDialog.orderedSelectedInputs(
                Arrays.asList("line_B", "line_A"),
                Arrays.asList("line_A", "line_B", "line_C"));

        assertEquals(Arrays.asList("line_B", "line_A", "line_C"), ordered);
    }

    @Test(expected = IllegalArgumentException.class)
    public void applySelectionRejectsInvalidMerge() {
        SandboxModel.CombinerNode combiner = new SandboxModel.CombinerNode("combiner_1",
                CombinerOp.AND,
                Arrays.asList("line_A", "line_B"));

        MergeEditorDialog.applySelection(combiner,
                CombinerOp.ADD,
                Collections.singletonList("line_A"));
    }
}
