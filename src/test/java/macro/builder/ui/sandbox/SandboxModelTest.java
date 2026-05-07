package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

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
        model.selected = model.lines.get(0).nodes.get(0);

        DagIR partial = model.toPartialDag();

        assertEquals("Legacy Command", partial.lines.get(0).ops.get(0).commandName);
        assertEquals("Plugins > Legacy Command", partial.lines.get(0).ops.get(0).menuPath);
        assertEquals("legacy", partial.executionTier);
    }
}
