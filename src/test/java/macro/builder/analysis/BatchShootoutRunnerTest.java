package macro.builder.analysis;

import ij.ImagePlus;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BatchShootoutRunnerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void runsFixedThresholdShootoutForEachOrdinaryTiff() throws Exception {
        File first = temporaryFolder.newFile("first.tif");
        File second = temporaryFolder.newFile("second.tif");
        saveTiff(shortImage("first", new int[][]{
                {1000, 1000, 1000, 1000, 1000, 1000},
                {1000, 2000, 2100, 1000, 3000, 1000},
                {1000, 1000, 1000, 1000, 1000, 1000}
        }), first);
        saveTiff(byteImage("second", new int[][]{
                {0, 0, 0, 0},
                {0, 200, 0, 200},
                {0, 0, 0, 0}
        }), second);

        List<BatchShootoutResult> rows = new BatchShootoutRunner().run(
                Arrays.asList(first, second),
                "",
                fixedSettings(2000.0),
                null);

        assertEquals(2, rows.size());
        assertEquals(first.getAbsolutePath(), rows.get(0).filePath);
        assertEquals(BatchShootoutResult.Status.SUCCESS, rows.get(0).status);
        assertEquals("Fixed 2000", rows.get(0).variant);
        assertEquals(2, rows.get(0).countSummary.count);
        assertEquals(1000.0, rows.get(0).imageMinimum, 0.0001);
        assertEquals(3000.0, rows.get(0).imageMaximum, 0.0001);
        assertEquals(BatchShootoutResult.Status.SUCCESS, rows.get(1).status);
        assertEquals(0, rows.get(1).countSummary.count);

        String csv = BatchShootoutRunner.buildCsv(rows);
        assertTrue(csv.startsWith("file,title,width,height,channels,slices,frames,counting_mode,variant,"
                + "threshold_value,count,mean_size,coverage,range_min,range_max,status,error\n"));
        assertTrue(csv.contains(first.getAbsolutePath()));
    }

    @Test
    public void failedImageProducesErrorRowAndDoesNotStopRemainingFiles() throws Exception {
        File broken = temporaryFolder.newFile("broken.tif");
        Files.write(broken.toPath(), "not a tiff".getBytes(StandardCharsets.UTF_8));
        File good = temporaryFolder.newFile("good.tif");
        saveTiff(byteImage("good", new int[][]{
                {0, 0, 0},
                {0, 255, 0},
                {0, 0, 0}
        }), good);

        List<BatchShootoutResult> rows = new BatchShootoutRunner().run(
                Arrays.asList(broken, good),
                "",
                fixedSettings(100.0),
                null);

        assertEquals(2, rows.size());
        assertEquals(BatchShootoutResult.Status.FAILED, rows.get(0).status);
        assertTrue(rows.get(0).error.contains("Fiji could not open"));
        assertEquals(BatchShootoutResult.Status.SUCCESS, rows.get(1).status);
        assertEquals(1, rows.get(1).countSummary.count);
    }

    @Test
    public void folderSelectionExpandsBatchCandidatesAndSkipsOtherFiles() throws Exception {
        File directory = temporaryFolder.newFolder("batch");
        File image = new File(directory, "image.tif");
        File container = new File(directory, "container.czi");
        File notes = new File(directory, "notes.txt");
        saveTiff(byteImage("image", new int[][]{{255}}), image);
        Files.write(container.toPath(), "container".getBytes(StandardCharsets.UTF_8));
        Files.write(notes.toPath(), "notes".getBytes(StandardCharsets.UTF_8));

        List<File> files = BatchShootoutRunner.collectBatchFiles(Collections.singletonList(directory));

        assertEquals(2, files.size());
        assertEquals("container.czi", files.get(0).getName());
        assertEquals("image.tif", files.get(1).getName());
    }

    @Test
    public void batchRunDoesNotLeaveImageWindowsOpen() throws Exception {
        int before = openWindowCount();
        File image = temporaryFolder.newFile("window-check.tif");
        saveTiff(byteImage("window-check", new int[][]{
                {0, 255},
                {0, 0}
        }), image);

        new BatchShootoutRunner().run(
                Collections.singletonList(image),
                "",
                fixedSettings(100.0),
                null);

        assertEquals(before, openWindowCount());
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

    private static ImagePlus shortImage(String title, int[][] values) {
        int height = values.length;
        int width = values[0].length;
        ShortProcessor processor = new ShortProcessor(width, height);
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

    private static int openWindowCount() {
        int[] ids = WindowManager.getIDList();
        return ids == null ? 0 : ids.length;
    }
}
