package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConsensusMaskBuilderTest {

    @Test
    public void consensusUsesMajorityAndOutlierHasLowestAgreement() {
        ImagePlus first = mask("first", new int[][]{
                {255, 255, 0, 0},
                {255, 255, 0, 0}
        });
        ImagePlus second = mask("second", new int[][]{
                {255, 255, 0, 0},
                {255, 255, 0, 0}
        });
        ImagePlus outlier = mask("outlier", new int[][]{
                {0, 0, 0, 255},
                {0, 0, 0, 255}
        });

        ConsensusMaskBuilder.ConsensusResult result = ConsensusMaskBuilder.build(
                Arrays.asList(first, second, outlier));

        assertMaskEquals(first, result.consensusMask);
        assertTrue(result.agreementScores[2] < result.agreementScores[0]);
        assertTrue(result.agreementScores[2] < result.agreementScores[1]);
        assertEquals(result.agreementScores[0], result.agreementScores[1], 1e-12);
    }

    @Test
    public void estimateIncludesMasksConsensusAndOneSliceVotes() {
        ImagePlus mask = mask("mask", new int[][]{
                {255, 0},
                {0, 255}
        });

        long bytes = ConsensusMaskBuilder.estimateRetainedBytes(Arrays.asList(mask, mask, mask));

        assertEquals(32L, bytes);
    }

    @Test
    public void runnerWritesAgreementOnlyWhenAtLeastThreeVariantsSucceed() {
        ThresholdShootoutRunner runner = new ThresholdShootoutRunner();

        List<ShootoutResult> enoughRows = runner.run(
                brightObjectImage(),
                "",
                fixedSettings(Double.valueOf(50.0), Double.valueOf(100.0), Double.valueOf(150.0)));
        List<ShootoutResult> tooFewRows = runner.run(
                brightObjectImage(),
                "",
                fixedSettings(Double.valueOf(50.0), Double.valueOf(100.0)));

        assertEquals(3, enoughRows.size());
        for (ShootoutResult row : enoughRows) {
            assertEquals(1.0, row.agreementScore, 1e-12);
        }
        assertEquals(2, tooFewRows.size());
        assertTrue(Double.isNaN(tooFewRows.get(0).agreementScore));
        assertTrue(Double.isNaN(tooFewRows.get(1).agreementScore));
    }

    private static ImagePlus mask(String title, int[][] values) {
        int height = values.length;
        int width = values[0].length;
        ByteProcessor processor = new ByteProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                processor.set(x, y, values[y][x]);
            }
        }
        return new ImagePlus(title, processor);
    }

    private static ImagePlus brightObjectImage() {
        ByteProcessor processor = new ByteProcessor(4, 4);
        processor.set(1, 1, 200);
        processor.set(1, 2, 200);
        processor.set(2, 1, 200);
        processor.set(2, 2, 200);
        return new ImagePlus("bright", processor);
    }

    private static ShootoutSettings fixedSettings(Double... thresholds) {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Arrays.asList(thresholds),
                ShootoutSettings.DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true);
    }

    private static void assertMaskEquals(ImagePlus expected, ImagePlus actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertEquals(expected.getStackSize(), actual.getStackSize());
        for (int s = 1; s <= expected.getStackSize(); s++) {
            for (int y = 0; y < expected.getHeight(); y++) {
                for (int x = 0; x < expected.getWidth(); x++) {
                    assertEquals(
                            expected.getStack().getProcessor(s).get(x, y),
                            actual.getStack().getProcessor(s).get(x, y));
                }
            }
        }
    }
}
