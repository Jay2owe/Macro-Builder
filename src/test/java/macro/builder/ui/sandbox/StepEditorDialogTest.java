package macro.builder.ui.sandbox;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StepEditorDialogTest {

    @Test
    public void renderingPreservesFlagsWhenEditableValueChanges() {
        List<ArgsEditorModel.Token> tokens = ArgsEditorModel.parse("sigma=2 stack");

        assertTrue(ArgsEditorModel.hasEditableParameters(tokens));
        tokens.get(0).setValue("4");

        assertEquals("sigma=4 stack", ArgsEditorModel.render(tokens));
    }

    @Test
    public void parserKeepsBracketedValuesWithSpacesTogether() {
        List<ArgsEditorModel.Token> tokens =
                ArgsEditorModel.parse("method=[Mean C] radius=15 parameter_1 white");

        assertEquals(4, tokens.size());
        assertEquals("method", tokens.get(0).key());
        assertEquals("[Mean C]", tokens.get(0).value());
        assertEquals("parameter_1", tokens.get(2).key());
        assertFalse(tokens.get(2).isEditable());
        assertEquals("method=[Mean C] radius=15 parameter_1 white",
                ArgsEditorModel.render(tokens));
    }

    @Test
    public void unitLabelsMakeSpatialValuesExplicitlyPixelBased() {
        List<ArgsEditorModel.Token> tokens =
                ArgsEditorModel.parse("sigma=2 radius=15 rolling=50 x=2 y=2 z=1");

        assertTrue(ArgsEditorModel.hasPixelParameters(tokens));
        assertEquals("sigma (pixels)", ArgsEditorModel.displayLabel(tokens.get(0)));
        assertEquals("radius (pixels)", ArgsEditorModel.displayLabel(tokens.get(1)));
        assertEquals("rolling (pixels)", ArgsEditorModel.displayLabel(tokens.get(2)));
        assertEquals("x (pixels)", ArgsEditorModel.displayLabel(tokens.get(3)));
        assertEquals("This spatial value is in pixels, not microns.",
                ArgsEditorModel.unitHint(tokens.get(0)));
    }

    @Test
    public void unitLabelsSeparateIntensityAndMethodSettingsFromDistances() {
        List<ArgsEditorModel.Token> tokens =
                ArgsEditorModel.parse("value=100 saturated=0.35 mask=0.60 parameter_1=0 parameter_2=0");

        assertFalse(ArgsEditorModel.hasPixelParameters(tokens));
        assertEquals("value (intensity)", ArgsEditorModel.displayLabel(tokens.get(0)));
        assertEquals("saturated (%)", ArgsEditorModel.displayLabel(tokens.get(1)));
        assertEquals("mask (weight)", ArgsEditorModel.displayLabel(tokens.get(2)));
        assertEquals("parameter_1 (method setting)", ArgsEditorModel.displayLabel(tokens.get(3)));
        assertEquals("This value is an image intensity, not a distance.",
                ArgsEditorModel.unitHint(tokens.get(0)));
    }

    @Test
    public void rawOnlyOptionsAreDetectedAsNotEditable() {
        List<ArgsEditorModel.Token> tokens =
                ArgsEditorModel.parse("stack white only-flags");

        assertFalse(ArgsEditorModel.hasEditableParameters(tokens));
        assertEquals("stack white only-flags", ArgsEditorModel.render(tokens));
    }
}
