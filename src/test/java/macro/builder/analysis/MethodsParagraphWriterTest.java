package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MethodsParagraphWriterTest {

    @Test
    public void paragraphMentionsEveryPopulatedScore() {
        ShootoutSettings settings = settingsWithReference();
        ShootoutResult row = scoredResult(settings);
        TestCountsManifest manifest = manifest(row, settings);

        String paragraph = MethodsParagraphWriter.write(manifest);

        assertTrue(paragraph.contains("Triangle"));
        assertTrue(paragraph.contains("Macro-Builder 1.4.2"));
        assertTrue(paragraph.contains("count 7"));
        assertTrue(paragraph.contains("mean object size 2.5"));
        assertTrue(paragraph.contains("coverage 20%"));
        assertTrue(paragraph.contains("range was 0-255"));
        assertTrue(paragraph.contains("precision 1"));
        assertTrue(paragraph.contains("recall 1"));
        assertTrue(paragraph.contains("F1 1"));
        assertTrue(paragraph.contains("separation 0.64"));
        assertTrue(paragraph.contains("distinctness 0.73"));
        assertTrue(paragraph.contains("fragility 0.05"));
        assertTrue(paragraph.contains("agreement 0.88"));
        assertTrue(paragraph.contains("count range of 6-8"));
    }

    @Test
    public void paragraphOmitsAbsentScores() {
        ShootoutSettings settings = new ShootoutSettings(
                ShootoutSettings.CountingMode.PARTICLES_2D,
                ShootoutSettings.ThresholdMode.FIXED_VALUES,
                Collections.<String>emptyList(),
                Collections.singletonList(Double.valueOf(12.0)),
                0.0,
                Double.POSITIVE_INFINITY,
                true);
        ShootoutResult row = ShootoutResult.success(
                ShootoutResult.Source.FIXED,
                ShootoutSettings.CountingMode.PARTICLES_2D,
                "Fixed 12",
                Double.valueOf(12.0),
                0.0,
                255.0,
                null,
                new ObjectCounter.CountSummary(4, 3.0, 12.0, 0.1));

        String paragraph = MethodsParagraphWriter.write(manifest(row, settings));

        assertTrue(paragraph.contains("fixed threshold 12-255"));
        assertFalse(paragraph.contains("precision"));
        assertFalse(paragraph.contains("recall"));
        assertFalse(paragraph.contains("F1"));
        assertFalse(paragraph.contains("agreement"));
        assertFalse(paragraph.contains("NaN"));
        assertFalse(paragraph.contains("null"));
        assertFalse(paragraph.contains("  "));
    }

    private static TestCountsManifest manifest(ShootoutResult row, ShootoutSettings settings) {
        return TestCountsManifest.builder()
                .pluginVersion("1.4.2")
                .fijiVersion("2.16.0")
                .timestamp(Instant.parse("2026-05-16T12:00:00Z"))
                .imageSource(TestCountsManifest.SourceRef.inMemory("image"))
                .macroText("macro")
                .settings(settings)
                .results(Collections.singletonList(row))
                .chosenVariant(row)
                .build();
    }

    private static ShootoutResult scoredResult(ShootoutSettings settings) {
        ByteProcessor processor = new ByteProcessor(1, 1);
        processor.set(0, 0, 255);
        GroundTruthScorer.ScoreSummary score = GroundTruthScorer.score(
                new ImagePlus("mask", processor),
                settings.groundTruthReference,
                settings);
        return ShootoutResult.success(
                ShootoutResult.Source.AUTO,
                ShootoutSettings.CountingMode.PARTICLES_2D,
                "Triangle",
                Double.valueOf(42.0),
                0.0,
                255.0,
                null,
                new ObjectCounter.CountSummary(7, 2.5, 14.0, 0.2))
                .withGroundTruthScore(score)
                .withQualityScores(0.64, 0.73)
                .withFragility(0.05, new int[]{6, 8})
                .withAgreement(0.88);
    }

    private static ShootoutSettings settingsWithReference() {
        GroundTruthReference reference = new GroundTruthReference(
                GroundTruthReference.SourceFormat.CSV_POINTS,
                "truth.csv",
                Collections.singletonList(
                        GroundTruthReference.ReferenceObject.point(0.0, 0.0, 0, 1)));
        return new ShootoutSettings(
                ShootoutSettings.CountingMode.PARTICLES_2D,
                ShootoutSettings.ThresholdMode.AUTO_METHODS,
                Collections.singletonList("Triangle"),
                Collections.<Double>emptyList(),
                10,
                0.0,
                Double.POSITIVE_INFINITY,
                true)
                .withGroundTruthReference(reference);
    }
}
