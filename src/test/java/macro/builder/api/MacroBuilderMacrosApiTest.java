package macro.builder.api;

import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.image.FilterMacroParser;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MacroBuilderMacrosApiTest {

    @Test
    public void publicApiAppliesChosenThresholdToIjm() {
        String updated = MacroBuilderMacros.applyThresholdToIjm(
                "run(\"Gaussian Blur...\", \"sigma=2 stack\");",
                result(ShootoutResult.Source.AUTO, "Triangle", 42.0),
                settings(true));

        assertTrue(updated.endsWith("setAutoThreshold(\"Triangle dark\");\n"));
    }

    @Test
    public void publicApiAppliesChosenThresholdToDag() {
        DagIR dag = new DagIR(
                1,
                1,
                Collections.singletonList(new DagLine(
                        "line_A",
                        Collections.singletonList(new DagNode(
                                "blur",
                                FilterMacroParser.OpType.GAUSSIAN_BLUR,
                                "sigma=2 stack")),
                        1)),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");

        DagIR updated = MacroBuilderMacros.applyThresholdToDag(
                dag,
                result(ShootoutResult.Source.FIXED, "Fixed 12.5", 12.5),
                settings(false));

        assertEquals(2, updated.lines.get(0).ops.size());
        assertEquals(FilterMacroParser.OpType.THRESHOLD, updated.lines.get(0).ops.get(1).type);
        assertTrue(MacroBuilderVariations.toExecutableMacro(updated)
                .contains("setThreshold(12.5, 255);"));
    }

    @Test
    public void publicApiBuildsThresholdLinesAndArgs() {
        ShootoutResult row = result(ShootoutResult.Source.FIXED, "Fixed 12.5", 12.5);

        assertEquals("setThreshold(12.5, 255);",
                MacroBuilderMacros.thresholdLine(row, settings(false)));
        assertEquals("mode=fixed lower=12.5 upper=255",
                MacroBuilderMacros.thresholdArgs(row, settings(false)));
    }

    private static ShootoutResult result(ShootoutResult.Source source, String label, double threshold) {
        return ShootoutResult.success(
                source,
                ShootoutSettings.CountingMode.PARTICLES_2D,
                label,
                Double.valueOf(threshold),
                0.0,
                255.0,
                null,
                new ObjectCounter.CountSummary(1, 1.0, 1.0, 0.1));
    }

    private static ShootoutSettings settings(boolean darkBackground) {
        return new ShootoutSettings(
                ShootoutSettings.CountingMode.PARTICLES_2D,
                ShootoutSettings.ThresholdMode.AUTO_METHODS,
                Collections.singletonList("Triangle"),
                Collections.<Double>emptyList(),
                0.0,
                Double.POSITIVE_INFINITY,
                darkBackground);
    }
}
