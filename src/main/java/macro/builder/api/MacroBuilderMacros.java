package macro.builder.api;

import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.image.dag.DagIR;
import macro.builder.macro.MacroApplier;

/** Public facade for converting chosen count results back into macro code. */
public final class MacroBuilderMacros {

    private MacroBuilderMacros() {
    }

    public static String applyThresholdToIjm(
            String originalIjm,
            ShootoutResult chosen,
            ShootoutSettings settings) {
        return MacroApplier.applyToIjm(
                originalIjm,
                chosen,
                settings,
                MacroApplier.rangeFor(chosen));
    }

    public static String applyThresholdToIjm(
            String originalIjm,
            ShootoutResult chosen,
            ShootoutSettings settings,
            double minimum,
            double maximum) {
        return MacroApplier.applyToIjm(
                originalIjm,
                chosen,
                settings,
                new MacroApplier.Range(minimum, maximum));
    }

    public static DagIR applyThresholdToDag(
            DagIR dag,
            ShootoutResult chosen,
            ShootoutSettings settings) {
        return MacroApplier.applyToDag(dag, chosen, settings);
    }

    public static String thresholdArgs(
            ShootoutResult chosen,
            ShootoutSettings settings) {
        return MacroApplier.thresholdArgs(chosen, settings, MacroApplier.rangeFor(chosen));
    }

    public static String thresholdArgs(
            ShootoutResult chosen,
            ShootoutSettings settings,
            double minimum,
            double maximum) {
        return MacroApplier.thresholdArgs(
                chosen,
                settings,
                new MacroApplier.Range(minimum, maximum));
    }

    public static String thresholdLine(
            ShootoutResult chosen,
            ShootoutSettings settings) {
        return MacroApplier.thresholdLine(chosen, settings, MacroApplier.rangeFor(chosen));
    }

    public static String thresholdLine(
            ShootoutResult chosen,
            ShootoutSettings settings,
            double minimum,
            double maximum) {
        return MacroApplier.thresholdLine(
                chosen,
                settings,
                new MacroApplier.Range(minimum, maximum));
    }
}
