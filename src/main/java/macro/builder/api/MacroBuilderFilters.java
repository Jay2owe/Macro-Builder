package macro.builder.api;

import macro.builder.analysis.MacroBatchCompatibility;
import macro.builder.image.FilterMacroEditorModel;
import macro.builder.image.FilterMacroParser;
import macro.builder.image.FilterMacroParser.Op;
import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.NamedFilterLoader;
import macro.builder.image.variation.FilterCompatibility;
import macro.builder.image.variation.OpTypeParamRegistry;
import macro.builder.image.variation.ParamSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Public facade for filter presets, filter macro parsing, and filter metadata. */
public final class MacroBuilderFilters {

    private MacroBuilderFilters() {
    }

    public static List<String> presetNames() {
        return Collections.unmodifiableList(
                new ArrayList<String>(Arrays.asList(NamedFilterLoader.FILTER_NAMES)));
    }

    public static String loadPreset(String presetName) {
        return NamedFilterLoader.loadFilterContent(presetName);
    }

    public static String loadDefaultPreset() {
        return NamedFilterLoader.loadDefaultFilter();
    }

    public static String loadIntensityPreset() {
        return NamedFilterLoader.loadIntensityFilter();
    }

    public static List<Op> parseOperations(File macroFile) throws Exception {
        return FilterMacroParser.parse(macroFile);
    }

    public static List<Op> parseOperations(String macroContent) {
        return FilterMacroParser.parseString(macroContent);
    }

    public static FilterMacroEditorModel.MacroDefinition parseEditableMacro(String macroContent) {
        return FilterMacroEditorModel.parse(macroContent);
    }

    public static List<String> batchCompatibilityWarnings(String macroContent) {
        return MacroBatchCompatibility.warnings(macroContent);
    }

    public static List<ParamSpec> parameterSpecs(OpType type) {
        return OpTypeParamRegistry.paramsOf(type);
    }

    public static String defaultArgs(OpType type) {
        return OpTypeParamRegistry.argsForDefaults(type);
    }

    public static Map<String, Double> parseArgs(OpType type, String args) {
        return OpTypeParamRegistry.parseArgs(type, args);
    }

    public static String renderArgs(OpType type, Map<String, Double> values) {
        return OpTypeParamRegistry.renderArgs(type, values);
    }

    public static List<OpType> compatibleAlternatives(OpType type) {
        return FilterCompatibility.alternativesFor(type);
    }

    public static List<OpType> compatibleAlternativesExcludingBaseline(OpType type) {
        return FilterCompatibility.alternativesExcludingBaseline(type);
    }
}
