package macro.builder.ui.sandbox.variation;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class MontageExporterTest {

    @Test
    public void gridDimsCreatesNearSquareRowsAndColumns() {
        assertArrayEquals(new int[] { 1, 1 }, MontageExporter.gridDims(1));
        assertArrayEquals(new int[] { 2, 2 }, MontageExporter.gridDims(4));
        assertArrayEquals(new int[] { 3, 3 }, MontageExporter.gridDims(7));
        assertArrayEquals(new int[] { 3, 4 }, MontageExporter.gridDims(10));
    }
}
