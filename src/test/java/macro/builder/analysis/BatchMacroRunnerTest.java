package macro.builder.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BatchMacroRunnerTest {

    private static final String ADD_FIVE_MACRO = "run(\"Add...\", \"value=5\");\n";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void runsMacroForOrdinaryTiffAndSavesOutput() throws Exception {
        File inputFolder = temporaryFolder.newFolder("input");
        File nestedFolder = new File(inputFolder, "nested");
        assertTrue(nestedFolder.mkdirs());
        File source = new File(nestedFolder, "source.tif");
        saveTiff(byteImage("source", new int[][]{{10, 20}}), source);
        File outputFolder = temporaryFolder.newFolder("output");

        List<BatchMacroResult> results = new BatchMacroRunner().run(
                Collections.singletonList(BatchMacroInput.file(source, "nested/source.tif")),
                ADD_FIVE_MACRO,
                outputFolder,
                null);

        assertEquals(1, results.size());
        BatchMacroResult result = results.get(0);
        assertEquals(BatchMacroResult.Status.SUCCESS, result.status);
        File expectedOutput = new File(new File(outputFolder, "nested"), "source_MacroBuilder.tif");
        assertEquals(expectedOutput.getCanonicalFile(), result.outputFile.getCanonicalFile());
        assertTrue(result.outputFile.isFile());
        assertEquals(15, firstPixel(result.outputFile));
        assertEquals(10, firstPixel(source));
        assertEquals(2, result.width);
        assertEquals(1, result.height);
        assertEquals(1, result.channels);
        assertEquals(1, result.slices);
        assertEquals(1, result.frames);
    }

    @Test
    public void savesStacksAsTiffStacks() throws Exception {
        File source = temporaryFolder.newFile("stack.tif");
        saveTiffStack(stackImage("stack"), source);
        File outputFolder = temporaryFolder.newFolder("stack-output");

        List<BatchMacroResult> results = new BatchMacroRunner().run(
                Collections.singletonList(BatchMacroInput.file(source, "stack.tif")),
                ADD_FIVE_MACRO,
                outputFolder,
                null);

        assertEquals(BatchMacroResult.Status.SUCCESS, results.get(0).status);
        ImagePlus opened = IJ.openImage(results.get(0).outputFile.getAbsolutePath());
        try {
            assertTrue(opened != null);
            assertEquals(2, opened.getStackSize());
            assertEquals(6, opened.getStack().getProcessor(1).get(0, 0));
            assertEquals(7, opened.getStack().getProcessor(2).get(0, 0));
        } finally {
            closeImageQuietly(opened);
        }
    }

    @Test
    public void failedImageProducesErrorRowAndDoesNotStopLaterValidImage() throws Exception {
        File broken = temporaryFolder.newFile("broken.tif");
        Files.write(broken.toPath(), "not a tiff".getBytes(StandardCharsets.UTF_8));
        File good = temporaryFolder.newFile("good.tif");
        saveTiff(byteImage("good", new int[][]{{3}}), good);
        File outputFolder = temporaryFolder.newFolder("failure-output");

        List<BatchMacroResult> results = new BatchMacroRunner().run(
                Arrays.asList(
                        BatchMacroInput.file(broken, "broken.tif"),
                        BatchMacroInput.file(good, "good.tif")),
                ADD_FIVE_MACRO,
                outputFolder,
                null);

        assertEquals(2, results.size());
        assertEquals(BatchMacroResult.Status.FAILED, results.get(0).status);
        assertTrue(results.get(0).error.contains("Fiji could not open"));
        assertEquals(BatchMacroResult.Status.SUCCESS, results.get(1).status);
        assertEquals(8, firstPixel(results.get(1).outputFile));
    }

    @Test
    public void csvContainsSourceOutputStatusAndErrorColumns() throws Exception {
        File source = temporaryFolder.newFile("csv-source.tif");
        File output = new File(temporaryFolder.getRoot(), "csv-output.tif");
        BatchMacroInput input = BatchMacroInput.file(source, "csv-source.tif");
        BatchMacroInput container = BatchMacroInput.containerSeries(
                temporaryFolder.newFile("sample.lif"),
                2,
                "DAPI",
                512,
                256,
                3,
                4,
                5);
        List<BatchMacroResult> rows = Arrays.asList(
                BatchMacroResult.success(input, output),
                BatchMacroResult.failed(input, "bad, \"quoted\" error"),
                BatchMacroResult.failed(container, "Bio-Formats missing"));

        String csv = BatchMacroRunner.buildCsv(rows);

        assertTrue(csv.startsWith("source,kind,series_index,series_name,width,height,channels,slices,frames,"
                + "output,status,error\n"));
        assertTrue(csv.contains(source.getAbsolutePath()));
        assertTrue(csv.contains(output.getAbsolutePath()));
        assertTrue(csv.contains("SUCCESS"));
        assertTrue(csv.contains("FAILED"));
        assertTrue(csv.contains("\"bad, \"\"quoted\"\" error\""));
        assertTrue(csv.contains("CONTAINER_SERIES,2,DAPI,512,256,3,4,5"));

        File csvFile = new File(temporaryFolder.newFolder("reports"), "summary.csv");
        BatchMacroRunner.writeCsv(csvFile, rows);
        assertEquals(csv, new String(Files.readAllBytes(csvFile.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void containerSeriesOutputNameUsesSeriesIndexAndName() throws Exception {
        File container = temporaryFolder.newFile("sample.lif");
        BatchMacroInput input = BatchMacroInput.containerSeries(
                container, 2, "DAPI nucleus", 512, 256, 1, 1, 1);
        File outputFolder = temporaryFolder.newFolder("container-output");

        File output = BatchMacroRunner.outputFileFor(input, outputFolder);

        assertEquals(new File(outputFolder, "sample_s003_DAPI_nucleus_MacroBuilder.tif"), output);
    }

    @Test
    public void containerSeriesFailureIsReportedAsBatchRow() throws Exception {
        File container = temporaryFolder.newFile("broken.lif");
        Files.write(container.toPath(), "not a real container".getBytes(StandardCharsets.UTF_8));
        BatchMacroInput input = BatchMacroInput.containerSeries(container, 0, "DAPI", 64, 32, 1, 1, 1);

        List<BatchMacroResult> results = new BatchMacroRunner().run(
                Collections.singletonList(input),
                ADD_FIVE_MACRO,
                temporaryFolder.newFolder("container-failure-output"),
                null);

        assertEquals(1, results.size());
        assertEquals(BatchMacroResult.Status.FAILED, results.get(0).status);
        assertTrue(results.get(0).error.contains("Bio-Formats"));
    }

    @Test
    public void batchRunDoesNotLeaveImageWindowsOpen() throws Exception {
        int before = openWindowCount();
        File source = temporaryFolder.newFile("window-check.tif");
        saveTiff(byteImage("window-check", new int[][]{{10}}), source);

        new BatchMacroRunner().run(
                Collections.singletonList(BatchMacroInput.file(source, "window-check.tif")),
                ADD_FIVE_MACRO,
                temporaryFolder.newFolder("window-output"),
                null);

        assertEquals(before, openWindowCount());
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

    private static ImagePlus stackImage(String title) {
        ImageStack stack = new ImageStack(1, 1);
        ByteProcessor first = new ByteProcessor(1, 1);
        first.set(0, 0, 1);
        ByteProcessor second = new ByteProcessor(1, 1);
        second.set(0, 0, 2);
        stack.addSlice("first", first);
        stack.addSlice("second", second);
        return new ImagePlus(title, stack);
    }

    private static void saveTiff(ImagePlus image, File file) {
        assertTrue(new FileSaver(image).saveAsTiff(file.getAbsolutePath()));
        image.flush();
    }

    private static void saveTiffStack(ImagePlus image, File file) {
        assertTrue(new FileSaver(image).saveAsTiffStack(file.getAbsolutePath()));
        image.flush();
    }

    private static int firstPixel(File file) {
        ImagePlus opened = IJ.openImage(file.getAbsolutePath());
        try {
            assertTrue(opened != null);
            return opened.getProcessor().get(0, 0);
        } finally {
            closeImageQuietly(opened);
        }
    }

    private static int openWindowCount() {
        int[] ids = WindowManager.getIDList();
        return ids == null ? 0 : ids.length;
    }

    private static void closeImageQuietly(ImagePlus image) {
        if (image == null) {
            return;
        }
        image.changes = false;
        if (image.getWindow() != null) {
            image.close();
        } else {
            image.flush();
        }
    }
}
