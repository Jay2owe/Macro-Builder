package macro.builder.api;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ShortProcessor;
import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.VariantAxis;
import macro.builder.image.variation.VariantPlan;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MacroBuilderVariationsApiTest {

    @Test
    public void samplesAndRunsOneFactorAtATimeVariations() {
        ImagePlus source = shortStack("source");
        DagIR baseline = baselineDag();
        VariantAxis axis = MacroBuilderVariations.paramSweep(
                "n1", "sigma=1 stack", "sigma=2 stack");

        MacroBuilderVariationResult result = MacroBuilderVariations.run(
                MacroBuilderVariationParameters.builder()
                        .sourceImage(source)
                        .baseline(baseline)
                        .addAxis(axis)
                        .maxVariants(3)
                        .build());

        try {
            assertEquals(3, result.plans().size());
            assertEquals(3, result.results().size());
            assertEquals(3, result.successCount());
            assertTrue(result.successful());
            assertEquals(3, result.successfulOutputs().size());
            for (ImagePlus output : result.successfulOutputs()) {
                assertNotNull(output);
                assertEquals(source.getWidth(), output.getWidth());
                assertEquals(source.getHeight(), output.getHeight());
            }
        } finally {
            result.closeOutputs();
            source.flush();
        }
    }

    @Test
    public void cartesianSamplingUsesPublicHelpers() {
        DagIR baseline = baselineDag();
        VariantAxis blur = MacroBuilderVariations.paramSweep(
                "n1", "sigma=1 stack", "sigma=2 stack");
        VariantAxis swap = MacroBuilderVariations.filterSwap(
                "n1",
                Collections.singletonList(MacroBuilderVariations.filterAlternative(
                        "Median", OpType.MEDIAN, "radius=1 stack")));

        List<VariantPlan> plans = MacroBuilderVariations.sampleCartesian(
                baseline,
                java.util.Arrays.asList(blur, swap),
                16);

        assertEquals(2, plans.size());
        assertEquals(OpType.MEDIAN, plans.get(0).dag.lines.get(0).ops.get(0).type);
        assertEquals(OpType.MEDIAN, plans.get(1).dag.lines.get(0).ops.get(0).type);
    }

    @Test
    public void loadsDagFromMacroAndEmitsExecutableMacro() {
        DagIR dag = MacroBuilderVariations.loadDag(
                "run(\"Gaussian Blur...\", \"sigma=1 stack\");\n");

        assertEquals(1, dag.lines.size());
        assertEquals(OpType.GAUSSIAN_BLUR, dag.lines.get(0).ops.get(0).type);
        assertTrue(MacroBuilderVariations.toExecutableMacro(dag).contains("Gaussian Blur"));
    }

    private static DagIR baselineDag() {
        DagLine line = new DagLine(
                "line_1",
                Collections.singletonList(new DagNode(
                        "n1", OpType.GAUSSIAN_BLUR, "sigma=1 stack")),
                1);
        return new DagIR(
                1,
                1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                line.id,
                "native");
    }

    private static ImagePlus shortStack(String title) {
        ImageStack stack = new ImageStack(8, 8);
        for (int z = 0; z < 2; z++) {
            ShortProcessor processor = new ShortProcessor(8, 8);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    processor.set(x, y, x + y + z);
                }
            }
            stack.addSlice("z" + z, processor);
        }
        return new ImagePlus(title, stack);
    }
}
