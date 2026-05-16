package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestCountsManifestTest {

    @Test
    public void writesSchemaFieldsAndPopulatedScores() {
        String tricky = "memory \\ \" \r \n \t \u0001";
        ShootoutSettings settings = settingsWithReference();
        ShootoutResult row = scoredResult(settings);

        TestCountsManifest manifest = TestCountsManifest.builder()
                .pluginVersion("1.2.3")
                .fijiVersion("Fiji 2.16.0")
                .timestamp(Instant.parse("2026-05-16T12:00:00Z"))
                .imageSource(TestCountsManifest.SourceRef.inMemory(tricky))
                .macroText("run(\"Add...\", \"value=1\");\n" + tricky)
                .settings(settings)
                .results(Collections.singletonList(row))
                .chosenVariant(row)
                .groundTruth(TestCountsManifest.SourceRef.file("C:\\truth\\RoiSet.zip", repeat('a', 64)))
                .build();

        String json = manifest.toJson();

        assertTrue(json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains("\"pluginVersion\":\"1.2.3\""));
        assertTrue(json.contains("\"fijiVersion\":\"Fiji 2.16.0\""));
        assertTrue(json.contains("\"timestamp\":\"2026-05-16T12:00:00Z\""));
        assertTrue(json.contains("\"macroSource\":{\"sha256\":\""));
        assertTrue(json.contains("\"imageSource\":\"in-memory:" + TestCountsManifest.jsonEscape(tricky) + "\""));
        assertTrue(json.contains("\"settings\""));
        assertTrue(json.contains("\"results\""));
        assertTrue(json.contains("\"chosenVariant\""));
        assertTrue(json.contains("\"groundTruth\":{\"path\":\"C:\\\\truth\\\\RoiSet.zip\""));
        assertTrue(json.contains("\"source\":\"GRID\""));
        assertTrue(json.contains("\"count\":12"));
        assertTrue(json.contains("\"meanSize\":3.5"));
        assertTrue(json.contains("\"coverage\":0.25"));
        assertTrue(json.contains("\"precision\":1"));
        assertTrue(json.contains("\"recall\":1"));
        assertTrue(json.contains("\"f1\":1"));
        assertTrue(json.contains("\"separation\":0.61"));
        assertTrue(json.contains("\"distinctness\":0.72"));
        assertTrue(json.contains("\"fragility\":0.08"));
        assertTrue(json.contains("\"agreement\":0.91"));
    }

    @Test
    public void escapeHelperRoundTripsControlCharacters() {
        String tricky = "\\ \" \r \n \t \u0001";

        String escaped = TestCountsManifest.jsonEscape(tricky);

        assertEquals(tricky, unescapeJsonString("\"" + escaped + "\""));
    }

    private static ShootoutResult scoredResult(ShootoutSettings settings) {
        ByteProcessor processor = new ByteProcessor(1, 1);
        processor.set(0, 0, 255);
        ImagePlus mask = new ImagePlus("mask", processor);
        GroundTruthScorer.ScoreSummary score =
                GroundTruthScorer.score(mask, settings.groundTruthReference, settings);
        return ShootoutResult.success(
                ShootoutResult.Source.GRID,
                ShootoutSettings.CountingMode.PARTICLES_2D,
                "Grid 12.5",
                Double.valueOf(12.5),
                0.0,
                255.0,
                null,
                new ObjectCounter.CountSummary(12, 3.5, 42.0, 0.25))
                .withGroundTruthScore(score)
                .withQualityScores(0.61, 0.72)
                .withFragility(0.08, new int[]{10, 14})
                .withAgreement(0.91);
    }

    private static ShootoutSettings settingsWithReference() {
        GroundTruthReference reference = new GroundTruthReference(
                GroundTruthReference.SourceFormat.CSV_POINTS,
                "truth.csv",
                Collections.singletonList(
                        GroundTruthReference.ReferenceObject.point(0.0, 0.0, 0, 1)));
        return new ShootoutSettings(
                ShootoutSettings.CountingMode.PARTICLES_2D,
                ShootoutSettings.ThresholdMode.AUTO_GRID,
                Collections.singletonList("Triangle"),
                Collections.singletonList(Double.valueOf(12.5)),
                10,
                0.0,
                Double.POSITIVE_INFINITY,
                true)
                .withGroundTruthReference(reference);
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }

    private static String unescapeJsonString(String jsonString) {
        String text = jsonString.substring(1, jsonString.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char escaped = text.charAt(++i);
            switch (escaped) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case 'r': sb.append('\r'); break;
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    String hex = text.substring(i + 1, i + 5);
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                    break;
                default: sb.append(escaped); break;
            }
        }
        return sb.toString();
    }
}
