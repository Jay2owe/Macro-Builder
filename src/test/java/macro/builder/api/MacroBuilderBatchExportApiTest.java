package macro.builder.api;

import macro.builder.analysis.BatchMacroExporter;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MacroBuilderBatchExportApiTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publicApiExportsWrapperMacro() throws Exception {
        File wrapper = new File(temporaryFolder.getRoot(), "Counts.ijm");

        BatchMacroExporter.ExportResult result = MacroBuilderBatchExport.exportWrapperMacro(
                wrapper,
                "run(\"Add...\", \"value=0\");\n",
                settings(),
                2);

        String text = new String(Files.readAllBytes(result.wrapperMacro.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.wrapperMacro.isFile());
        assertTrue(text.contains("run(\"Macro Builder Batch Count\""));
        assertTrue(text.contains("\\\"primaryChannel\\\": 2"));
    }

    @Test
    public void publicApiRoundTripsExportSettingsJson() throws Exception {
        BatchMacroExporter.ExportedSettings exported =
                new BatchMacroExporter.ExportedSettings(null, "Filter.ijm", "counts.csv", settings(), 3);
        File settingsFile = new File(temporaryFolder.getRoot(), "settings.json");
        Files.write(
                settingsFile.toPath(),
                MacroBuilderBatchExport.settingsToJson(exported).getBytes(StandardCharsets.UTF_8));

        BatchMacroExporter.ExportedSettings read = MacroBuilderBatchExport.readSettings(settingsFile);

        assertEquals(3, read.primaryChannel);
        assertEquals("counts.csv", read.resultsFile);
    }

    private static ShootoutSettings settings() {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(100.0)),
                0.0,
                Double.POSITIVE_INFINITY,
                true);
    }
}
