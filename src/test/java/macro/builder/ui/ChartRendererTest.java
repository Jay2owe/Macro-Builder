package macro.builder.ui;

import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ByteProcessor;
import macro.builder.analysis.ObjectCounter;
import macro.builder.analysis.ShootoutContext;
import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChartRendererTest {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    public void histogramRendererReturnsDefaultSizeAndPaintsThresholds() {
        BufferedImage image = ChartRenderer.renderHistogram(context(0.0, 255.0), rows());

        assertDefaultChartImage(image);
    }

    @Test
    public void curveRendererReturnsDefaultSizeAndPaintsRecommendation() {
        BufferedImage image = ChartRenderer.renderCurve(context(0.0, 255.0), rows());

        assertDefaultChartImage(image);
    }

    @Test
    public void zeroSpanRangeStillRendersBothCharts() {
        ShootoutContext context = context(42.0, 42.0);

        assertDefaultChartImage(ChartRenderer.renderHistogram(context, rows()));
        assertDefaultChartImage(ChartRenderer.renderCurve(context, rows()));
    }

    @Test
    public void repeatedRendersDoNotOpenImageWindows() {
        int before = openWindowCount();
        ShootoutContext context = context(0.0, 255.0);
        List<ShootoutResult> rows = rows();

        for (int i = 0; i < 5; i++) {
            assertNotNull(ChartRenderer.renderHistogram(context, rows));
            assertNotNull(ChartRenderer.renderCurve(context, rows));
        }

        assertEquals(before, openWindowCount());
    }

    private static void assertDefaultChartImage(BufferedImage image) {
        assertNotNull(image);
        assertEquals(ChartRenderer.DEFAULT_WIDTH, image.getWidth());
        assertEquals(ChartRenderer.DEFAULT_HEIGHT, image.getHeight());
        assertTrue(distinctNonBackgroundColours(image) >= 2);
    }

    private static ShootoutContext context(double minimum, double maximum) {
        int[] histogram = new int[256];
        histogram[42] = 130;
        histogram[96] = 18;
        histogram[182] = 160;
        return new ShootoutContext(
                new ImagePlus("chart", new ByteProcessor(2, 2)),
                histogram,
                minimum,
                maximum,
                false);
    }

    private static List<ShootoutResult> rows() {
        List<ShootoutResult> rows = new ArrayList<ShootoutResult>();
        rows.add(result("Grid 64", 64.0, 14));
        rows.add(result("Grid 96", 96.0, 8));
        rows.add(result("Grid 128", 128.0, 8).withRecommendation("count barely changed across this region"));
        rows.add(result("Grid 160", 160.0, 9));
        return rows;
    }

    private static ShootoutResult result(String variant, double threshold, int count) {
        return ShootoutResult.success(
                ShootoutSettings.CountingMode.PARTICLES_2D,
                variant,
                Double.valueOf(threshold),
                0.0,
                255.0,
                null,
                new ObjectCounter.CountSummary(count, count == 0 ? 0.0 : 4.0, count * 4.0, 0.1));
    }

    private static int distinctNonBackgroundColours(BufferedImage image) {
        int background = image.getRGB(0, 0) & 0x00ffffff;
        Set<Integer> colours = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0x00ffffff;
                if (rgb != background) {
                    colours.add(Integer.valueOf(rgb));
                    if (colours.size() >= 2) {
                        return colours.size();
                    }
                }
            }
        }
        return colours.size();
    }

    private static int openWindowCount() {
        int[] ids = WindowManager.getIDList();
        return ids == null ? 0 : ids.length;
    }
}
