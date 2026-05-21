package macro.builder.analysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class GroundTruthScorerDefaultPinTest {

    @Test
    public void areaReferenceMatchesAtIouPointFive() {
        ByteProcessor processor = new ByteProcessor(5, 3);
        processor.set(1, 1, 255);
        processor.set(2, 1, 255);
        ImagePlus mask = new ImagePlus("half-overlap", processor);
        GroundTruthReference reference = areaReference();

        GroundTruthScorer.ScoreSummary score =
                GroundTruthScorer.score(mask, reference, settings(reference));

        assertEquals(0.5, GroundTruthScorer.IOU_THRESHOLD, 0.0);
        assertEquals(1, score.tp);
        assertEquals(0, score.fp);
        assertEquals(0, score.fn);
    }

    @Test
    public void areaReferenceDoesNotMatchBelowIouPointFive() {
        ByteProcessor processor = new ByteProcessor(5, 3);
        processor.set(1, 1, 255);
        processor.set(2, 1, 255);
        processor.set(3, 1, 255);
        ImagePlus mask = new ImagePlus("third-overlap", processor);
        GroundTruthReference reference = areaReference();

        GroundTruthScorer.ScoreSummary score =
                GroundTruthScorer.score(mask, reference, settings(reference));

        assertEquals(0, score.tp);
        assertEquals(1, score.fp);
        assertEquals(1, score.fn);
    }

    private static GroundTruthReference areaReference() {
        return new GroundTruthReference(
                GroundTruthReference.SourceFormat.ROI_SET,
                "synthetic",
                Collections.singletonList(GroundTruthReference.ReferenceObject.area(new Roi(1, 1, 1, 1), 1)));
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
}
