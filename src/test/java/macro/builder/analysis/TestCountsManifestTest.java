package macro.builder.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestCountsManifestTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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

    @Test
    public void recordsClickFitBlockWhenClickPointsArePresent() {
        ShootoutSettings settings = ShootoutSettings.defaults().withClickPoints(Arrays.asList(
                new int[]{3, 4, 1},
                new int[]{5, 6, 2}));
        ShootoutResult row = ShootoutResult.success(
                ShootoutResult.Source.CLICK_FIT,
                ShootoutSettings.CountingMode.PARTICLES_2D,
                "Click-fit",
                Double.valueOf(12.5),
                0.0,
                255.0,
                null,
                new ObjectCounter.CountSummary(12, 3.5, 42.0, 0.25))
                .withRecommendation("catches 2 of 2 clicked objects with a plausible count of 12.");

        String json = TestCountsManifest.builder()
                .settings(settings)
                .results(Collections.singletonList(row))
                .chosenVariant(row)
                .build()
                .toJson();

        assertTrue(json.contains("\"clickFit\":{\"points\":[[3,4,1],[5,6,2]],"
                + "\"thresholdValue\":12.5,\"variant\":\"Click-fit\"}"));
        assertTrue(json.contains("\"clickPoints\":[[3,4,1],[5,6,2]]"));
        assertTrue(json.contains("\"source\":\"CLICK_FIT\""));
    }

    @Test
    public void readRejectsNewerSchema() throws Exception {
        File file = write("newer.testcounts.json", "{\"schemaVersion\":2}");

        try {
            TestCountsManifest.read(file);
        } catch (IOException ex) {
            assertTrue(ex.getMessage().contains("schema version 2"));
            assertTrue(ex.getMessage().contains("newer than this plugin's version "
                    + TestCountsManifest.SCHEMA_VERSION));
            return;
        }

        throw new AssertionError("Expected newer schema version to be rejected.");
    }

    @Test
    public void readRoundTrip() throws Exception {
        ShootoutSettings settings = settingsWithReference()
                .withRunFragilityChecks(false)
                .withChannelsToSweep(Arrays.asList(Integer.valueOf(1), Integer.valueOf(3)))
                .withClickPoints(Arrays.asList(new int[]{7, 8, 1}, new int[]{9, 10, 2}));
        ShootoutResult scored = scoredResult(settings).withRecommendation("stable plateau");
        ShootoutResult failed = ShootoutResult.failure(
                ShootoutResult.Source.FIXED,
                ShootoutSettings.CountingMode.OBJECTS_3D,
                "Fixed 99",
                Double.valueOf(99.0),
                0.0,
                255.0,
                "No objects");
        List<ShootoutResult> rows = new ArrayList<ShootoutResult>();
        rows.add(scored);
        rows.add(failed);
        TestCountsManifest manifest = TestCountsManifest.builder()
                .pluginVersion("1.2.3")
                .fijiVersion("Fiji 2.16.0")
                .timestamp(Instant.parse("2026-05-16T12:00:00Z"))
                .imageSource(TestCountsManifest.SourceRef.file("C:\\images\\sample.tif", repeat('b', 64)))
                .macroSource(TestCountsManifest.HashRef.of(repeat('c', 64)))
                .settings(settings)
                .results(rows)
                .chosenVariant(scored)
                .groundTruth(TestCountsManifest.SourceRef.file("C:\\truth\\RoiSet.zip", repeat('d', 64)))
                .clickFit(new TestCountsManifest.ClickFitSnapshot(
                        settings.clickPoints,
                        Double.valueOf(21.25),
                        "Click-fit"))
                .build();
        File file = write("full.testcounts.json", manifest.toJson());

        TestCountsManifest read = TestCountsManifest.read(file);

        assertManifestEquals(manifest, read);
    }

    @Test
    public void readMissingOptionalFields() throws Exception {
        File file = write("minimal.testcounts.json", "{\"schemaVersion\":1}");

        TestCountsManifest manifest = TestCountsManifest.read(file);

        assertEquals(1, manifest.schemaVersion);
        assertEquals("dev", manifest.pluginVersion);
        assertEquals("headless", manifest.fijiVersion);
        assertTrue(manifest.imageSource.inMemory);
        assertEquals(ShootoutSettings.defaults().thresholdMode.name(), manifest.settings.thresholdMode);
        assertTrue(manifest.results.isEmpty());
        assertNull(manifest.chosenVariant);
        assertNull(manifest.groundTruth);
    }

    @Test
    public void readMalformedJsonIncludesPath() throws Exception {
        File file = write("bad.testcounts.json", "{\"schemaVersion\":1,");

        try {
            TestCountsManifest.read(file);
        } catch (IOException ex) {
            assertTrue(ex.getMessage().contains(file.getAbsolutePath()));
            return;
        }

        throw new AssertionError("Expected malformed JSON to throw IOException.");
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

    private File write(String name, String text) throws IOException {
        File file = new File(temporaryFolder.getRoot(), name);
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static void assertManifestEquals(TestCountsManifest expected, TestCountsManifest actual) {
        assertEquals(expected.schemaVersion, actual.schemaVersion);
        assertEquals(expected.pluginVersion, actual.pluginVersion);
        assertEquals(expected.fijiVersion, actual.fijiVersion);
        assertEquals(expected.timestamp, actual.timestamp);
        assertSourceRefEquals(expected.imageSource, actual.imageSource);
        assertHashRefEquals(expected.macroSource, actual.macroSource);
        assertSettingsEquals(expected.settings, actual.settings);
        assertEquals(expected.results.size(), actual.results.size());
        for (int i = 0; i < expected.results.size(); i++) {
            assertResultEquals(expected.results.get(i), actual.results.get(i));
        }
        assertResultEquals(expected.chosenVariant, actual.chosenVariant);
        assertSourceRefEquals(expected.groundTruth, actual.groundTruth);
        assertClickFitEquals(expected.clickFit, actual.clickFit);
    }

    private static void assertSourceRefEquals(
            TestCountsManifest.SourceRef expected,
            TestCountsManifest.SourceRef actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertEquals(expected.path, actual.path);
        assertEquals(expected.sha256, actual.sha256);
        assertEquals(expected.inMemory, actual.inMemory);
    }

    private static void assertHashRefEquals(
            TestCountsManifest.HashRef expected,
            TestCountsManifest.HashRef actual) {
        assertEquals(expected.sha256, actual.sha256);
    }

    private static void assertSettingsEquals(
            TestCountsManifest.SettingsSnapshot expected,
            TestCountsManifest.SettingsSnapshot actual) {
        assertEquals(expected.countingMode, actual.countingMode);
        assertEquals(expected.thresholdMode, actual.thresholdMode);
        assertEquals(expected.autoMethods, actual.autoMethods);
        assertEquals(expected.fixedThresholds, actual.fixedThresholds);
        assertEquals(expected.gridSteps, actual.gridSteps);
        assertEquals(expected.minSize, actual.minSize, 0.0);
        assertEquals(expected.maxSize, actual.maxSize, 0.0);
        assertEquals(expected.darkBackground, actual.darkBackground);
        assertEquals(expected.channelsToSweep, actual.channelsToSweep);
        assertEquals(expected.runFragilityChecks, actual.runFragilityChecks);
        assertPointsEqual(expected.clickPoints, actual.clickPoints);
        assertEquals(expected.groundTruthObjectCount, actual.groundTruthObjectCount);
        assertEquals(expected.groundTruthSourceFormat, actual.groundTruthSourceFormat);
        assertEquals(expected.groundTruthSourceName, actual.groundTruthSourceName);
    }

    private static void assertResultEquals(
            TestCountsManifest.ResultSnapshot expected,
            TestCountsManifest.ResultSnapshot actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertEquals(expected.variant, actual.variant);
        assertEquals(expected.source, actual.source);
        assertEquals(expected.countingMode, actual.countingMode);
        assertEquals(expected.thresholdLabel, actual.thresholdLabel);
        assertEquals(expected.thresholdValue, actual.thresholdValue);
        assertEquals(expected.imageMinimum, actual.imageMinimum);
        assertEquals(expected.imageMaximum, actual.imageMaximum);
        assertEquals(expected.status, actual.status);
        assertEquals(expected.error, actual.error);
        assertEquals(expected.recommended, actual.recommended);
        assertEquals(expected.recommendationReason, actual.recommendationReason);
        assertEquals(expected.count, actual.count);
        assertEquals(expected.meanSize, actual.meanSize);
        assertEquals(expected.coverage, actual.coverage);
        assertEquals(expected.precision, actual.precision);
        assertEquals(expected.recall, actual.recall);
        assertEquals(expected.f1, actual.f1);
        assertEquals(expected.separation, actual.separation);
        assertEquals(expected.distinctness, actual.distinctness);
        assertEquals(expected.fragility, actual.fragility);
        assertEquals(expected.fragilityRangeMin, actual.fragilityRangeMin);
        assertEquals(expected.fragilityRangeMax, actual.fragilityRangeMax);
        assertEquals(expected.agreement, actual.agreement);
    }

    private static void assertClickFitEquals(
            TestCountsManifest.ClickFitSnapshot expected,
            TestCountsManifest.ClickFitSnapshot actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertPointsEqual(expected.points, actual.points);
        assertEquals(expected.thresholdValue, actual.thresholdValue);
        assertEquals(expected.variant, actual.variant);
    }

    private static void assertPointsEqual(List<int[]> expected, List<int[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i)[0], actual.get(i)[0]);
            assertEquals(expected.get(i)[1], actual.get(i)[1]);
            assertEquals(expected.get(i)[2], actual.get(i)[2]);
        }
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
