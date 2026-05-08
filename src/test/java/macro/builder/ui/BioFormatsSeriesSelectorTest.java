package macro.builder.ui;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class BioFormatsSeriesSelectorTest {

    @Test
    public void seriesInfoLabelIncludesOneBasedIndexNameAndDimensions() {
        BioFormatsSeriesSelector.SeriesInfo info =
                new BioFormatsSeriesSelector.SeriesInfo(2, "Embryo crop", 512, 256, 3, 12, 4);

        assertEquals("Series 3: Embryo crop (512 x 256, C=3, Z=12, T=4)", info.toString());
    }

    @Test
    public void seriesInfoLabelFallsBackWhenNameMissing() {
        BioFormatsSeriesSelector.SeriesInfo info =
                new BioFormatsSeriesSelector.SeriesInfo(0, " ", 64, 32, 1, 1, 1);

        assertEquals("Series 1 (64 x 32, C=1, Z=1, T=1)", info.toString());
    }

    @Test
    public void metadataNamePrefersExplicitNameFields() {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("Series description", "fallback");
        metadata.put("Image name", "Chosen name");

        assertEquals("Chosen name", BioFormatsSeriesSelector.seriesNameFromMetadata(metadata));
    }
}
