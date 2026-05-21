package macro.builder.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import macro.builder.ui.MaskPreviewRenderer;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroundTruthScorerTest {

    @Test
    public void scoresPrecisionRecallAndF1WithGreedyMatching() {
        ImagePlus mask = scoredMask();
        GroundTruthReference reference = scoredReference();
        ShootoutSettings settings = settings(reference);

        GroundTruthScorer.ScoreSummary score = GroundTruthScorer.score(mask, reference, settings);

        assertEquals(2, score.tp);
        assertEquals(1, score.fp);
        assertEquals(1, score.fn);
        assertEquals(2.0 / 3.0, score.precision, 1e-6);
        assertEquals(2.0 / 3.0, score.recall, 1e-6);
        assertEquals(2.0 / 3.0, score.f1, 1e-6);
        assertEquals(GroundTruthScorer.DETECTION_TP, score.perObjectStatus[0]);
        assertEquals(GroundTruthScorer.DETECTION_TP, score.perObjectStatus[1]);
        assertEquals(GroundTruthScorer.DETECTION_FP, score.perObjectStatus[2]);
    }

    @Test
    public void pointReferenceOnBoundaryUsesPointInMaskRule() {
        ImagePlus mask = new ImagePlus("point", new ByteProcessor(4, 4));
        mask.getProcessor().set(1, 1, 255);
        GroundTruthReference reference = new GroundTruthReference(
                GroundTruthReference.SourceFormat.CSV_POINTS,
                "point",
                Collections.singletonList(GroundTruthReference.ReferenceObject.point(1.0, 1.0, 0, 1)));

        GroundTruthScorer.ScoreSummary score = GroundTruthScorer.score(mask, reference, settings(reference));

        assertEquals(1, score.tp);
        assertEquals(0, score.fp);
        assertEquals(0, score.fn);
        assertEquals(1.0, score.f1, 1e-6);
    }

    @Test
    public void overlayContainsMatchedMissedAndExtraOutlineColours() {
        ImagePlus mask = scoredMask();
        GroundTruthReference reference = scoredReference();
        ShootoutSettings settings = settings(reference);
        GroundTruthScorer.ScoreSummary score = GroundTruthScorer.score(mask, reference, settings);

        ImagePlus preview = MaskPreviewRenderer.render(
                null,
                mask,
                reference,
                score.perObjectStatus,
                settings,
                false);

        int[] pixels = (int[]) preview.getProcessor().getPixels();
        assertTrue(containsColour(pixels, 0x00b450));
        assertTrue(containsColour(pixels, 0x00bed2));
        assertTrue(containsColour(pixels, 0xdc2d2d));
        preview.flush();
    }

    private static ImagePlus scoredMask() {
        ByteProcessor processor = new ByteProcessor(12, 6);
        fill(processor, 1, 1, 2, 2);
        processor.set(6, 1, 255);
        processor.set(10, 4, 255);
        return new ImagePlus("mask", processor);
    }

    private static GroundTruthReference scoredReference() {
        return new GroundTruthReference(
                GroundTruthReference.SourceFormat.ROI_SET,
                "synthetic",
                Arrays.asList(
                        GroundTruthReference.ReferenceObject.area(new Roi(1, 1, 2, 2), 1),
                        GroundTruthReference.ReferenceObject.point(6.0, 1.0, 0, 2),
                        GroundTruthReference.ReferenceObject.area(new Roi(1, 4, 2, 2), 3)));
    }

    private static ShootoutSettings settings(GroundTruthReference reference) {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(1.0)),
                ShootoutSettings.DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true,
                ShootoutSettings.defaultChannelsToSweep(),
                reference);
    }

    private static void fill(ByteProcessor processor, int x0, int y0, int width, int height) {
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                processor.set(x, y, 255);
            }
        }
    }

    private static boolean containsColour(int[] pixels, int rgb) {
        for (int pixel : pixels) {
            if ((pixel & 0x00ffffff) == rgb) {
                return true;
            }
        }
        return false;
    }
}
