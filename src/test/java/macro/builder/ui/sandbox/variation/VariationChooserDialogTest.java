package macro.builder.ui.sandbox.variation;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.FilterCompatibility;
import macro.builder.image.variation.OpTypeParamRegistry;
import macro.builder.image.variation.ParamSpec;
import macro.builder.image.variation.VariantAxis;
import macro.builder.image.variation.VariantAxis.AlternativeValue;
import macro.builder.image.variation.VariantAxis.Kind;
import macro.builder.image.variation.VariantPlan;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Headless tests for the building blocks of {@link VariationChooserDialog}.
 *
 * <p>The dialog itself instantiates a JDialog (which fails in headless CI), so
 * these tests exercise the panels directly and the static plan-chooser. Manual
 * smoke of the full dialog is reserved for stage 08's end-to-end check with
 * Fiji running.
 */
public class VariationChooserDialogTest {

    @Test
    public void sweepPanelGeometricSpacingMatchesSpecExample() {
        SweepPanel panel = new SweepPanel();
        panel.setNode(gaussianNode("n1", "sigma=2 stack"));
        // Gaussian's only param is sigma (LOG). Confirm and override the range.
        ParamSpec spec = panel.getSelectedParam();
        assertEquals("sigma", spec.argKey);
        assertEquals(ParamSpec.Scale.LOG, spec.scale);
        panel.setSweepRange(0.5, 4.0, 4);

        VariantAxis axis = panel.buildAxis();
        assertEquals(Kind.PARAM_SWEEP, axis.kind);
        assertEquals("n1", axis.nodeId);
        assertEquals(4, axis.alternatives.size());

        // Geometric spacing on [0.5, 4.0] with 4 steps yields 0.5, 1.0, 2.0, 4.0.
        double[] expected = {0.5, 1.0, 2.0, 4.0};
        for (int i = 0; i < expected.length; i++) {
            AlternativeValue alt = axis.alternatives.get(i);
            String args = alt.args;
            double actual = parseDoubleArg(args, "sigma");
            assertEquals("step " + i, expected[i], actual, 1e-6);
            // FILTER_SWAP only — PARAM_SWEEP keeps null type.
            assertEquals(null, alt.type);
            assertTrue("label expected to start with sigma=", alt.label.startsWith("sigma="));
        }
    }

    @Test
    public void sweepPanelLinearSpacingProducesArithmeticSteps() {
        SweepPanel panel = new SweepPanel();
        // MEDIAN's radius is LINEAR.
        panel.setNode(new DagNode("n1", OpType.MEDIAN, "radius=2 stack"));
        ParamSpec spec = panel.getSelectedParam();
        assertEquals(ParamSpec.Scale.LINEAR, spec.scale);
        panel.setSweepRange(1.0, 10.0, 4);

        VariantAxis axis = panel.buildAxis();
        assertEquals(4, axis.alternatives.size());

        // Linear spacing on [1, 10] with 4 steps: 1, 4, 7, 10.
        double[] expected = {1.0, 4.0, 7.0, 10.0};
        for (int i = 0; i < expected.length; i++) {
            double actual = parseDoubleArg(axis.alternatives.get(i).args, "radius");
            assertEquals("step " + i, expected[i], actual, 1e-9);
        }
    }

    @Test
    public void swapPanelExcludesBaselineAndAppliesRegistryDefaults() {
        SwapPanel panel = new SwapPanel();
        DagNode baseline = gaussianNode("n7", "sigma=2 stack");
        panel.setNode(baseline);
        // Baseline (Gaussian) is auto-checked and disabled. Tick Median + Mean too.
        panel.setTicked(OpType.GAUSSIAN_BLUR, OpType.MEDIAN, OpType.MEAN);

        VariantAxis axis = panel.buildAxis();
        assertEquals(Kind.FILTER_SWAP, axis.kind);
        assertEquals("n7", axis.nodeId);
        assertEquals("baseline excluded", 2, axis.alternatives.size());

        AlternativeValue median = findByType(axis.alternatives, OpType.MEDIAN);
        assertEquals(OpTypeParamRegistry.argsForDefaults(OpType.MEDIAN), median.args);
        assertEquals("MEDIAN", median.label);

        AlternativeValue mean = findByType(axis.alternatives, OpType.MEAN);
        assertEquals(OpTypeParamRegistry.argsForDefaults(OpType.MEAN), mean.args);
        assertEquals("MEAN", mean.label);
    }

    @Test
    public void choosePlansRejectsCartesianWhenAdvancedClosed() {
        DagIR baseline = singleLineDag(gaussianNode("n1", "sigma=2 stack"));
        VariantAxis axis = new VariantAxis("n1", Kind.PARAM_SWEEP, Arrays.asList(
                new AlternativeValue("sigma=1.0", null, "sigma=1.0"),
                new AlternativeValue("sigma=4.0", null, "sigma=4.0")));
        try {
            VariationChooserDialog.choosePlans(baseline, Collections.singletonList(axis),
                    /* advancedOpen = */ false,
                    /* cartesianRequested = */ true,
                    9);
            fail("expected IllegalStateException for cartesian without advanced");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("advanced"));
        }
    }

    @Test
    public void choosePlansAllowsOfatWhenAdvancedClosed() {
        DagIR baseline = singleLineDag(gaussianNode("n1", "sigma=2 stack"));
        VariantAxis axis = new VariantAxis("n1", Kind.PARAM_SWEEP, Arrays.asList(
                new AlternativeValue("sigma=1.0", null, "sigma=1.0"),
                new AlternativeValue("sigma=4.0", null, "sigma=4.0")));
        List<VariantPlan> plans = VariationChooserDialog.choosePlans(
                baseline, Collections.singletonList(axis),
                /* advancedOpen = */ false,
                /* cartesianRequested = */ false,
                9);
        // 1 baseline + 2 alternatives = 3 plans.
        assertEquals(3, plans.size());
        assertEquals("baseline", plans.get(0).label);
    }

    @Test
    public void filterCompatibility3dDoesNotIncludeAny2dFilters() {
        List<OpType> alts = FilterCompatibility.alternativesFor(OpType.GAUSSIAN_BLUR_3D);
        assertFalse("3D bucket should be non-empty", alts.isEmpty());
        for (OpType t : alts) {
            assertTrue("2D pollution: " + t,
                    t == OpType.GAUSSIAN_BLUR_3D
                            || t == OpType.MEDIAN_3D
                            || t == OpType.MINIMUM_3D);
        }
    }

    @Test
    public void filterCompatibilityGaussianBlurContainsBlurFamily() {
        List<OpType> alts = FilterCompatibility.alternativesFor(OpType.GAUSSIAN_BLUR);
        assertTrue(alts.contains(OpType.GAUSSIAN_BLUR));
        assertTrue(alts.contains(OpType.MEDIAN));
        assertTrue(alts.contains(OpType.MEAN));
        assertTrue(alts.contains(OpType.UNSHARP_MASK));
        // Must NOT cross over into 3D filters.
        assertFalse(alts.contains(OpType.GAUSSIAN_BLUR_3D));
    }

    @Test
    public void filterCompatibilityUnknownOpsReturnEmpty() {
        assertTrue(FilterCompatibility.alternativesFor(null).isEmpty());
        assertTrue(FilterCompatibility.alternativesFor(OpType.UNKNOWN).isEmpty());
    }

    @Test
    public void sweepPanelAlternativeCountReflectsStepsSpinner() {
        SweepPanel panel = new SweepPanel();
        panel.setNode(gaussianNode("n1", "sigma=2 stack"));
        panel.setSweepRange(1.0, 8.0, 6);
        assertEquals(6, panel.alternativeCount());
        assertEquals(6, panel.buildAxis().alternatives.size());
    }

    @Test
    public void swapPanelEmptyTickListMakesPanelNotReady() {
        SwapPanel panel = new SwapPanel();
        panel.setNode(gaussianNode("n1", "sigma=2 stack"));
        // Default state: only the baseline is checked (and disabled), no real
        // alternatives picked yet.
        assertFalse(panel.isReady());
        assertEquals(0, panel.alternativeCount());
    }

    // --- helpers -----------------------------------------------------------

    private static DagNode gaussianNode(String id, String args) {
        return new DagNode(id, OpType.GAUSSIAN_BLUR, args);
    }

    private static DagIR singleLineDag(DagNode node) {
        DagLine line = new DagLine("line_A", "primary",
                Collections.singletonList(node), 1);
        return new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_A",
                "native");
    }

    private static AlternativeValue findByType(List<AlternativeValue> alts, OpType type) {
        for (AlternativeValue a : alts) {
            if (a.type == type) return a;
        }
        fail("no alternative for type " + type);
        return null;
    }

    /** Re-uses {@link OpTypeParamRegistry#parseArgs} so the assertion stays
     *  resilient to formatting changes in the renderer. */
    private static double parseDoubleArg(String args, String key) {
        Double v = OpTypeParamRegistry.parseArgs(OpType.GAUSSIAN_BLUR, args).get(key);
        if (v != null) return v.doubleValue();
        // Fallback for non-Gaussian keys (radius etc.).
        v = OpTypeParamRegistry.parseArgs(OpType.MEDIAN, args).get(key);
        if (v != null) return v.doubleValue();
        fail("missing arg key " + key + " in " + args);
        return Double.NaN;
    }
}
