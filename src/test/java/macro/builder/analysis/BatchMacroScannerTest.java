package macro.builder.analysis;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BatchMacroScannerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void regexMustMatchWholeFilename() throws Exception {
        touch(new File(temporaryFolder.getRoot(), "cell.tif"));

        List<BatchMacroInput> substringOnly = new BatchMacroScanner().scanFolder(
                temporaryFolder.getRoot(),
                "cell",
                false);

        assertTrue(substringOnly.isEmpty());

        List<BatchMacroInput> exact = new BatchMacroScanner().scanFolder(
                temporaryFolder.getRoot(),
                "cell\\.tif",
                false);

        assertEquals(1, exact.size());
        assertEquals("cell.tif", exact.get(0).file.getName());
    }

    @Test
    public void recursiveScanPreservesRelativePaths() throws Exception {
        touch(new File(temporaryFolder.getRoot(), "root.tif"));
        File subfolder = temporaryFolder.newFolder("subfolder");
        touch(new File(subfolder, "nested.tif"));

        BatchMacroScanner scanner = new BatchMacroScanner();
        List<BatchMacroInput> nonRecursive = scanner.scanFolder(
                temporaryFolder.getRoot(),
                "(?i).*\\.tif",
                false);
        List<BatchMacroInput> recursive = scanner.scanFolder(
                temporaryFolder.getRoot(),
                "(?i).*\\.tif",
                true);

        assertEquals(1, nonRecursive.size());
        assertEquals("root.tif", nonRecursive.get(0).relativePath);
        assertEquals(2, recursive.size());
        assertEquals("root.tif", recursive.get(0).relativePath);
        assertEquals("subfolder/nested.tif", recursive.get(1).relativePath);
    }

    @Test
    public void invalidRegexIsReportedToCaller() throws Exception {
        try {
            new BatchMacroScanner().scanFolder(temporaryFolder.getRoot(), "(", false);
            fail("Expected PatternSyntaxException");
        } catch (PatternSyntaxException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    @Test
    public void resultsAreSortedDeterministicallyAndUnsupportedFilesAreSkipped() throws Exception {
        touch(new File(temporaryFolder.getRoot(), "zeta.tif"));
        touch(new File(temporaryFolder.getRoot(), "alpha.PNG"));
        touch(new File(temporaryFolder.getRoot(), "notes.txt"));
        File subfolder = temporaryFolder.newFolder("middle");
        touch(new File(subfolder, "beta.jpg"));

        List<BatchMacroInput> rows = new BatchMacroScanner().scanFolder(
                temporaryFolder.getRoot(),
                ".*",
                true);

        assertEquals(3, rows.size());
        assertEquals("alpha.PNG", rows.get(0).relativePath);
        assertEquals("middle/beta.jpg", rows.get(1).relativePath);
        assertEquals("zeta.tif", rows.get(2).relativePath);
    }

    @Test
    public void duplicateFileSelectionsCollapseByCanonicalPath() throws Exception {
        File image = new File(temporaryFolder.getRoot(), "same.tif");
        touch(image);

        List<BatchMacroInput> rows = new BatchMacroScanner().scanFiles(
                temporaryFolder.getRoot(),
                Arrays.asList(image, image.getAbsoluteFile(), image.getCanonicalFile()),
                Pattern.compile(".*\\.tif"));

        assertEquals(1, rows.size());
        assertEquals("same.tif", rows.get(0).relativePath);
    }

    private static void touch(File file) throws Exception {
        Files.write(file.toPath(), new byte[]{1});
    }
}
