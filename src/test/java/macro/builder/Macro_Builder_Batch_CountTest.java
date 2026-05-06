package macro.builder;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
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

import static org.junit.Assert.assertTrue;

public class Macro_Builder_Batch_CountTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void pluginCommandWritesBatchCountCsv() throws Exception {
        File exportFolder = temporaryFolder.newFolder("export folder");
        BatchMacroExporter.ExportResult export = new BatchMacroExporter().export(
                new File(exportFolder, "Batch_Count.ijm"),
                "// no-op\n",
                fixedSettings(100.0));

        File inputFolder = temporaryFolder.newFolder("input folder");
        File outputFolder = temporaryFolder.newFolder("output folder");
        File image = new File(inputFolder, "count me.tif");
        saveTiff(byteImage("count me", new int[][]{
                {0, 0, 0},
                {0, 255, 0},
                {0, 0, 0}
        }), image);

        new Macro_Builder_Batch_Count().run(
                "settings=[" + export.settingsJson.getAbsolutePath() + "] "
                        + "input=[" + inputFolder.getAbsolutePath() + "] "
                        + "output=[" + outputFolder.getAbsolutePath() + "]");

        File csv = new File(outputFolder, BatchMacroExporter.DEFAULT_RESULTS_FILE);
        assertTrue(csv.isFile());
        String text = new String(Files.readAllBytes(csv.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains(image.getAbsolutePath()));
        assertTrue(text.contains("Fixed 100"));
        assertTrue(text.contains(",SUCCESS,"));
    }

    private static ShootoutSettings fixedSettings(double threshold) {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(threshold)),
                0.0,
                Double.POSITIVE_INFINITY,
                true);
    }

    private static ImagePlus byteImage(String title, int[][] values) {
        int height = values.length;
        int width = values[0].length;
        ByteProcessor processor = new ByteProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                processor.set(x, y, values[y][x]);
            }
        }
        return new ImagePlus(title, processor);
    }

    private static void saveTiff(ImagePlus image, File file) {
        assertTrue(new FileSaver(image).saveAsTiff(file.getAbsolutePath()));
        image.flush();
    }
}
