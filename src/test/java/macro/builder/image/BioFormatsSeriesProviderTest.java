package macro.builder.image;

import ij.ImagePlus;
import macro.builder.analysis.BatchMacroInput;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

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

    @Test
    public void listAndOpenTinyTwoSeriesFixtureWhenAvailable() throws Exception {
        BioFormatsSeriesProvider provider = new BioFormatsSeriesProvider();
        assumeTrue("Bio-Formats is not on the test classpath.", provider.isAvailable());
        URL resource = getClass().getResource("/tiny_two_series.ome.tif");
        assumeTrue("tiny_two_series.ome.tif fixture is not committed; skipping live Bio-Formats test.",
                resource != null);
        File fixture = new File(resource.toURI());
        assumeTrue("tiny_two_series.ome.tif must stay under 100 kB.", fixture.length() < 100000L);

        List<BatchMacroInput> series = provider.listSeries(fixture);

        assertEquals(2, series.size());
        assertEquals(0, series.get(0).seriesIndex);
        assertEquals(1, series.get(1).seriesIndex);

        ImagePlus opened = provider.openSeries(series.get(0));
        try {
            assertNotNull(opened);
            assertTrue(opened.getWidth() > 0);
            assertTrue(opened.getHeight() > 0);
        } finally {
            closeImageQuietly(opened);
        }
    }

    private File fakeContainer() throws Exception {
        File container = temporaryFolder.newFile("sample.lif");
        Files.write(container.toPath(), "not a real container".getBytes(StandardCharsets.UTF_8));
        return container;
    }

    private static void closeImageQuietly(ImagePlus image) {
        if (image == null) return;
        try {
            image.changes = false;
            if (image.getWindow() != null) {
                image.close();
            } else {
                image.flush();
            }
        } catch (Throwable ignored) {
        }
    }
}
