package macro.builder.ui.sandbox.variation;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.VariantPlan;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class IjmClipboardExporterTest {

    @Test
    public void buildTextIncludesHeaderAndMacroBodyForEveryPlan() {
        VariantPlan first = new VariantPlan("sigma=1", dag("n1", "sigma=1 stack"), null);
        VariantPlan second = new VariantPlan("sigma=2", dag("n1", "sigma=2 stack"), null);

        String text = IjmClipboardExporter.buildText(java.util.Arrays.asList(first, second));

        assertTrue(text.contains("// VARIANT: sigma=1"));
        assertTrue(text.contains("// VARIANT: sigma=2"));
        assertTrue(text.contains("run(\"Gaussian Blur...\", \"sigma=1 stack\")"));
        assertTrue(text.contains("run(\"Gaussian Blur...\", \"sigma=2 stack\")"));
    }

    private static DagIR dag(String nodeId, String args) {
        DagLine line = new DagLine("line_A",
                Collections.singletonList(new DagNode(nodeId, OpType.GAUSSIAN_BLUR, args)),
                1);
        return new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");
    }
}
