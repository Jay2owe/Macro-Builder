package macro.builder.api;

import macro.builder.analysis.BatchMacroInput;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacroBuilderInputsApiTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publicApiScansImageFolders() throws Exception {
        File folder = temporaryFolder.newFolder("images");
        File image = new File(folder, "sample_DAPI.tif");
        File ignored = new File(folder, "sample.txt");
        Files.write(image.toPath(), "not a real tif".getBytes(StandardCharsets.UTF_8));
        Files.write(ignored.toPath(), "text".getBytes(StandardCharsets.UTF_8));

        List<BatchMacroInput> rows = MacroBuilderInputs.scanFolder(
                folder,
                "(?i).*_DAPI\\.tif",
                false);

        assertEquals(1, rows.size());
        assertEquals(image.getName(), rows.get(0).relativePath);
        assertTrue(MacroBuilderInputs.isDirectImageFile(image));
        assertFalse(MacroBuilderInputs.isDirectImageFile(ignored));
    }

    @Test
    public void publicApiExposesBioFormatsAvailabilityChecks() throws Exception {
        File container = temporaryFolder.newFile("sample.lif");

        MacroBuilderInputs.isBioFormatsAvailable();
        assertTrue(MacroBuilderInputs.bioFormatsUnavailableMessage().contains("Bio-Formats"));
        assertTrue(MacroBuilderInputs.isBioFormatsContainer(container));
    }
}
