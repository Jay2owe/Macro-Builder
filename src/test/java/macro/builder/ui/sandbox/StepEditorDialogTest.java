package macro.builder.ui.sandbox;

import ij.measure.Calibration;
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
    public void calibratedSpatialValuesDisplayInMicronsAndStorePixels() {
        Calibration calibration = new Calibration();
        calibration.setUnit("micron");
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 2.0;
        ArgsEditorModel.UnitContext units = ArgsEditorModel.UnitContext.fromCalibration(calibration);
        List<ArgsEditorModel.Token> tokens =
                ArgsEditorModel.parse("sigma=4 x=4 y=6 z=2");

        assertTrue(ArgsEditorModel.hasPhysicalParameters(tokens, units));
        assertFalse(ArgsEditorModel.hasPixelParameters(tokens, units));
        assertEquals("sigma (microns)", ArgsEditorModel.displayLabel(tokens.get(0), units));
        assertEquals("2", ArgsEditorModel.displayValue(tokens.get(0), units));
        assertEquals("12", ArgsEditorModel.storageValue(tokens.get(1), "6", units));
        assertEquals("4", ArgsEditorModel.storageValue(tokens.get(3), "8", units));
        assertEquals("This uses the source image calibration and is saved as pixels, so later metadata loss will not change filtering.",
                ArgsEditorModel.unitHint(tokens.get(0), units));
    }

    @Test
    public void calibratedDefaultArgsTreatCatalogNumbersAsDisplayUnits() {
        Calibration calibration = new Calibration();
        calibration.setUnit("micron");
        calibration.pixelWidth = 0.325;
        calibration.pixelHeight = 0.325;
        calibration.pixelDepth = 2.0;
        ArgsEditorModel.UnitContext units = ArgsEditorModel.UnitContext.fromCalibration(calibration);

        String stored = ArgsEditorModel.storageArgsForDisplayDefaults("sigma=2 stack x=2 y=3 z=4", units);

        assertEquals("sigma=6.153846 stack x=6.153846 y=9.230769 z=2", stored);
        assertEquals("sigma=2 microns stack x=2 microns y=3 microns z=4 microns",
                ArgsEditorModel.displayArgs(stored, units));
    }

    @Test
    public void displayedArgsIncludeUnitsInsteadOfRawStoredPixels() {
        Calibration calibration = new Calibration();
        calibration.setUnit("microns");
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        ArgsEditorModel.UnitContext units = ArgsEditorModel.UnitContext.fromCalibration(calibration);

        assertEquals("sigma=2 microns stack",
                ArgsEditorModel.displayArgs("sigma=4 stack", units));
        assertEquals("sigma=2 pixels stack value=100 intensity saturated=0.35%",
                ArgsEditorModel.displayArgs("sigma=2 stack value=100 saturated=0.35", ArgsEditorModel.UnitContext.none()));
    }

    @Test
    public void anisotropicSingleRadiusStaysPixelBased() {
        Calibration calibration = new Calibration();
        calibration.setUnit("micron");
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 1.0;
        ArgsEditorModel.UnitContext units = ArgsEditorModel.UnitContext.fromCalibration(calibration);
        List<ArgsEditorModel.Token> tokens = ArgsEditorModel.parse("radius=4 x=4 y=4");

        assertEquals("radius (pixels)", ArgsEditorModel.displayLabel(tokens.get(0), units));
        assertEquals("x (microns)", ArgsEditorModel.displayLabel(tokens.get(1), units));
        assertEquals("y (microns)", ArgsEditorModel.displayLabel(tokens.get(2), units));
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
