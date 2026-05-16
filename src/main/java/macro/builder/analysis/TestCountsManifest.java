package macro.builder.analysis;

import ij.IJ;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TestCountsManifest {
    public static final int SCHEMA_VERSION = 1;

    public final int schemaVersion;
    public final String pluginVersion;
    public final String fijiVersion;
    public final String timestamp;
    public final SourceRef imageSource;
    public final HashRef macroSource;
    public final SettingsSnapshot settings;
    public final List<ResultSnapshot> results;
    public final ResultSnapshot chosenVariant;
    public final SourceRef groundTruth;

    private TestCountsManifest(Builder builder) {
        this.schemaVersion = SCHEMA_VERSION;
        this.pluginVersion = clean(builder.pluginVersion, "dev");
        this.fijiVersion = clean(builder.fijiVersion, "headless");
        this.timestamp = clean(builder.timestamp, DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        this.imageSource = builder.imageSource == null
                ? SourceRef.inMemory("unknown")
                : builder.imageSource;
        this.macroSource = builder.macroSource == null
                ? HashRef.of(sha256(""))
                : builder.macroSource;
        this.settings = builder.settings == null
                ? SettingsSnapshot.from(ShootoutSettings.defaults())
                : builder.settings;
        this.results = immutableResults(builder.results);
        this.chosenVariant = builder.chosenVariant;
        this.groundTruth = builder.groundTruth;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendField(sb, "schemaVersion", Integer.toString(schemaVersion));
        sb.append(",");
        appendField(sb, "pluginVersion", quote(pluginVersion));
        sb.append(",");
        appendField(sb, "fijiVersion", quote(fijiVersion));
        sb.append(",");
        appendField(sb, "timestamp", quote(timestamp));
        sb.append(",");
        sb.append(quote("imageSource")).append(":");
        appendSourceRef(sb, imageSource);
        sb.append(",");
        sb.append(quote("macroSource")).append(":");
        appendHashRef(sb, macroSource);
        sb.append(",");
        sb.append(quote("settings")).append(":");
        appendSettings(sb, settings);
        sb.append(",");
        sb.append(quote("results")).append(":[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append(",");
            appendResult(sb, results.get(i));
        }
        sb.append("]");
        if (chosenVariant != null) {
            sb.append(",");
            sb.append(quote("chosenVariant")).append(":");
            appendResult(sb, chosenVariant);
        }
        if (groundTruth != null) {
            sb.append(",");
            sb.append(quote("groundTruth")).append(":");
            appendSourceRef(sb, groundTruth);
        }
        sb.append("}");
        return sb.toString();
    }

    public static String detectFijiVersion() {
        try {
            String version = IJ.getFullVersion();
            return version == null || version.trim().isEmpty() ? "headless" : version.trim();
        } catch (Throwable ignored) {
            return "headless";
        }
    }

    public static String sha256(String text) {
        return hex(sha256Digest().digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("File is not readable");
        }
        MessageDigest digest = sha256Digest();
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return hex(digest.digest());
        } finally {
            in.close();
        }
    }

    public static String jsonEscape(String value) {
        String v = value == null ? "" : value;
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\r': sb.append("\\r"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int j = hex.length(); j < 4; j++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1000000000000000.0) {
            return Long.toString(Math.round(value));
        }
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        while (formatted.indexOf('.') >= 0 && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void appendSettings(StringBuilder sb, SettingsSnapshot settings) {
        sb.append("{");
        appendField(sb, "countingMode", quote(settings.countingMode));
        sb.append(",");
        appendField(sb, "thresholdMode", quote(settings.thresholdMode));
        sb.append(",");
        appendStringArray(sb, "autoMethods", settings.autoMethods);
        sb.append(",");
        appendNumberArray(sb, "fixedThresholds", settings.fixedThresholds);
        sb.append(",");
        appendField(sb, "gridSteps", Integer.toString(settings.gridSteps));
        sb.append(",");
        appendField(sb, "minSize", numberOrString(settings.minSize));
        sb.append(",");
        appendField(sb, "maxSize", numberOrString(settings.maxSize));
        sb.append(",");
        appendField(sb, "darkBackground", Boolean.toString(settings.darkBackground));
        sb.append(",");
        appendIntegerArray(sb, "channelsToSweep", settings.channelsToSweep);
        sb.append(",");
        appendField(sb, "runFragilityChecks", Boolean.toString(settings.runFragilityChecks));
        if (settings.groundTruthObjectCount != null) {
            sb.append(",");
            appendField(sb, "groundTruthObjectCount", settings.groundTruthObjectCount.toString());
        }
        if (settings.groundTruthSourceFormat != null) {
            sb.append(",");
            appendField(sb, "groundTruthSourceFormat", quote(settings.groundTruthSourceFormat));
        }
        if (settings.groundTruthSourceName != null) {
            sb.append(",");
            appendField(sb, "groundTruthSourceName", quote(settings.groundTruthSourceName));
        }
        sb.append("}");
    }

    private static void appendResult(StringBuilder sb, ResultSnapshot row) {
        sb.append("{");
        appendField(sb, "variant", quote(row.variant));
        sb.append(",");
        appendField(sb, "source", quote(row.source));
        sb.append(",");
        appendField(sb, "countingMode", quote(row.countingMode));
        sb.append(",");
        appendField(sb, "thresholdLabel", quote(row.thresholdLabel));
        if (row.thresholdValue != null) appendNumberField(sb, "thresholdValue", row.thresholdValue.doubleValue());
        if (row.imageMinimum != null) appendNumberField(sb, "imageMinimum", row.imageMinimum.doubleValue());
        if (row.imageMaximum != null) appendNumberField(sb, "imageMaximum", row.imageMaximum.doubleValue());
        sb.append(",");
        appendField(sb, "status", quote(row.status));
        if (row.error != null) appendStringField(sb, "error", row.error);
        if (row.recommended) appendBooleanField(sb, "recommended", true);
        if (row.recommendationReason != null) appendStringField(sb, "recommendationReason", row.recommendationReason);
        if (row.count != null) appendIntegerField(sb, "count", row.count.intValue());
        if (row.meanSize != null) appendNumberField(sb, "meanSize", row.meanSize.doubleValue());
        if (row.coverage != null) appendNumberField(sb, "coverage", row.coverage.doubleValue());
        if (row.precision != null) appendNumberField(sb, "precision", row.precision.doubleValue());
        if (row.recall != null) appendNumberField(sb, "recall", row.recall.doubleValue());
        if (row.f1 != null) appendNumberField(sb, "f1", row.f1.doubleValue());
        if (row.separation != null) appendNumberField(sb, "separation", row.separation.doubleValue());
        if (row.distinctness != null) appendNumberField(sb, "distinctness", row.distinctness.doubleValue());
        if (row.fragility != null) appendNumberField(sb, "fragility", row.fragility.doubleValue());
        if (row.fragilityRangeMin != null) appendIntegerField(sb, "fragilityRangeMin", row.fragilityRangeMin.intValue());
        if (row.fragilityRangeMax != null) appendIntegerField(sb, "fragilityRangeMax", row.fragilityRangeMax.intValue());
        if (row.agreement != null) appendNumberField(sb, "agreement", row.agreement.doubleValue());
        sb.append("}");
    }

    private static void appendSourceRef(StringBuilder sb, SourceRef ref) {
        if (ref == null) {
            sb.append(quote("in-memory:unknown"));
            return;
        }
        if (ref.inMemory) {
            sb.append(quote(ref.path));
            return;
        }
        sb.append("{");
        appendField(sb, "path", quote(ref.path));
        if (ref.sha256 != null && ref.sha256.length() > 0) {
            sb.append(",");
            appendField(sb, "sha256", quote(ref.sha256));
        }
        sb.append("}");
    }

    private static void appendHashRef(StringBuilder sb, HashRef ref) {
        sb.append("{");
        appendField(sb, "sha256", quote(ref == null ? "" : ref.sha256));
        sb.append("}");
    }

    private static void appendStringArray(StringBuilder sb, String name, List<String> values) {
        sb.append(quote(name)).append(":[");
        for (int i = 0; values != null && i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(quote(values.get(i)));
        }
        sb.append("]");
    }

    private static void appendNumberArray(StringBuilder sb, String name, List<Double> values) {
        sb.append(quote(name)).append(":[");
        for (int i = 0; values != null && i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(numberOrString(values.get(i).doubleValue()));
        }
        sb.append("]");
    }

    private static void appendIntegerArray(StringBuilder sb, String name, List<Integer> values) {
        sb.append(quote(name)).append(":[");
        for (int i = 0; values != null && i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(values.get(i));
        }
        sb.append("]");
    }

    private static void appendNumberField(StringBuilder sb, String name, double value) {
        if (!isFinite(value)) return;
        sb.append(",");
        appendField(sb, name, formatNumber(value));
    }

    private static void appendIntegerField(StringBuilder sb, String name, int value) {
        sb.append(",");
        appendField(sb, name, Integer.toString(value));
    }

    private static void appendBooleanField(StringBuilder sb, String name, boolean value) {
        sb.append(",");
        appendField(sb, name, Boolean.toString(value));
    }

    private static void appendStringField(StringBuilder sb, String name, String value) {
        if (value == null) return;
        sb.append(",");
        appendField(sb, name, quote(value));
    }

    private static String numberOrString(double value) {
        if (Double.isInfinite(value)) {
            return quote(value > 0.0 ? "Infinity" : "-Infinity");
        }
        if (Double.isNaN(value)) {
            return quote("NaN");
        }
        return formatNumber(value);
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        sb.append(quote(name)).append(":").append(value);
    }

    private static String quote(String value) {
        return "\"" + jsonEscape(value) + "\"";
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            if (value < 16) sb.append('0');
            sb.append(Integer.toHexString(value));
        }
        return sb.toString();
    }

    private static List<ResultSnapshot> immutableResults(List<ResultSnapshot> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<ResultSnapshot>(rows));
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static final class Builder {
        private String pluginVersion = "dev";
        private String fijiVersion = detectFijiVersion();
        private String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        private SourceRef imageSource;
        private HashRef macroSource;
        private SettingsSnapshot settings;
        private List<ResultSnapshot> results = Collections.emptyList();
        private ResultSnapshot chosenVariant;
        private SourceRef groundTruth;

        public Builder pluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
            return this;
        }

        public Builder fijiVersion(String fijiVersion) {
            this.fijiVersion = fijiVersion;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = DateTimeFormatter.ISO_INSTANT.format(
                    timestamp == null ? Instant.now() : timestamp);
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder imageSource(SourceRef imageSource) {
            this.imageSource = imageSource;
            return this;
        }

        public Builder macroText(String macroText) {
            this.macroSource = HashRef.of(sha256(macroText));
            return this;
        }

        public Builder macroSource(HashRef macroSource) {
            this.macroSource = macroSource;
            return this;
        }

        public Builder settings(ShootoutSettings settings) {
            this.settings = SettingsSnapshot.from(settings);
            return this;
        }

        public Builder settings(SettingsSnapshot settings) {
            this.settings = settings;
            return this;
        }

        public Builder results(List<ShootoutResult> results) {
            if (results == null || results.isEmpty()) {
                this.results = Collections.emptyList();
                return this;
            }
            List<ResultSnapshot> snapshots = new ArrayList<ResultSnapshot>(results.size());
            for (ShootoutResult row : results) {
                if (row != null) {
                    snapshots.add(ResultSnapshot.from(row));
                }
            }
            this.results = snapshots;
            return this;
        }

        public Builder resultSnapshots(List<ResultSnapshot> results) {
            this.results = results == null
                    ? Collections.<ResultSnapshot>emptyList()
                    : new ArrayList<ResultSnapshot>(results);
            return this;
        }

        public Builder chosenVariant(ShootoutResult chosenVariant) {
            this.chosenVariant = chosenVariant == null ? null : ResultSnapshot.from(chosenVariant);
            return this;
        }

        public Builder chosenVariant(ResultSnapshot chosenVariant) {
            this.chosenVariant = chosenVariant;
            return this;
        }

        public Builder groundTruth(SourceRef groundTruth) {
            this.groundTruth = groundTruth;
            return this;
        }

        public TestCountsManifest build() {
            return new TestCountsManifest(this);
        }
    }

    public static final class SourceRef {
        public final String path;
        public final String sha256;
        public final boolean inMemory;

        private SourceRef(String path, String sha256, boolean inMemory) {
            this.path = path == null || path.length() == 0 ? "in-memory:unknown" : path;
            this.sha256 = sha256 == null ? "" : sha256;
            this.inMemory = inMemory;
        }

        public static SourceRef file(File file, String sha256) {
            String path = file == null ? "" : file.getAbsolutePath();
            return file(path, sha256);
        }

        public static SourceRef file(String path, String sha256) {
            return new SourceRef(path, sha256, false);
        }

        public static SourceRef inMemory(String title) {
            String label = title == null || title.trim().isEmpty() ? "untitled" : title;
            return new SourceRef("in-memory:" + label, "", true);
        }
    }

    public static final class HashRef {
        public final String sha256;

        private HashRef(String sha256) {
            this.sha256 = sha256 == null ? "" : sha256;
        }

        public static HashRef of(String sha256) {
            return new HashRef(sha256);
        }
    }

    public static final class SettingsSnapshot {
        public final String countingMode;
        public final String thresholdMode;
        public final List<String> autoMethods;
        public final List<Double> fixedThresholds;
        public final int gridSteps;
        public final double minSize;
        public final double maxSize;
        public final boolean darkBackground;
        public final List<Integer> channelsToSweep;
        public final boolean runFragilityChecks;
        public final Integer groundTruthObjectCount;
        public final String groundTruthSourceFormat;
        public final String groundTruthSourceName;

        private SettingsSnapshot(ShootoutSettings settings) {
            ShootoutSettings safe = settings == null ? ShootoutSettings.defaults() : settings;
            this.countingMode = safe.countingMode.name();
            this.thresholdMode = safe.thresholdMode.name();
            this.autoMethods = Collections.unmodifiableList(new ArrayList<String>(safe.autoMethods));
            this.fixedThresholds = Collections.unmodifiableList(new ArrayList<Double>(safe.fixedThresholds));
            this.gridSteps = safe.gridSteps;
            this.minSize = safe.minSize;
            this.maxSize = safe.maxSize;
            this.darkBackground = safe.darkBackground;
            this.channelsToSweep = Collections.unmodifiableList(new ArrayList<Integer>(safe.channelsToSweep));
            this.runFragilityChecks = safe.runFragilityChecks;
            if (safe.groundTruthReference == null) {
                this.groundTruthObjectCount = null;
                this.groundTruthSourceFormat = null;
                this.groundTruthSourceName = null;
            } else {
                this.groundTruthObjectCount = Integer.valueOf(safe.groundTruthReference.size());
                this.groundTruthSourceFormat = safe.groundTruthReference.sourceFormat.name();
                this.groundTruthSourceName = safe.groundTruthReference.sourceName;
            }
        }

        public static SettingsSnapshot from(ShootoutSettings settings) {
            return new SettingsSnapshot(settings);
        }
    }

    public static final class ResultSnapshot {
        public final String variant;
        public final String source;
        public final String countingMode;
        public final String thresholdLabel;
        public final Double thresholdValue;
        public final Double imageMinimum;
        public final Double imageMaximum;
        public final String status;
        public final String error;
        public final boolean recommended;
        public final String recommendationReason;
        public final Integer count;
        public final Double meanSize;
        public final Double coverage;
        public final Double precision;
        public final Double recall;
        public final Double f1;
        public final Double separation;
        public final Double distinctness;
        public final Double fragility;
        public final Integer fragilityRangeMin;
        public final Integer fragilityRangeMax;
        public final Double agreement;

        private ResultSnapshot(ShootoutResult row) {
            this.variant = row.variant;
            this.source = row.source.name();
            this.countingMode = row.countingMode.name();
            this.thresholdLabel = row.thresholdLabel;
            this.thresholdValue = row.thresholdValue;
            this.imageMinimum = isFinite(row.imageMinimum) ? Double.valueOf(row.imageMinimum) : null;
            this.imageMaximum = isFinite(row.imageMaximum) ? Double.valueOf(row.imageMaximum) : null;
            this.status = row.status.name();
            this.error = row.error == null || row.error.trim().isEmpty() ? null : row.error;
            this.recommended = row.recommended;
            this.recommendationReason = row.recommendationReason == null
                    || row.recommendationReason.trim().isEmpty()
                    ? null
                    : row.recommendationReason;
            if (row.countSummary == null) {
                this.count = null;
                this.meanSize = null;
                this.coverage = null;
            } else {
                this.count = Integer.valueOf(row.countSummary.count);
                this.meanSize = finiteOrNull(row.countSummary.meanSize);
                this.coverage = finiteOrNull(row.countSummary.coverage);
            }
            this.precision = finiteOrNull(row.precision);
            this.recall = finiteOrNull(row.recall);
            this.f1 = finiteOrNull(row.f1);
            this.separation = finiteOrNull(row.separationScore);
            this.distinctness = finiteOrNull(row.distinctnessScore);
            this.fragility = finiteOrNull(row.fragilityScore);
            if (row.fragilityCountRange == null || row.fragilityCountRange.length == 0) {
                this.fragilityRangeMin = null;
                this.fragilityRangeMax = null;
            } else {
                int min = row.countSummary == null ? row.fragilityCountRange[0] : row.countSummary.count;
                int max = min;
                for (int i = 0; i < row.fragilityCountRange.length; i++) {
                    int count = row.fragilityCountRange[i];
                    if (count < min) min = count;
                    if (count > max) max = count;
                }
                this.fragilityRangeMin = Integer.valueOf(min);
                this.fragilityRangeMax = Integer.valueOf(max);
            }
            this.agreement = finiteOrNull(row.agreementScore);
        }

        public static ResultSnapshot from(ShootoutResult row) {
            if (row == null) {
                throw new IllegalArgumentException("row must not be null");
            }
            return new ResultSnapshot(row);
        }

        private static Double finiteOrNull(double value) {
            return isFinite(value) ? Double.valueOf(value) : null;
        }
    }
}
