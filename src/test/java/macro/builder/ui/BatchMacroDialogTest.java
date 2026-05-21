package macro.builder.ui;

import macro.builder.analysis.BatchMacroInput;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BatchMacroDialogTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void inputTableModelTracksTickedRowsWithoutOpeningDialog() throws Exception {
        File inputFolder = temporaryFolder.newFolder("input");
        File nested = new File(inputFolder, "nested");
        assertTrue(nested.mkdirs());
        File first = touch(new File(inputFolder, "first.tif"));
        File second = touch(new File(nested, "second.png"));

        BatchMacroDialog.InputTableModel model = new BatchMacroDialog.InputTableModel();
        model.setInputs(Arrays.asList(
                BatchMacroInput.file(first, "first.tif"),
                BatchMacroInput.file(second, "nested/second.png")), true);

        assertEquals(2, model.getRowCount());
        assertEquals(Boolean.class, model.getColumnClass(0));
        assertEquals(Boolean.TRUE, model.getValueAt(0, 0));
        assertEquals("first.tif", model.getValueAt(0, 1));
        assertEquals("", model.getValueAt(0, 2));
        assertEquals("TIF", model.getValueAt(0, 3));
        assertEquals("second.png", model.getValueAt(1, 1));
        assertEquals("nested", model.getValueAt(1, 2));
        assertEquals("PNG", model.getValueAt(1, 3));

        model.setValueAt(Boolean.FALSE, 0, 0);
        List<BatchMacroInput> selected = model.selectedInputs();

        assertEquals(1, selected.size());
        assertEquals(second.getCanonicalFile(), selected.get(0).file.getCanonicalFile());
    }

    @Test
    public void tableModelCanSelectAndClearAllRows() throws Exception {
        File first = touch(temporaryFolder.newFile("a.tif"));
        File second = touch(temporaryFolder.newFile("b.tif"));

        BatchMacroDialog.InputTableModel model = new BatchMacroDialog.InputTableModel();
        model.setInputs(Arrays.asList(
                BatchMacroInput.file(first, "a.tif"),
                BatchMacroInput.file(second, "b.tif")), false);

        assertTrue(model.selectedInputs().isEmpty());

        model.setAllSelected(true);
        assertEquals(2, model.selectedInputs().size());

        model.setAllSelected(false);
        assertTrue(model.selectedInputs().isEmpty());
    }

    @Test
    public void inputTableModelDisplaysContainerSeriesRows() throws Exception {
        File container = touch(temporaryFolder.newFile("sample.lif"));

        BatchMacroDialog.InputTableModel model = new BatchMacroDialog.InputTableModel();
        model.setInputs(Arrays.asList(
                BatchMacroInput.containerSeries(container, 2, "DAPI", 512, 256, 3, 4, 5)), true);

        assertEquals("Series 3: DAPI", model.getValueAt(0, 1));
        assertEquals("sample.lif", model.getValueAt(0, 2));
        assertEquals("Bio-Formats", model.getValueAt(0, 3));
        assertEquals("512 x 256, C=3, Z=4, T=5", model.getValueAt(0, 4));
    }

    @Test
    public void folderValidationRejectsMissingInputAndFileOutput() throws Exception {
        File input = temporaryFolder.newFolder("real-input");
        File notFolder = temporaryFolder.newFile("not-a-folder.txt");
        File futureOutput = new File(temporaryFolder.getRoot(), "future-output");

        assertEquals(input.getAbsoluteFile(), BatchMacroDialog.inputFolderFromText(input.getAbsolutePath()));
        assertEquals(futureOutput.getAbsoluteFile(),
                BatchMacroDialog.outputFolderFromText(futureOutput.getAbsolutePath()));

        try {
            BatchMacroDialog.inputFolderFromText(new File(temporaryFolder.getRoot(), "missing").getAbsolutePath());
            fail("Expected input folder validation failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Input folder"));
        }

        try {
            BatchMacroDialog.outputFolderFromText(notFolder.getAbsolutePath());
            fail("Expected output folder validation failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Output path"));
        }
    }

    @Test
    public void containerValidationRejectsMissingFile() throws Exception {
        File container = temporaryFolder.newFile("sample.lif");
        assertEquals(container.getAbsoluteFile(),
                BatchMacroDialog.containerFileFromText(container.getAbsolutePath()));

        try {
            BatchMacroDialog.containerFileFromText(
                    new File(temporaryFolder.getRoot(), "missing.lif").getAbsolutePath());
            fail("Expected container file validation failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Container file"));
        }
    }

    @Test
    public void inputModeValidationRejectsMixedOrWrongRows() throws Exception {
        File image = touch(temporaryFolder.newFile("image.tif"));
        File container = touch(temporaryFolder.newFile("sample.lif"));
        BatchMacroInput fileInput = BatchMacroInput.file(image, "image.tif");
        BatchMacroInput containerInput = BatchMacroInput.containerSeries(
                container, 0, "DAPI", 512, 256, 1, 1, 1);

        assertTrue(BatchMacroDialog.inputsMatchMode(Arrays.asList(fileInput), false));
        assertTrue(BatchMacroDialog.inputsMatchMode(Arrays.asList(containerInput), true));
        assertFalse(BatchMacroDialog.inputsMatchMode(Arrays.asList(fileInput), true));
        assertFalse(BatchMacroDialog.inputsMatchMode(Arrays.asList(containerInput), false));
        assertFalse(BatchMacroDialog.inputsMatchMode(Arrays.asList(fileInput, containerInput), false));
        assertFalse(BatchMacroDialog.inputsMatchMode(Arrays.asList(fileInput, containerInput), true));
    }

    private static File touch(File file) throws Exception {
        Files.write(file.toPath(), new byte[]{1});
        return file;
    }
}
