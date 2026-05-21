package macro.builder.analysis;

import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import ij.macro.Tokenizer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BatchMacroExporterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exportWritesSingleSelfContainedWrapperMacro() throws Exception {
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

        BatchMacroExporter.ExportResult result = new BatchMacroExporter().export(wrapper, macro, settings, 2);

        assertTrue(result.wrapperMacro.isFile());
        assertFalse(new File(temporaryFolder.getRoot(), "Validated_Counts_Filter.ijm").exists());
        assertFalse(new File(temporaryFolder.getRoot(), "Validated_Counts.settings.json").exists());

        String wrapperText = read(result.wrapperMacro);
        assertNotNull(new Tokenizer().tokenize(wrapperText));
        assertTrue(wrapperText.contains("run(\"Macro Builder Batch Count\""));
        assertTrue(wrapperText.contains("File.saveString(filterMacro, filterPath);"));
        assertTrue(wrapperText.contains("File.saveString(settingsJson, settingsPath);"));
        assertTrue(wrapperText.contains("File.delete(filterPath);"));
        assertTrue(wrapperText.contains("settings=[\" + settingsPath + \"] input=[\" + input + \"] output=[\" + output + \"]"));
        assertTrue(wrapperText.contains("run(\\\"Add...\\\", \\\"value=0\\\");\\n"));
        assertTrue(wrapperText.contains("\\\"macroPath\\\": \\\"Macro_Builder_Batch_Count_Filter.runtime.ijm\\\""));
        assertTrue(wrapperText.contains("\\\"primaryChannel\\\": 2"));
        assertTrue(wrapperText.contains("\\\"countingMode\\\": \\\"OBJECTS_3D\\\""));
        assertTrue(wrapperText.contains("\\\"thresholdMode\\\": \\\"AUTO_AND_FIXED\\\""));
        assertTrue(wrapperText.contains("\\\"fixedThresholds\\\": [2000]"));
        assertTrue(wrapperText.contains("\\\"maxSize\\\": \\\"Infinity\\\""));
    }

    @Test
    public void oldSettingsWithoutPrimaryChannelDefaultToOne() throws Exception {
        File settingsFile = new File(temporaryFolder.getRoot(), "old.settings.json");
        Files.write(settingsFile.toPath(), (
                "{\n"
                        + "  \"schemaVersion\": 1,\n"
                        + "  \"macroPath\": \"Filter.ijm\",\n"
                        + "  \"resultsFile\": \"counts.csv\",\n"
                        + "  \"countingMode\": \"PARTICLES_2D\",\n"
                        + "  \"thresholdMode\": \"FIXED_VALUES\",\n"
                        + "  \"autoMethods\": [],\n"
                        + "  \"fixedThresholds\": [100],\n"
                        + "  \"minSize\": 0,\n"
                        + "  \"maxSize\": \"Infinity\",\n"
                        + "  \"darkBackground\": true\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));

        BatchMacroExporter.ExportedSettings settings = BatchMacroExporter.readSettings(settingsFile);

        assertEquals(1, settings.primaryChannel);
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
