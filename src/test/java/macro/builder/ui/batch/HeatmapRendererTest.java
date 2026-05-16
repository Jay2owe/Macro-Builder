package macro.builder.ui.batch;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HeatmapRendererTest {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    public void rendersExpectedSizeMissingCellsAndDifferentValues() {
        double[][] matrix = new double[][]{
                {1.0, 2.0},
                {Double.NaN, 3.0}
        };

        BufferedImage image = HeatmapRenderer.render(matrix, ViridisPalette.INSTANCE, 5, 4);

        assertEquals(10, image.getWidth());
        assertEquals(8, image.getHeight());
        assertEquals(HeatmapRenderer.MISSING_RGB, rgb(image, 0, 4));
        assertNotEquals(rgb(image, 0, 0), rgb(image, 5, 0));
    }

    @Test
    public void usesCanonicalViridisEndpoints() {
        assertEquals(256, ViridisPalette.INSTANCE.size());
        assertEquals(0x440154, ViridisPalette.INSTANCE.colour(0.0));
        assertEquals(0x20908C, ViridisPalette.INSTANCE.colour(0.5));
        assertEquals(0xFDE724, ViridisPalette.INSTANCE.colour(1.0));
    }

    @Test
    public void groupsRowsWhenRenderEstimateExceedsCap() {
        double[][] matrix = new double[100][10];
        int groupSize = HeatmapRenderer.rowGroupSize(matrix, 20, 10, 2000L);

        assertTrue(groupSize > 1);
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0x00ffffff;
    }
}
