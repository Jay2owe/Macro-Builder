package macro.builder.macro;

import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.image.FilterMacroParser;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.dag.DagToIjmEmitter;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MacroApplierTest {

    @Test
    public void appliesAutoVariantToIjm() {
        String updated = MacroApplier.applyToIjm(
                "run(\"Gaussian Blur...\", \"sigma=2 stack\");",
                result(ShootoutResult.Source.AUTO, "Triangle", 42.0),
                settings(true),
                new MacroApplier.Range(0.0, 255.0));

        assertTrue(updated.endsWith("setAutoThreshold(\"Triangle dark\");\n"));
    }

    @Test
    public void appliesFixedVariantToIjmWithLocaleRootNumberFormatting() {
        String updated = MacroApplier.applyToIjm(
                "",
                result(ShootoutResult.Source.FIXED, "Fixed 12.5", 12.5),
                settings(false),
                new MacroApplier.Range(0.0, 255.0));

        assertEquals("setThreshold(12.5, 255);\n", updated);
    }

    @Test
    public void insertsThresholdNodeAtOutputLineEnd() {
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

        DagIR updated = MacroApplier.applyToDag(
                dag,
                result(ShootoutResult.Source.GRID, "Grid 12.5", 12.5),
                settings(false));

        assertEquals(1, dag.lines.get(0).ops.size());
        assertEquals(2, updated.lines.get(0).ops.size());
        DagNode threshold = updated.lines.get(0).ops.get(1);
        assertEquals(FilterMacroParser.OpType.THRESHOLD, threshold.type);
        assertTrue(threshold.args.contains("mode=fixed"));
        assertTrue(threshold.args.contains("lower=12.5"));
        assertTrue(threshold.args.contains("upper=255"));
        assertTrue(DagToIjmEmitter.emit(updated).contains("setThreshold(12.5, 255);"));
    }

    @Test
    public void mutatesExistingThresholdNode() {
        DagIR dag = new DagIR(
                1,
                1,
                Collections.singletonList(new DagLine(
                        "line_A",
                        Collections.singletonList(new DagNode(
                                "threshold",
                                FilterMacroParser.OpType.THRESHOLD,
                                "mode=fixed lower=10 upper=255")),
                        1)),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");

        DagIR updated = MacroApplier.applyToDag(
                dag,
                result(ShootoutResult.Source.AUTO, "Otsu", 40.0),
                settings(false));

        assertEquals(1, updated.lines.get(0).ops.size());
        DagNode threshold = updated.lines.get(0).ops.get(0);
        assertEquals("threshold", threshold.id);
        assertEquals(FilterMacroParser.OpType.THRESHOLD, threshold.type);
        assertTrue(threshold.args.contains("mode=auto"));
        assertTrue(threshold.args.contains("method=Otsu"));
        assertTrue(DagToIjmEmitter.emit(updated).contains("setAutoThreshold(\"Otsu\");"));
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
