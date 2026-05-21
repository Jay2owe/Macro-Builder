package macro.builder.api;

import macro.builder.image.FilterMacroEditorModel;
import macro.builder.image.FilterMacroParser;
import macro.builder.image.FilterMacroParser.Op;
import macro.builder.image.FilterMacroParser.OpType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacroBuilderFiltersApiTest {

    @Test
    public void publicApiLoadsPresetsAndParsesOperations() {
        assertTrue(MacroBuilderFilters.presetNames().contains("Default"));
        String macro = MacroBuilderFilters.loadDefaultPreset();

        assertTrue(macro.contains("run("));
        List<Op> ops = MacroBuilderFilters.parseOperations(macro);
        assertFalse(ops.isEmpty());
    }

    @Test
    public void publicApiEditsAndRendersFilterMacros() {
        FilterMacroEditorModel.MacroDefinition definition =
                MacroBuilderFilters.parseEditableMacro("run(\"Gaussian Blur...\", \"sigma=2 stack\");\n");

        assertEquals(1, definition.editableParameterCount());
        definition.getSections().get(0).entries.get(0).parameters.get(0).setValue("4");

        assertTrue(definition.render().contains("sigma=4 stack"));
    }

    @Test
    public void publicApiExposesFilterMetadataForVariations() {
        assertFalse(MacroBuilderFilters.parameterSpecs(OpType.GAUSSIAN_BLUR).isEmpty());
        assertEquals("sigma=2.0", MacroBuilderFilters.defaultArgs(OpType.GAUSSIAN_BLUR));
        assertEquals(Double.valueOf(3.0),
                MacroBuilderFilters.parseArgs(OpType.GAUSSIAN_BLUR, "sigma=3 stack").get("sigma"));
        assertTrue(MacroBuilderFilters.renderArgs(
                OpType.GAUSSIAN_BLUR,
                MacroBuilderFilters.parseArgs(OpType.GAUSSIAN_BLUR, "sigma=3 stack"))
                .contains("sigma=3.0"));
        assertTrue(MacroBuilderFilters.compatibleAlternatives(OpType.GAUSSIAN_BLUR)
                .contains(FilterMacroParser.OpType.MEDIAN));
    }

    @Test
    public void publicApiReportsBatchCompatibilityWarnings() {
        List<String> warnings = MacroBuilderFilters.batchCompatibilityWarnings(
                "selectWindow(\"Fixed title\");\n");

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("selectWindow"));
    }
}
