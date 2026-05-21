package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DagUndoHistoryTest {

    @Test
    public void undoReturnsPreviousDistinctDag() {
        DagIR first = dag("sigma=1 stack");
        DagIR second = dag("sigma=2 stack");
        DagUndoHistory history = new DagUndoHistory(first);

        assertTrue(history.record(second));

        assertTrue(history.canUndo());
        assertEquals(first, history.undo());
        assertFalse(history.canUndo());
    }

    @Test
    public void duplicateStatesAreNotRecorded() {
        DagIR first = dag("sigma=1 stack");
        DagUndoHistory history = new DagUndoHistory(first);

        assertFalse(history.record(first));

        assertFalse(history.canUndo());
    }

    @Test
    public void maxDepthDropsOldestState() {
        DagIR one = dag("sigma=1 stack");
        DagIR two = dag("sigma=2 stack");
        DagIR three = dag("sigma=3 stack");
        DagUndoHistory history = new DagUndoHistory(one, 1);

        history.record(two);
        history.record(three);

        assertEquals(two, history.undo());
        assertFalse(history.canUndo());
    }

    private static DagIR dag(String args) {
        DagLine line = new DagLine("line_A",
                Collections.singletonList(new DagNode("n1", OpType.GAUSSIAN_BLUR, args)),
                1);
        return new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");
    }
}
