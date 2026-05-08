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

    private static File touch(File file) throws Exception {
        Files.write(file.toPath(), new byte[]{1});
        return file;
    }
}
