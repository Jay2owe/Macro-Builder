package macro.builder.image;

import macro.builder.analysis.BatchMacroInput;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

public class BioFormatsSeriesProviderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void availabilityCheckDoesNotThrow() {
        new BioFormatsSeriesProvider().isAvailable();
    }

    @Test
    public void missingRuntimeListSeriesUsesPlainMessage() throws Exception {
        BioFormatsSeriesProvider provider = new BioFormatsSeriesProvider();
        assumeFalse(provider.isAvailable());
        File container = fakeContainer();

        try {
            provider.listSeries(container);
            fail("Expected missing Bio-Formats runtime failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Bio-Formats"));
            assertFalse(expected.getMessage().contains("ClassNotFoundException"));
        }
    }

    @Test
    public void missingRuntimeOpenSeriesUsesPlainMessage() throws Exception {
        BioFormatsSeriesProvider provider = new BioFormatsSeriesProvider();
        assumeFalse(provider.isAvailable());
        BatchMacroInput input = BatchMacroInput.containerSeries(
                fakeContainer(), 0, "DAPI", 64, 32, 1, 1, 1);

        try {
            provider.openSeries(input);
            fail("Expected missing Bio-Formats runtime failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Bio-Formats"));
            assertFalse(expected.getMessage().contains("ClassNotFoundException"));
        }
    }

    @Test
    public void metadataNamePrefersExplicitNameFields() {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("Series description", "fallback");
        metadata.put("Image name", "Chosen name");

        assertEquals("Chosen name", BioFormatsSeriesProvider.seriesNameFromMetadata(metadata));
    }

    private File fakeContainer() throws Exception {
        File container = temporaryFolder.newFile("sample.lif");
        Files.write(container.toPath(), "not a real container".getBytes(StandardCharsets.UTF_8));
        return container;
    }
}
