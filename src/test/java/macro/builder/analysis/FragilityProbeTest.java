package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import macro.builder.ui.FragilityBarRenderer;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FragilityProbeTest {

    @Test
    public void plateauScoresLowerThanSteepThresholdSlope() {
        ShootoutSettings settings = settings(true);
        int[] plateauSamples = FragilityProbe.probe(
                context(plateauImage(), 0.0, 200.0),
                settings,
                128.0);
        int[] steepSamples = FragilityProbe.probe(
                context(steepSlopeImage(), 0.0, 140.0),
                settings,
                128.0);

        double plateauScore = FragilityProbe.scoreFrom(plateauSamples, 5);
        double steepScore = FragilityProbe.scoreFrom(steepSamples, 3);

        assertEquals(0.0, plateauScore, 1e-12);
        assertTrue("Steep slope should score much higher than plateau",
                steepScore >= plateauScore + 0.3);
    }

    @Test
    public void runnerStoresFragilityWhenEnabledAndLeavesItBlankWhenDisabled() {
        ThresholdShootoutRunner runner = new ThresholdShootoutRunner();
        ShootoutSettings enabled = fixedSettings(true);
        ShootoutSettings disabled = fixedSettings(false);

        List<ShootoutResult> enabledRows = runner.run(steepSlopeImage(), "", enabled);
        List<ShootoutResult> disabledRows = runner.run(steepSlopeImage(), "", disabled);

        assertEquals(1, enabledRows.size());
        assertTrue(Double.isFinite(enabledRows.get(0).fragilityScore));
        assertNotNull(enabledRows.get(0).fragilityCountRange);
        assertEquals(6, enabledRows.get(0).fragilityCountRange.length);

        assertEquals(1, disabledRows.size());
        assertTrue(Double.isNaN(disabledRows.get(0).fragilityScore));
        assertNull(disabledRows.get(0).fragilityCountRange);
    }

    @Test
    public void autoGridFragilityUsesNeighbourCounts() {
        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.AUTO_GRID,
                Collections.<String>emptyList(),
                Collections.<Double>emptyList(),
                6,
                0.0,
                Double.POSITIVE_INFINITY,
                true,
                ShootoutSettings.defaultChannelsToSweep(),
                null,
                true);

        List<ShootoutResult> rows = new ThresholdShootoutRunner().run(steepSlopeImage(), "", settings);

        assertEquals(6, rows.size());
        ShootoutResult middle = rows.get(2);
        assertNotNull(middle.fragilityCountRange);
        assertEquals(4, middle.fragilityCountRange.length);
        assertEquals(rows.get(1).countSummary.count, middle.fragilityCountRange[0]);
        assertEquals(rows.get(3).countSummary.count, middle.fragilityCountRange[1]);
        assertEquals(rows.get(0).countSummary.count, middle.fragilityCountRange[2]);
        assertEquals(rows.get(4).countSummary.count, middle.fragilityCountRange[3]);
    }

    @Test
    public void rendererMapsSteadyAndFragileScoresToDifferentSpanWidths() {
        FragilityBarRenderer.Value steady = FragilityBarRenderer.Value.of(
                0.20,
                100,
                new int[]{90, 110});
        FragilityBarRenderer.Value fragile = FragilityBarRenderer.Value.of(
                0.75,
                100,
                new int[]{25, 175});

        assertTrue(FragilityBarRenderer.spanPixels(steady, 100) <= 25);
        assertTrue(FragilityBarRenderer.spanPixels(fragile, 100) >= 60);
    }

    @Test
    public void scoreFromHandlesZeroCentreCount() {
        assertEquals(0.0, FragilityProbe.scoreFrom(new int[]{0, 0}, 0), 1e-12);
        assertEquals(1.0, FragilityProbe.scoreFrom(new int[]{0, 1}, 0), 1e-12);
    }

    private static ShootoutSettings fixedSettings(boolean runFragilityChecks) {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(128.0)),
                ShootoutSettings.DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true,
                ShootoutSettings.defaultChannelsToSweep(),
                null,
                runFragilityChecks);
    }

    private static ShootoutSettings settings(boolean runFragilityChecks) {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(128.0)),
                ShootoutSettings.DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true,
                ShootoutSettings.defaultChannelsToSweep(),
                null,
                runFragilityChecks);
    }

    private static ShootoutContext context(ImagePlus image, double minimum, double maximum) {
        return new ShootoutContext(image, new int[256], minimum, maximum, false);
    }

    private static ImagePlus plateauImage() {
        ByteProcessor processor = new ByteProcessor(32, 8);
        for (int i = 0; i < 5; i++) {
            fillSquare(processor, 2 + i * 6, 2, 200);
        }
        return new ImagePlus("plateau", processor);
    }

    private static ImagePlus steepSlopeImage() {
        ByteProcessor processor = new ByteProcessor(32, 8);
        int[] values = new int[]{120, 125, 130, 135, 140};
        for (int i = 0; i < values.length; i++) {
            fillSquare(processor, 2 + i * 6, 2, values[i]);
        }
        return new ImagePlus("steep", processor);
    }

    private static void fillSquare(ByteProcessor processor, int startX, int startY, int value) {
        for (int y = startY; y < startY + 2; y++) {
            for (int x = startX; x < startX + 2; x++) {
                processor.set(x, y, value);
            }
        }
    }
}
