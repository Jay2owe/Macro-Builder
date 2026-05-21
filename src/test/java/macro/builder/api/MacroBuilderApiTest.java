package macro.builder.api;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import macro.builder.Macro_Builder;
import macro.builder.analysis.BatchMacroInput;
import macro.builder.analysis.BatchMacroResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MacroBuilderApiTest {

    private static final String ADD_FIVE_MACRO = "run(\"Add...\", \"value=5\");\n";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publicApiRunsBatchMacroAndWritesCsv() throws Exception {
        File source = temporaryFolder.newFile("source.tif");
        saveTiff(byteImage("source", new int[][]{{10}}), source);
        File outputFolder = temporaryFolder.newFolder("api-output");
        File csv = new File(outputFolder, "api.csv");

        MacroBuilderResult result = MacroBuilder.runBatch(
                MacroBuilderParameters.builder()
                        .addInput(BatchMacroInput.file(source, source.getName()))
                        .macro(ADD_FIVE_MACRO)
                        .outputDirectory(outputFolder)
                        .csvFile(csv)
                        .build());

        assertEquals(1, result.rows().size());
        assertEquals(BatchMacroResult.Status.SUCCESS, result.rows().get(0).status);
        assertEquals(1, result.successCount());
        assertTrue(csv.isFile());
        assertEquals(15, firstPixel(result.rows().get(0).outputFile));
        assertEquals(10, firstPixel(source));
    }

    @Test
    public void macroBuilderCommandRunsBatchMacroFromOptions() throws Exception {
        File inputFolder = temporaryFolder.newFolder("input folder");
        File outputFolder = temporaryFolder.newFolder("output folder");
        File source = new File(inputFolder, "source image.tif");
        saveTiff(byteImage("source", new int[][]{{20}}), source);
        File macroFile = temporaryFolder.newFile("filter macro.ijm");
        Files.write(macroFile.toPath(), ADD_FIVE_MACRO.getBytes(StandardCharsets.UTF_8));

        new Macro_Builder().run(
                "macro=[" + macroFile.getAbsolutePath() + "] "
                        + "input=[" + inputFolder.getAbsolutePath() + "] "
                        + "output=[" + outputFolder.getAbsolutePath() + "] "
                        + "csv=[command.csv]");

        File csv = new File(outputFolder, "command.csv");
        File output = new File(outputFolder, "source_image_MacroBuilder.tif");
        assertTrue(csv.isFile());
        assertTrue(output.isFile());
        assertEquals(25, firstPixel(output));
    }

    @Test
    public void parametersCanBeBuiltFromMacroOptions() throws Exception {
        File inputFolder = temporaryFolder.newFolder("input");
        File source = new File(inputFolder, "source.tif");
        saveTiff(byteImage("source", new int[][]{{3}}), source);
        File outputFolder = temporaryFolder.newFolder("output");
        File macroFile = temporaryFolder.newFile("filter.ijm");
        Files.write(macroFile.toPath(), ADD_FIVE_MACRO.getBytes(StandardCharsets.UTF_8));

        MacroBuilderParameters parameters = MacroBuilderParameters.fromMacroOptions(
                "macro=[" + macroFile.getAbsolutePath() + "] "
                        + "input=[" + inputFolder.getAbsolutePath() + "] "
                        + "output=[" + outputFolder.getAbsolutePath() + "] "
                        + "recursive=false csv=[none]");

        assertEquals(1, parameters.inputs().size());
        assertEquals(ADD_FIVE_MACRO, parameters.macro());
        assertEquals(null, parameters.csvFile());
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

    private static int firstPixel(File file) {
        ImagePlus opened = IJ.openImage(file.getAbsolutePath());
        try {
            assertTrue(opened != null);
            return opened.getProcessor().get(0, 0);
        } finally {
            if (opened != null) {
                opened.flush();
            }
        }
    }
}
