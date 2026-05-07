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
    public void rawOnlyOptionsAreDetectedAsNotEditable() {
        List<ArgsEditorModel.Token> tokens =
                ArgsEditorModel.parse("stack white only-flags");

        assertFalse(ArgsEditorModel.hasEditableParameters(tokens));
        assertEquals("stack white only-flags", ArgsEditorModel.render(tokens));
    }
}
