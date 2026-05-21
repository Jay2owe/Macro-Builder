package macro.builder.analysis;

import java.util.ArrayList;
import java.util.List;

public final class MethodsParagraphWriter {
    private MethodsParagraphWriter() {
    }

    public static String write(TestCountsManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest must not be null");
        }
        TestCountsManifest.ResultSnapshot row = chosenRow(manifest);
        if (row == null) {
            return "Images were evaluated with Macro-Builder "
                    + safe(manifest.pluginVersion) + " (Fiji " + safe(manifest.fijiVersion)
                    + "), but no successful threshold variant was selected.";
        }

        List<String> sentences = new ArrayList<String>();
        sentences.add("Images were thresholded with " + thresholdDescription(row)
                + " using Macro-Builder " + safe(manifest.pluginVersion)
                + " (Fiji " + safe(manifest.fijiVersion) + ")");

        List<String> countParts = new ArrayList<String>();
        if (row.count != null) {
            countParts.add("count " + row.count);
        }
        if (row.meanSize != null) {
            countParts.add("mean object size " + number(row.meanSize.doubleValue()) + " pixels");
        }
        if (row.coverage != null) {
            countParts.add("coverage " + number(row.coverage.doubleValue() * 100.0) + "%");
        }
        if (!countParts.isEmpty()) {
            sentences.add("The accepted row reported " + join(countParts));
        }

        if (row.imageMinimum != null && row.imageMaximum != null) {
            sentences.add("The macro-output range was "
                    + number(row.imageMinimum.doubleValue()) + "-"
                    + number(row.imageMaximum.doubleValue()));
        }

        List<String> scoreParts = new ArrayList<String>();
        addScore(scoreParts, "precision", row.precision);
        addScore(scoreParts, "recall", row.recall);
        addScore(scoreParts, "F1", row.f1);
        addScore(scoreParts, "separation", row.separation);
        addScore(scoreParts, "distinctness", row.distinctness);
        addScore(scoreParts, "fragility", row.fragility);
        addScore(scoreParts, "agreement", row.agreement);
        if (!scoreParts.isEmpty()) {
            String prefix = manifest.settings.groundTruthObjectCount == null
                    ? "Scores were "
                    : "Counts were validated against "
                    + manifest.settings.groundTruthObjectCount + " reference objects (";
            if (manifest.settings.groundTruthObjectCount == null) {
                sentences.add(prefix + join(scoreParts));
            } else {
                sentences.add(prefix + join(scoreParts) + ")");
            }
        }

        if (row.fragilityRangeMin != null && row.fragilityRangeMax != null) {
            sentences.add("Threshold sensitivity gave a count range of "
                    + row.fragilityRangeMin + "-" + row.fragilityRangeMax);
        }

        return punctuate(sentences);
    }

    private static TestCountsManifest.ResultSnapshot chosenRow(TestCountsManifest manifest) {
        if (manifest.chosenVariant != null) {
            return manifest.chosenVariant;
        }
        for (TestCountsManifest.ResultSnapshot row : manifest.results) {
            if (row != null && row.recommended) {
                return row;
            }
        }
        for (TestCountsManifest.ResultSnapshot row : manifest.results) {
            if (row != null && "SUCCESS".equals(row.status)) {
                return row;
            }
        }
        return null;
    }

    private static String thresholdDescription(TestCountsManifest.ResultSnapshot row) {
        if ("AUTO".equals(row.source)) {
            return safe(row.variant) + " automatic threshold";
        }
        if (row.thresholdValue != null && row.imageMaximum != null) {
            String label = "GRID".equals(row.source) ? "grid threshold" : "fixed threshold";
            return label + " " + number(row.thresholdValue.doubleValue())
                    + "-" + number(row.imageMaximum.doubleValue());
        }
        if (row.thresholdValue != null) {
            String label = "GRID".equals(row.source) ? "grid threshold" : "fixed threshold";
            return label + " " + number(row.thresholdValue.doubleValue());
        }
        return safe(row.variant);
    }

    private static void addScore(List<String> parts, String label, Double value) {
        if (value != null) {
            parts.add(label + " " + number(value.doubleValue()));
        }
    }

    private static String punctuate(List<String> sentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (sentence == null || sentence.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append(sentence.trim());
            if (!sentence.endsWith(".")) {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    private static String join(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(i == parts.size() - 1 ? ", and " : ", ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String number(double value) {
        return TestCountsManifest.formatNumber(value);
    }

    private static String safe(String text) {
        return text == null || text.trim().isEmpty() ? "unknown" : text.trim();
    }
}
