package macro.builder.analysis;

import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BatchMacroExporterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exportWritesWrapperMacroFilterMacroAndSettings() throws Exception {
        File wrapper = new File(temporaryFolder.getRoot(), "Validated_Counts.ijm");
        String macro = "run(\"Add...\", \"value=0\");\n";
        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.OBJECTS_3D,
                ThresholdMode.AUTO_AND_FIXED,
                Arrays.asList("Otsu", "Li"),
                Collections.singletonList(Double.valueOf(2000.0)),
                100.0,
                Double.POSITIVE_INFINITY,
                false);

        BatchMacroExporter.ExportResult result = new BatchMacroExporter().export(wrapper, macro, settings);

        assertTrue(result.wrapperMacro.isFile());
        assertTrue(result.filterMacro.isFile());
        assertTrue(result.settingsJson.isFile());
        assertEquals("Validated_Counts_Filter.ijm", result.filterMacro.getName());
        assertEquals("Validated_Counts.settings.json", result.settingsJson.getName());
        assertEquals(macro, read(result.filterMacro));

        String wrapperText = read(result.wrapperMacro);
        assertTrue(wrapperText.contains("run(\"Macro Builder Batch Count\""));
        assertTrue(wrapperText.contains("settings=[\" + settings + \"] input=[\" + input + \"] output=[\" + output + \"]"));

        String json = read(result.settingsJson);
        assertTrue(json.contains("\"macroPath\": \"Validated_Counts_Filter.ijm\""));
        assertTrue(json.contains("\"countingMode\": \"OBJECTS_3D\""));
        assertTrue(json.contains("\"thresholdMode\": \"AUTO_AND_FIXED\""));
        assertTrue(json.contains("\"fixedThresholds\": [2000]"));
        assertTrue(json.contains("\"maxSize\": \"Infinity\""));

        BatchMacroExporter.ExportedSettings roundTripped = BatchMacroExporter.readSettings(result.settingsJson);
        assertEquals(result.filterMacro.getCanonicalFile(), roundTripped.macroFile().getCanonicalFile());
        assertEquals(CountingMode.OBJECTS_3D, roundTripped.settings.countingMode);
        assertEquals(ThresholdMode.AUTO_AND_FIXED, roundTripped.settings.thresholdMode);
        assertEquals(Arrays.asList("Otsu", "Li"), roundTripped.settings.autoMethods);
        assertEquals(2000.0, roundTripped.settings.fixedThresholds.get(0).doubleValue(), 0.0001);
        assertEquals(100.0, roundTripped.settings.minSize, 0.0001);
        assertEquals(Double.POSITIVE_INFINITY, roundTripped.settings.maxSize, 0.0001);
        assertEquals(false, roundTripped.settings.darkBackground);
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
