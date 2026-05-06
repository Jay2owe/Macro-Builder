package macro.builder.analysis;

import macro.builder.analysis.ShootoutSettings.CountingMode;
import macro.builder.analysis.ShootoutSettings.ThresholdMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BatchMacroExporter {

    public static final String DEFAULT_WRAPPER_NAME = "Macro_Builder_Batch_Count.ijm";
    public static final String DEFAULT_RESULTS_FILE = "Macro_Builder_Batch_Count.csv";

    private static final String DEFAULT_FILTER_SUFFIX = "_Filter.ijm";
    private static final String SETTINGS_SUFFIX = ".settings.json";

    public ExportResult export(File wrapperFile, String macro, ShootoutSettings settings) throws IOException {
        if (wrapperFile == null) {
            throw new IllegalArgumentException("wrapperFile must not be null");
        }
        if (macro == null || macro.trim().isEmpty()) {
            throw new IllegalArgumentException("macro must not be blank");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        File wrapper = ensureExtension(wrapperFile, ".ijm").getAbsoluteFile();
        File directory = wrapper.getParentFile();
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create export folder: " + directory.getAbsolutePath());
        }

        String baseName = baseName(wrapper);
        File filterMacro = new File(directory, baseName + DEFAULT_FILTER_SUFFIX);
        File settingsFile = new File(directory, baseName + SETTINGS_SUFFIX);
        ExportedSettings exportedSettings = new ExportedSettings(
                settingsFile,
                filterMacro.getName(),
                DEFAULT_RESULTS_FILE,
                settings);

        Files.write(filterMacro.toPath(), macro.getBytes(StandardCharsets.UTF_8));
        Files.write(settingsFile.toPath(), toJson(exportedSettings).getBytes(StandardCharsets.UTF_8));
        Files.write(wrapper.toPath(), buildWrapperMacro(settingsFile).getBytes(StandardCharsets.UTF_8));

        return new ExportResult(wrapper, filterMacro, settingsFile);
    }

    public static ExportedSettings readSettings(File settingsFile) throws IOException {
        if (settingsFile == null) {
            throw new IllegalArgumentException("settingsFile must not be null");
        }
        String json = new String(Files.readAllBytes(settingsFile.toPath()), StandardCharsets.UTF_8);
        String macroPath = stringField(json, "macroPath", "Macro_Builder_Filter.ijm");
        String resultsFile = stringField(json, "resultsFile", DEFAULT_RESULTS_FILE);
        String countingMode = stringField(json, "countingMode", CountingMode.PARTICLES_2D.name());
        String thresholdMode = stringField(json, "thresholdMode", ThresholdMode.AUTO_METHODS.name());
        List<String> autoMethods = stringArrayField(json, "autoMethods", ShootoutSettings.defaultAutoMethods());
        List<Double> fixedThresholds = doubleArrayField(json, "fixedThresholds", Collections.<Double>emptyList());
        double minSize = doubleField(json, "minSize", 0.0, false);
        double maxSize = doubleField(json, "maxSize", Double.POSITIVE_INFINITY, true);
        boolean darkBackground = booleanField(json, "darkBackground", true);

        ShootoutSettings settings = new ShootoutSettings(
                CountingMode.valueOf(countingMode),
                ThresholdMode.valueOf(thresholdMode),
                autoMethods,
                fixedThresholds,
                minSize,
                maxSize,
                darkBackground);
        return new ExportedSettings(settingsFile.getAbsoluteFile(), macroPath, resultsFile, settings);
    }

    public static String toJson(ExportedSettings exportedSettings) {
        if (exportedSettings == null) {
            throw new IllegalArgumentException("exportedSettings must not be null");
        }
        ShootoutSettings settings = exportedSettings.settings;
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": 1,\n");
        json.append("  \"macroPath\": ").append(jsonString(exportedSettings.macroPath)).append(",\n");
        json.append("  \"resultsFile\": ").append(jsonString(exportedSettings.resultsFile)).append(",\n");
        json.append("  \"countingMode\": ").append(jsonString(settings.countingMode.name())).append(",\n");
        json.append("  \"thresholdMode\": ").append(jsonString(settings.thresholdMode.name())).append(",\n");
        json.append("  \"autoMethods\": ").append(jsonStringArray(settings.autoMethods)).append(",\n");
        json.append("  \"fixedThresholds\": ").append(jsonDoubleArray(settings.fixedThresholds)).append(",\n");
        json.append("  \"minSize\": ").append(jsonFiniteDouble(settings.minSize)).append(",\n");
        json.append("  \"maxSize\": ").append(jsonDoubleOrInfinity(settings.maxSize)).append(",\n");
        json.append("  \"darkBackground\": ").append(settings.darkBackground).append('\n');
        json.append("}\n");
        return json.toString();
    }

    public static String buildWrapperMacro(File settingsFile) {
        if (settingsFile == null) {
            throw new IllegalArgumentException("settingsFile must not be null");
        }
        String settingsPath = settingsFile.getAbsolutePath().replace(File.separatorChar, '/');
        StringBuilder macro = new StringBuilder();
        macro.append("// Generated by Macro Builder.\n");
        macro.append("// Choose an input folder and an output folder, then Macro Builder runs the saved filter and count settings.\n");
        macro.append("settings = \"").append(escapeMacroString(settingsPath)).append("\";\n");
        macro.append("input = getDirectory(\"Choose input folder\");\n");
        macro.append("if (input == \"\") exit(\"No input folder selected.\");\n");
        macro.append("output = getDirectory(\"Choose output folder\");\n");
        macro.append("if (output == \"\") exit(\"No output folder selected.\");\n");
        macro.append("run(\"Macro Builder Batch Count\", \"settings=[\" + settings + \"] input=[\" + input + \"] output=[\" + output + \"]\");\n");
        return macro.toString();
    }

    private static File ensureExtension(File file, String extension) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(extension)) {
            return file;
        }
        File parent = file.getParentFile();
        return parent == null ? new File(file.getName() + extension) : new File(parent, file.getName() + extension);
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String stringField(String json, String field, String defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        return unescapeJson(matcher.group(1));
    }

    private static boolean booleanField(String json, String field, boolean defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(true|false)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static double doubleField(String json, String field, double defaultValue, boolean allowInfinity) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        return parseDoubleToken(matcher.group(1), field, allowInfinity);
    }

    private static List<String> stringArrayField(String json, String field, List<String> defaultValue) {
        String body = arrayBody(json, field);
        if (body == null) {
            return defaultValue;
        }
        List<String> values = new ArrayList<String>();
        Pattern pattern = Pattern.compile("\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            values.add(unescapeJson(matcher.group(1)));
        }
        return Collections.unmodifiableList(values);
    }

    private static List<Double> doubleArrayField(String json, String field, List<Double> defaultValue) {
        String body = arrayBody(json, field);
        if (body == null) {
            return defaultValue;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = trimmed.split(",");
        List<Double> values = new ArrayList<Double>();
        for (String part : parts) {
            String token = part.trim();
            if (!token.isEmpty()) {
                values.add(Double.valueOf(parseDoubleToken(token, field, false)));
            }
        }
        return Collections.unmodifiableList(values);
    }

    private static String arrayBody(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private static double parseDoubleToken(String token, String field, boolean allowInfinity) {
        String text = token == null ? "" : token.trim();
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            text = unescapeJson(text.substring(1, text.length() - 1));
        }
        if (allowInfinity && "Infinity".equals(text)) {
            return Double.POSITIVE_INFINITY;
        }
        if (allowInfinity && "-Infinity".equals(text)) {
            return Double.NEGATIVE_INFINITY;
        }
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(field + " is not a number: " + text);
        }
        if ((!allowInfinity && Double.isInfinite(value)) || Double.isNaN(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return value;
    }

    private static String jsonStringArray(List<String> values) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(jsonString(values.get(i)));
        }
        json.append(']');
        return json.toString();
    }

    private static String jsonDoubleArray(List<Double> values) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(jsonFiniteDouble(values.get(i).doubleValue()));
        }
        json.append(']');
        return json.toString();
    }

    private static String jsonDoubleOrInfinity(double value) {
        if (Double.isInfinite(value)) {
            return value > 0.0 ? "\"Infinity\"" : "\"-Infinity\"";
        }
        return jsonFiniteDouble(value);
    }

    private static String jsonFiniteDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("JSON numeric value must be finite");
        }
        if (value == Math.rint(value) && Math.abs(value) < 1000000000000000.0) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static String jsonString(String value) {
        return "\"" + escapeJson(value == null ? "" : value) + "\"";
    }

    private static String escapeJson(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    private static String unescapeJson(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaping) {
                if (c == '\\') {
                    escaping = true;
                } else {
                    out.append(c);
                }
                continue;
            }
            switch (c) {
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                default: out.append(c);
            }
            escaping = false;
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }

    private static String escapeMacroString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class ExportedSettings {
        public final File settingsFile;
        public final String macroPath;
        public final String resultsFile;
        public final ShootoutSettings settings;

        public ExportedSettings(
                File settingsFile,
                String macroPath,
                String resultsFile,
                ShootoutSettings settings) {
            if (settings == null) {
                throw new IllegalArgumentException("settings must not be null");
            }
            this.settingsFile = settingsFile;
            this.macroPath = macroPath == null || macroPath.trim().isEmpty()
                    ? "Macro_Builder_Filter.ijm"
                    : macroPath;
            this.resultsFile = resultsFile == null || resultsFile.trim().isEmpty()
                    ? DEFAULT_RESULTS_FILE
                    : resultsFile;
            this.settings = settings;
        }

        public File macroFile() {
            File macroFile = new File(macroPath);
            if (macroFile.isAbsolute() || settingsFile == null || settingsFile.getParentFile() == null) {
                return macroFile;
            }
            return new File(settingsFile.getParentFile(), macroPath);
        }
    }

    public static final class ExportResult {
        public final File wrapperMacro;
        public final File filterMacro;
        public final File settingsJson;

        private ExportResult(File wrapperMacro, File filterMacro, File settingsJson) {
            this.wrapperMacro = wrapperMacro;
            this.filterMacro = filterMacro;
            this.settingsJson = settingsJson;
        }
    }
}
