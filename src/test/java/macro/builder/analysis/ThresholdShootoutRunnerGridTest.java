package macro.builder.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import org.junit.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThresholdShootoutRunnerGridTest {

    @Test
    public void autoGridCreatesTenRowsAndOneRecommendation() {
        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.AUTO_GRID,
                Collections.<String>emptyList(),
                Collections.<Double>emptyList(),
                10,
                0.0,
                Double.POSITIVE_INFINITY,
                true);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                twoObjectImage(),
                "",
                settings);

        assertEquals(10, rows.size());
        int recommended = 0;
        for (int i = 0; i < rows.size(); i++) {
            ShootoutResult row = rows.get(i);
            assertTrue(row.isSuccess());
            assertTrue(row.variant.startsWith("Grid "));
            assertFalse(row.recommended && row.recommendationReason.length() == 0);
            if (row.recommended) {
                recommended++;
                assertEquals(5, i);
                assertEquals(PlateauFinder.DEFAULT_REASON, row.recommendationReason);
            }
        }
        assertEquals(1, recommended);
    }

    @Test
    public void gridThresholdsAreEvenlySpacedAcrossContextRange() {
        ShootoutContext context = new ShootoutContext(
                new ImagePlus("range", new ByteProcessor(1, 1)),
                new int[256],
                5.0,
                14.0,
                false);

        List<Double> thresholds = ThresholdShootoutRunner.gridThresholds(context, 4);

        assertEquals(4, thresholds.size());
        assertEquals(5.0, thresholds.get(0).doubleValue(), 0.0001);
        assertEquals(8.0, thresholds.get(1).doubleValue(), 0.0001);
        assertEquals(11.0, thresholds.get(2).doubleValue(), 0.0001);
        assertEquals(14.0, thresholds.get(3).doubleValue(), 0.0001);
    }

    @Test
    public void referenceLoadedRecommendsHighestF1Row() {
        GroundTruthReference reference = new GroundTruthReference(
                GroundTruthReference.SourceFormat.ROI_SET,
                "synthetic",
                Arrays.asList(
                        GroundTruthReference.ReferenceObject.area(new Roi(1, 1, 2, 2), 1),
                        GroundTruthReference.ReferenceObject.area(new Roi(8, 1, 2, 2), 2)));
        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Arrays.asList(Double.valueOf(100.0), Double.valueOf(255.0)),
                ShootoutSettings.DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true,
                ShootoutSettings.defaultChannelsToSweep(),
                reference);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(
                twoObjectImage(),
                "",
                settings);

        assertEquals(2, rows.size());
        assertEquals(1.0, rows.get(0).f1, 1e-6);
        assertTrue(rows.get(0).recommended);
        assertEquals("highest agreement with your reference", rows.get(0).recommendationReason);
        assertFalse(rows.get(1).recommended);
        assertEquals(0.0, rows.get(1).f1, 1e-6);
    }

    private static ImagePlus twoObjectImage() {
        ByteProcessor processor = new ByteProcessor(12, 5);
        fillSquare(processor, 1, 1, 200);
        fillSquare(processor, 8, 1, 200);
        return new ImagePlus("grid", processor);
    }

    private static void fillSquare(ByteProcessor processor, int startX, int startY, int value) {
        for (int y = startY; y < startY + 2; y++) {
            for (int x = startX; x < startX + 2; x++) {
                processor.set(x, y, value);
            }
        }
    }
}
