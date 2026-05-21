package macro.builder.api;

import ij.ImagePlus;
import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagToIjmEmitter;
import macro.builder.image.dag.IjmToDagLoader;
import macro.builder.image.variation.ProgressCallback;
import macro.builder.image.variation.VariantAxis;
import macro.builder.image.variation.VariantExecutor;
import macro.builder.image.variation.VariantPlan;
import macro.builder.image.variation.VariantResult;
import macro.builder.image.variation.VariantSampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Public facade for Macro Builder DAG and variation workflows. */
public final class MacroBuilderVariations {

    private MacroBuilderVariations() {
    }

    public static DagIR loadDag(String macroContent) {
        return IjmToDagLoader.load(macroContent);
    }

    public static String toExecutableMacro(DagIR dag) {
        return DagToIjmEmitter.emitExecutable(dag);
    }

    public static String toReadableMacro(DagIR dag) {
        return DagToIjmEmitter.emitReadable(dag);
    }

    public static VariantAxis paramSweep(String nodeId, String... argsValues) {
        List<VariantAxis.AlternativeValue> alternatives =
                new ArrayList<VariantAxis.AlternativeValue>();
        if (argsValues != null) {
            for (String args : argsValues) {
                alternatives.add(new VariantAxis.AlternativeValue(args, null, args));
            }
        }
        return new VariantAxis(nodeId, VariantAxis.Kind.PARAM_SWEEP, alternatives);
    }

    public static VariantAxis paramSweep(String nodeId, List<String> argsValues) {
        List<VariantAxis.AlternativeValue> alternatives =
                new ArrayList<VariantAxis.AlternativeValue>();
        if (argsValues != null) {
            for (String args : argsValues) {
                alternatives.add(new VariantAxis.AlternativeValue(args, null, args));
            }
        }
        return new VariantAxis(nodeId, VariantAxis.Kind.PARAM_SWEEP, alternatives);
    }

    public static VariantAxis filterSwap(
            String nodeId,
            List<VariantAxis.AlternativeValue> alternatives) {
        return new VariantAxis(nodeId, VariantAxis.Kind.FILTER_SWAP, alternatives);
    }

    public static VariantAxis.AlternativeValue filterAlternative(
            String label,
            OpType type,
            String args) {
        return new VariantAxis.AlternativeValue(label, type, args);
    }

    public static List<VariantPlan> sampleOneFactorAtATime(
            DagIR baseline,
            List<VariantAxis> axes,
            int maxVariants) {
        return VariantSampler.ofat(baseline, axes, maxVariants);
    }

    public static List<VariantPlan> sampleCartesian(
            DagIR baseline,
            List<VariantAxis> axes,
            int maxVariants) {
        return VariantSampler.cartesian(baseline, axes, maxVariants);
    }

    public static MacroBuilderVariationResult runPlans(
            ImagePlus source,
            List<VariantPlan> plans) {
        return runPlans(source, plans, null);
    }

    public static MacroBuilderVariationResult runPlans(
            ImagePlus source,
            List<VariantPlan> plans,
            ProgressCallback progress) {
        List<VariantPlan> safePlans = plans == null
                ? Collections.<VariantPlan>emptyList()
                : plans;
        List<VariantResult> results = VariantExecutor.runAll(source, safePlans, progress);
        return new MacroBuilderVariationResult(safePlans, results);
    }

    public static MacroBuilderVariationResult run(MacroBuilderVariationParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        List<VariantPlan> plans = parameters.plans();
        List<VariantResult> results = VariantExecutor.runAll(
                parameters.sourceImage(),
                plans,
                parameters.progress());
        return new MacroBuilderVariationResult(plans, results);
    }
}
