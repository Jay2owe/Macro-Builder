package macro.builder.analysis;

import ij.IJ;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    public final ClickFitSnapshot clickFit;

    private TestCountsManifest(Builder builder) {
        this.schemaVersion = builder.schemaVersion;
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
        this.clickFit = builder.clickFit == null
                ? ClickFitSnapshot.from(this.settings, this.chosenVariant, this.results)
                : builder.clickFit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TestCountsManifest read(File file) throws IOException {
        if (file == null) {
            throw new IOException("Sidecar file is required.");
        }
        String path = file.getAbsolutePath();
        String json;
        try {
            json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IOException("Could not read sidecar " + path + ": " + cleanMessage(ex), ex);
        }
        try {
            return parse(json);
        } catch (IOException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IOException("Malformed JSON in sidecar " + path + ": " + cleanMessage(ex), ex);
        }
    }

    static TestCountsManifest parse(String json) throws IOException {
        Object root = new JsonParser(json).parse();
        Map<String, Object> obj = asObject(root, "root");
        int schemaVersion = intField(obj, "schemaVersion", SCHEMA_VERSION);
        if (schemaVersion > SCHEMA_VERSION) {
            throw new IOException("Sidecar schema version " + schemaVersion
                    + " is newer than this plugin's version " + SCHEMA_VERSION
                    + ". Update the plugin and try again.");
        }

        return builder()
                .schemaVersion(schemaVersion)
                .pluginVersion(stringField(obj, "pluginVersion", "dev"))
                .fijiVersion(stringField(obj, "fijiVersion", "headless"))
                .timestamp(stringField(obj, "timestamp",
                        DateTimeFormatter.ISO_INSTANT.format(Instant.now())))
                .imageSource(sourceRefField(obj, "imageSource", SourceRef.inMemory("unknown")))
                .macroSource(hashRefField(obj, "macroSource", HashRef.of(sha256(""))))
                .settings(settingsField(obj, "settings"))
                .resultSnapshots(resultSnapshotsField(obj, "results"))
                .chosenVariant(resultSnapshotField(obj, "chosenVariant", null))
                .groundTruth(sourceRefField(obj, "groundTruth", null))
                .clickFit(clickFitField(obj, "clickFit"))
                .build();
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
        if (clickFit != null) {
            sb.append(",");
            sb.append(quote("clickFit")).append(":");
            appendClickFit(sb, clickFit);
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
        if (!settings.clickPoints.isEmpty()) {
            sb.append(",");
            appendPointArray(sb, "clickPoints", settings.clickPoints);
        }
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

    private static void appendClickFit(StringBuilder sb, ClickFitSnapshot clickFit) {
        sb.append("{");
        appendPointArray(sb, "points", clickFit.points);
        if (clickFit.thresholdValue != null) {
            appendNumberField(sb, "thresholdValue", clickFit.thresholdValue.doubleValue());
        }
        if (clickFit.variant != null) {
            appendStringField(sb, "variant", clickFit.variant);
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

    private static void appendPointArray(StringBuilder sb, String name, List<int[]> values) {
        sb.append(quote(name)).append(":[");
        for (int i = 0; values != null && i < values.size(); i++) {
            if (i > 0) sb.append(",");
            int[] point = values.get(i);
            sb.append("[")
                    .append(point[0])
                    .append(",")
                    .append(point[1])
                    .append(",")
                    .append(point[2])
                    .append("]");
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
        private int schemaVersion = SCHEMA_VERSION;
        private String pluginVersion = "dev";
        private String fijiVersion = detectFijiVersion();
        private String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        private SourceRef imageSource;
        private HashRef macroSource;
        private SettingsSnapshot settings;
        private List<ResultSnapshot> results = Collections.emptyList();
        private ResultSnapshot chosenVariant;
        private SourceRef groundTruth;
        private ClickFitSnapshot clickFit;

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion < 0 ? SCHEMA_VERSION : schemaVersion;
            return this;
        }

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

        public Builder clickFit(ClickFitSnapshot clickFit) {
            this.clickFit = clickFit;
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
        public final List<int[]> clickPoints;
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
            this.clickPoints = immutablePointCopy(safe.clickPoints);
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

        private SettingsSnapshot(
                String countingMode,
                String thresholdMode,
                List<String> autoMethods,
                List<Double> fixedThresholds,
                int gridSteps,
                double minSize,
                double maxSize,
                boolean darkBackground,
                List<Integer> channelsToSweep,
                boolean runFragilityChecks,
                List<int[]> clickPoints,
                Integer groundTruthObjectCount,
                String groundTruthSourceFormat,
                String groundTruthSourceName) {
            this.countingMode = countingMode;
            this.thresholdMode = thresholdMode;
            this.autoMethods = Collections.unmodifiableList(new ArrayList<String>(autoMethods));
            this.fixedThresholds = Collections.unmodifiableList(new ArrayList<Double>(fixedThresholds));
            this.gridSteps = gridSteps;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.darkBackground = darkBackground;
            this.channelsToSweep = Collections.unmodifiableList(new ArrayList<Integer>(channelsToSweep));
            this.runFragilityChecks = runFragilityChecks;
            this.clickPoints = immutablePointCopy(clickPoints);
            this.groundTruthObjectCount = groundTruthObjectCount;
            this.groundTruthSourceFormat = groundTruthSourceFormat;
            this.groundTruthSourceName = groundTruthSourceName;
        }

        public static SettingsSnapshot from(ShootoutSettings settings) {
            return new SettingsSnapshot(settings);
        }
    }

    public static final class ClickFitSnapshot {
        public final List<int[]> points;
        public final Double thresholdValue;
        public final String variant;

        public ClickFitSnapshot(List<int[]> points, Double thresholdValue, String variant) {
            this.points = immutablePointCopy(points);
            this.thresholdValue = thresholdValue;
            this.variant = variant;
        }

        static ClickFitSnapshot from(
                SettingsSnapshot settings,
                ResultSnapshot chosenVariant,
                List<ResultSnapshot> results) {
            if (settings == null || settings.clickPoints.isEmpty()) {
                return null;
            }
            ResultSnapshot row = isClickFit(chosenVariant) ? chosenVariant : firstClickFit(results);
            return new ClickFitSnapshot(
                    settings.clickPoints,
                    row == null ? null : row.thresholdValue,
                    row == null ? null : row.variant);
        }

        private static boolean isClickFit(ResultSnapshot row) {
            return row != null && "CLICK_FIT".equals(row.source);
        }

        private static ResultSnapshot firstClickFit(List<ResultSnapshot> rows) {
            if (rows == null) {
                return null;
            }
            for (int i = 0; i < rows.size(); i++) {
                ResultSnapshot row = rows.get(i);
                if (isClickFit(row)) {
                    return row;
                }
            }
            return null;
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

        private ResultSnapshot(
                String variant,
                String source,
                String countingMode,
                String thresholdLabel,
                Double thresholdValue,
                Double imageMinimum,
                Double imageMaximum,
                String status,
                String error,
                boolean recommended,
                String recommendationReason,
                Integer count,
                Double meanSize,
                Double coverage,
                Double precision,
                Double recall,
                Double f1,
                Double separation,
                Double distinctness,
                Double fragility,
                Integer fragilityRangeMin,
                Integer fragilityRangeMax,
                Double agreement) {
            this.variant = variant;
            this.source = source;
            this.countingMode = countingMode;
            this.thresholdLabel = thresholdLabel;
            this.thresholdValue = thresholdValue;
            this.imageMinimum = imageMinimum;
            this.imageMaximum = imageMaximum;
            this.status = status;
            this.error = error;
            this.recommended = recommended;
            this.recommendationReason = recommendationReason;
            this.count = count;
            this.meanSize = meanSize;
            this.coverage = coverage;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
            this.separation = separation;
            this.distinctness = distinctness;
            this.fragility = fragility;
            this.fragilityRangeMin = fragilityRangeMin;
            this.fragilityRangeMax = fragilityRangeMax;
            this.agreement = agreement;
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

    private static SettingsSnapshot settingsField(Map<String, Object> obj, String key) {
        ShootoutSettings defaults = ShootoutSettings.defaults();
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return SettingsSnapshot.from(defaults);
        }
        Map<String, Object> settings = asObject(obj.get(key), key);
        String countingMode = enumName(
                stringField(settings, "countingMode", defaults.countingMode.name()),
                ShootoutSettings.CountingMode.class,
                "settings.countingMode");
        String thresholdMode = enumName(
                stringField(settings, "thresholdMode", defaults.thresholdMode.name()),
                ShootoutSettings.ThresholdMode.class,
                "settings.thresholdMode");
        List<String> autoMethods = stringArrayField(settings, "autoMethods", defaults.autoMethods);
        List<Double> fixedThresholds = doubleArrayField(
                settings,
                "fixedThresholds",
                defaults.fixedThresholds);
        int gridSteps = intField(settings, "gridSteps", defaults.gridSteps);
        double minSize = doubleField(settings, "minSize", defaults.minSize);
        double maxSize = doubleField(settings, "maxSize", defaults.maxSize);
        boolean darkBackground = booleanField(settings, "darkBackground", defaults.darkBackground);
        List<Integer> channelsToSweep = intArrayField(
                settings,
                "channelsToSweep",
                defaults.channelsToSweep);
        boolean runFragilityChecks = booleanField(
                settings,
                "runFragilityChecks",
                defaults.runFragilityChecks);
        List<int[]> clickPoints = pointArrayField(settings, "clickPoints", defaults.clickPoints);
        Integer groundTruthObjectCount = integerObjectField(settings, "groundTruthObjectCount");
        String groundTruthSourceFormat = stringObjectField(settings, "groundTruthSourceFormat");
        if (groundTruthSourceFormat != null) {
            groundTruthSourceFormat = enumName(
                    groundTruthSourceFormat,
                    GroundTruthReference.SourceFormat.class,
                    "settings.groundTruthSourceFormat");
        }
        String groundTruthSourceName = stringObjectField(settings, "groundTruthSourceName");

        ShootoutSettings checked = new ShootoutSettings(
                ShootoutSettings.CountingMode.valueOf(countingMode),
                ShootoutSettings.ThresholdMode.valueOf(thresholdMode),
                autoMethods,
                fixedThresholds,
                gridSteps,
                minSize,
                maxSize,
                darkBackground,
                channelsToSweep,
                null,
                runFragilityChecks,
                clickPoints);
        return new SettingsSnapshot(
                checked.countingMode.name(),
                checked.thresholdMode.name(),
                checked.autoMethods,
                checked.fixedThresholds,
                checked.gridSteps,
                checked.minSize,
                checked.maxSize,
                checked.darkBackground,
                checked.channelsToSweep,
                checked.runFragilityChecks,
                checked.clickPoints,
                groundTruthObjectCount,
                groundTruthSourceFormat,
                groundTruthSourceName);
    }

    private static List<ResultSnapshot> resultSnapshotsField(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return Collections.emptyList();
        }
        List<Object> rawRows = asArray(obj.get(key), key);
        List<ResultSnapshot> rows = new ArrayList<ResultSnapshot>(rawRows.size());
        for (int i = 0; i < rawRows.size(); i++) {
            rows.add(resultSnapshot(rawRows.get(i), key + "[" + i + "]"));
        }
        return rows;
    }

    private static ResultSnapshot resultSnapshotField(
            Map<String, Object> obj,
            String key,
            ResultSnapshot defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        return resultSnapshot(obj.get(key), key);
    }

    private static ResultSnapshot resultSnapshot(Object value, String label) {
        Map<String, Object> row = asObject(value, label);
        String variant = clean(stringField(row, "variant", "unnamed"), "unnamed");
        String source = enumName(
                stringField(row, "source", ShootoutResult.Source.AUTO.name()),
                ShootoutResult.Source.class,
                label + ".source");
        String countingMode = enumName(
                stringField(row, "countingMode", ShootoutSettings.CountingMode.PARTICLES_2D.name()),
                ShootoutSettings.CountingMode.class,
                label + ".countingMode");
        String status = enumName(
                stringField(row, "status", ShootoutResult.Status.SUCCESS.name()),
                ShootoutResult.Status.class,
                label + ".status");
        return new ResultSnapshot(
                variant,
                source,
                countingMode,
                stringField(row, "thresholdLabel", variant),
                doubleObjectField(row, "thresholdValue"),
                doubleObjectField(row, "imageMinimum"),
                doubleObjectField(row, "imageMaximum"),
                status,
                stringObjectField(row, "error"),
                booleanField(row, "recommended", false),
                stringObjectField(row, "recommendationReason"),
                integerObjectField(row, "count"),
                doubleObjectField(row, "meanSize"),
                doubleObjectField(row, "coverage"),
                doubleObjectField(row, "precision"),
                doubleObjectField(row, "recall"),
                doubleObjectField(row, "f1"),
                doubleObjectField(row, "separation"),
                doubleObjectField(row, "distinctness"),
                doubleObjectField(row, "fragility"),
                integerObjectField(row, "fragilityRangeMin"),
                integerObjectField(row, "fragilityRangeMax"),
                doubleObjectField(row, "agreement"));
    }

    private static SourceRef sourceRefField(Map<String, Object> obj, String key, SourceRef defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        Object value = obj.get(key);
        if (value instanceof String) {
            String path = (String) value;
            if (path.startsWith("in-memory:")) {
                return new SourceRef(path, "", true);
            }
            return SourceRef.file(path, "");
        }
        Map<String, Object> ref = asObject(value, key);
        return SourceRef.file(
                stringField(ref, "path", ""),
                stringField(ref, "sha256", ""));
    }

    private static HashRef hashRefField(Map<String, Object> obj, String key, HashRef defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        Object value = obj.get(key);
        if (value instanceof String) {
            return HashRef.of((String) value);
        }
        Map<String, Object> ref = asObject(value, key);
        return HashRef.of(stringField(ref, "sha256", ""));
    }

    private static ClickFitSnapshot clickFitField(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return null;
        }
        Map<String, Object> clickFit = asObject(obj.get(key), key);
        return new ClickFitSnapshot(
                pointArrayField(clickFit, "points", Collections.<int[]>emptyList()),
                doubleObjectField(clickFit, "thresholdValue"),
                stringObjectField(clickFit, "variant"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String label) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(Object value, String label) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(label + " must be an array");
        }
        return (List<Object>) value;
    }

    private static String stringField(Map<String, Object> obj, String key, String defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        Object value = obj.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return (String) value;
    }

    private static String stringObjectField(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return null;
        }
        return stringField(obj, key, null);
    }

    private static boolean booleanField(Map<String, Object> obj, String key, boolean defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        Object value = obj.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return ((Boolean) value).booleanValue();
    }

    private static int intField(Map<String, Object> obj, String key, int defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        return asInt(obj.get(key), key);
    }

    private static Integer integerObjectField(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return null;
        }
        return Integer.valueOf(asInt(obj.get(key), key));
    }

    private static double doubleField(Map<String, Object> obj, String key, double defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        return asDouble(obj.get(key), key);
    }

    private static Double doubleObjectField(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return null;
        }
        return Double.valueOf(asDouble(obj.get(key), key));
    }

    private static List<String> stringArrayField(
            Map<String, Object> obj,
            String key,
            List<String> defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        List<Object> raw = asArray(obj.get(key), key);
        List<String> values = new ArrayList<String>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object value = raw.get(i);
            if (!(value instanceof String)) {
                throw new IllegalArgumentException(key + "[" + i + "] must be a string");
            }
            values.add((String) value);
        }
        return values;
    }

    private static List<Double> doubleArrayField(
            Map<String, Object> obj,
            String key,
            List<Double> defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        List<Object> raw = asArray(obj.get(key), key);
        List<Double> values = new ArrayList<Double>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            values.add(Double.valueOf(asDouble(raw.get(i), key + "[" + i + "]")));
        }
        return values;
    }

    private static List<Integer> intArrayField(
            Map<String, Object> obj,
            String key,
            List<Integer> defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        List<Object> raw = asArray(obj.get(key), key);
        List<Integer> values = new ArrayList<Integer>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            values.add(Integer.valueOf(asInt(raw.get(i), key + "[" + i + "]")));
        }
        return values;
    }

    private static List<int[]> pointArrayField(
            Map<String, Object> obj,
            String key,
            List<int[]> defaultValue) {
        if (!obj.containsKey(key) || obj.get(key) == null) {
            return defaultValue;
        }
        List<Object> raw = asArray(obj.get(key), key);
        List<int[]> points = new ArrayList<int[]>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            List<Object> point = asArray(raw.get(i), key + "[" + i + "]");
            if (point.size() < 2) {
                throw new IllegalArgumentException(key + "[" + i + "] needs x and y");
            }
            int z = point.size() > 2 ? asInt(point.get(2), key + "[" + i + "][2]") : 1;
            points.add(new int[]{
                    asInt(point.get(0), key + "[" + i + "][0]"),
                    asInt(point.get(1), key + "[" + i + "][1]"),
                    Math.max(1, z)});
        }
        return points;
    }

    private static int asInt(Object value, String label) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(label + " must be a number");
        }
        Number number = (Number) value;
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (d != Math.rint(d)) {
                throw new IllegalArgumentException(label + " must be an integer");
            }
        }
        long longValue = number.longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " is out of range");
        }
        return (int) longValue;
    }

    private static double asDouble(Object value, String label) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("Infinity".equals(text)) {
                return Double.POSITIVE_INFINITY;
            }
            if ("-Infinity".equals(text)) {
                return Double.NEGATIVE_INFINITY;
            }
            if ("NaN".equals(text)) {
                return Double.NaN;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(label + " must be a number");
            }
        }
        throw new IllegalArgumentException(label + " must be a number");
    }

    private static <E extends Enum<E>> String enumName(String value, Class<E> type, String label) {
        try {
            return Enum.valueOf(type, value).name();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(label + " has unsupported value: " + value);
        }
    }

    private static String cleanMessage(Throwable ex) {
        if (ex == null) {
            return "Unknown error";
        }
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static final class JsonParser {
        private final String text;
        private int pos;

        JsonParser(String text) {
            this.text = text == null ? "" : text;
        }

        Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (pos != text.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            char c = text.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Unexpected token");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    pos++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<Object>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    pos++;
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    throw error("Unterminated escape");
                }
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw error("Bad unicode escape");
                        }
                        String hex = text.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException ex) {
                            throw error("Bad unicode escape");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("Bad escape");
                }
            }
            throw error("Unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            if (peek('.')) {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (peek('e') || peek('E')) {
                pos++;
                if (peek('+') || peek('-')) pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            String number = text.substring(start, pos);
            try {
                if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
                    return Double.valueOf(number);
                }
                return Long.valueOf(number);
            } catch (NumberFormatException ex) {
                throw error("Bad number");
            }
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    return;
                }
                pos++;
            }
        }

        private boolean peek(char expected) {
            return pos < text.length() && text.charAt(pos) == expected;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!peek(expected)) {
                throw error("Expected '" + expected + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + pos);
        }
    }

    private static List<int[]> immutablePointCopy(List<int[]> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<int[]> copy = new ArrayList<int[]>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int[] point = values.get(i);
            if (point == null || point.length < 2) {
                continue;
            }
            int z = point.length > 2 ? point[2] : 1;
            copy.add(new int[]{point[0], point[1], Math.max(1, z)});
        }
        return Collections.unmodifiableList(copy);
    }
}
