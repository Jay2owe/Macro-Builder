package macro.builder.analysis;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BatchHeatmapModelTest {

    @Test
    public void parsesCsvByHeaderNameAndShapesMatrix() {
        String csv = ""
                + "variant,channel_index,file,f1,series_index,count,threshold_value,fragility_score,agreement_score,status\n"
                + "\"Otsu\",1,\"C:\\data\\a,file.tif\",0.90,,10,42,0.20,0.70,SUCCESS\n"
                + "\"Triangle\",1,\"C:\\data\\a,file.tif\",,,15,50,0.40,,SUCCESS\n"
                + "\"Otsu\",2,\"C:\\data\\b.tif\",0.80,3,,55,,0.50,SUCCESS\n";

        BatchHeatmapModel model = BatchHeatmapModel.fromCsvText(csv);

        assertEquals(2, model.rowCount());
        assertEquals(2, model.columnCount());
        assertEquals("Otsu", model.columnLabel(0));
        assertEquals("Triangle", model.columnLabel(1));
        assertTrue(model.rowLabel(0).contains("a,file.tif"));
        assertTrue(model.rowLabel(0).contains("C1"));
        assertTrue(model.rowLabel(1).contains("series 3"));
        assertTrue(model.rowLabel(1).contains("C2"));

        double[][] counts = model.matrix(BatchHeatmapModel.MetricKind.COUNT);
        assertEquals(10.0, counts[0][0], 0.0001);
        assertEquals(15.0, counts[0][1], 0.0001);
        assertTrue(Double.isNaN(counts[1][0]));
        assertTrue(Double.isNaN(counts[1][1]));

        double[][] f1 = model.matrix(BatchHeatmapModel.MetricKind.F1);
        assertEquals(0.90, f1[0][0], 0.0001);
        assertTrue(Double.isNaN(f1[0][1]));
        assertEquals(0.80, f1[1][0], 0.0001);
        assertTrue(Double.isNaN(f1[1][1]));

        double[][] normalised = model.matrix(BatchHeatmapModel.MetricKind.COUNT, true);
        assertEquals(0.0, normalised[0][0], 0.0001);
        assertEquals(1.0, normalised[0][1], 0.0001);

        BatchHeatmapModel.Cell cell = model.cellAt(1, 0);
        assertNotNull(cell);
        assertEquals("C:\\data\\b.tif", cell.filePath);
        assertEquals(3, cell.seriesIndex);
        assertEquals(2, cell.channelIndex);
        assertEquals("Otsu", cell.variant);
        assertEquals(55.0, cell.thresholdValue.doubleValue(), 0.0001);
    }

    @Test
    public void availableMetricsSkipColumnsWithNoData() {
        String csv = ""
                + "file,series_index,channel_index,variant,count,f1,fragility_score,agreement_score\n"
                + "a.tif,,1,Otsu,10,,,\n";

        List<BatchHeatmapModel.MetricKind> metrics = BatchHeatmapModel.fromCsvText(csv).availableMetrics();

        assertEquals(1, metrics.size());
        assertEquals(BatchHeatmapModel.MetricKind.COUNT, metrics.get(0));
    }
}
