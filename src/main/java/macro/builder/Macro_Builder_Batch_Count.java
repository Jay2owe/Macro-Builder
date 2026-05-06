package macro.builder;

import ij.IJ;
import ij.Macro;
import ij.plugin.PlugIn;
import macro.builder.analysis.BatchMacroExporter;
import macro.builder.analysis.BatchShootoutResult;
import macro.builder.analysis.BatchShootoutRunner;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Macro_Builder_Batch_Count implements PlugIn {

    @Override
    public void run(String arg) {
        try {
            Map<String, String> options = parseOptions(optionsText(arg));
            File settingsFile = requiredFile(options, "settings");
            File input = requiredFile(options, "input");
            File output = requiredFile(options, "output");
            if (!output.exists() && !output.mkdirs()) {
                throw new IllegalArgumentException("Could not create output folder: " + output.getAbsolutePath());
            }
            if (!output.isDirectory()) {
                throw new IllegalArgumentException("Output is not a folder: " + output.getAbsolutePath());
            }

            BatchMacroExporter.ExportedSettings exportedSettings = BatchMacroExporter.readSettings(settingsFile);
            File macroFile = exportedSettings.macroFile();
            String macro = new String(Files.readAllBytes(macroFile.toPath()), StandardCharsets.UTF_8);

            List<BatchShootoutResult> rows = new BatchShootoutRunner().run(
                    Collections.singletonList(input),
                    macro,
                    exportedSettings.settings,
                    new StatusProgress());
            File csv = new File(output, exportedSettings.resultsFile);
            Files.write(csv.toPath(), BatchShootoutRunner.buildCsv(rows).getBytes(StandardCharsets.UTF_8));
            IJ.showStatus("Macro Builder batch count complete: " + rows.size() + " row(s).");
            IJ.log("Macro Builder batch count wrote " + csv.getAbsolutePath());
        } catch (Exception ex) {
            reportFailure(ex);
        }
    }

    private static String optionsText(String arg) {
        if (arg != null && !arg.trim().isEmpty()) {
            return arg;
        }
        String options = Macro.getOptions();
        return options == null ? "" : options;
    }

    private static File requiredFile(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required option: " + key);
        }
        return new File(value);
    }

    private static Map<String, String> parseOptions(String options) {
        Map<String, String> parsed = new LinkedHashMap<String, String>();
        if (options == null) {
            return parsed;
        }
        int i = 0;
        while (i < options.length()) {
            while (i < options.length() && Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            int keyStart = i;
            while (i < options.length() && options.charAt(i) != '=' && !Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            if (keyStart == i) {
                i++;
                continue;
            }
            String key = options.substring(keyStart, i);
            while (i < options.length() && Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            if (i >= options.length() || options.charAt(i) != '=') {
                continue;
            }
            i++;
            while (i < options.length() && Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            String value;
            if (i < options.length() && options.charAt(i) == '[') {
                int valueStart = ++i;
                while (i < options.length() && options.charAt(i) != ']') {
                    i++;
                }
                value = options.substring(valueStart, i);
                if (i < options.length() && options.charAt(i) == ']') {
                    i++;
                }
            } else {
                int valueStart = i;
                while (i < options.length() && !Character.isWhitespace(options.charAt(i))) {
                    i++;
                }
                value = options.substring(valueStart, i);
            }
            parsed.put(key, value);
        }
        return parsed;
    }

    private static void reportFailure(Exception ex) {
        String message = cleanMessage(ex);
        IJ.log("Macro Builder batch count failed: " + message);
        if (!GraphicsEnvironment.isHeadless()) {
            IJ.showMessage("Macro Builder Batch Count", "Batch count failed:\n" + message);
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

    private static final class StatusProgress implements BatchShootoutRunner.Progress {
        @Override public void onStarted(int totalFiles) {
            IJ.showStatus("Macro Builder batch count: " + totalFiles + " file(s).");
        }

        @Override public void onFileStarted(File file, int index, int totalFiles) {
            IJ.showStatus("Macro Builder batch count " + index + "/" + totalFiles + ": " + file.getName());
        }

        @Override public void onFileFinished(File file, int index, int totalFiles, int rowCount) {
            IJ.showStatus("Macro Builder batch count " + index + "/" + totalFiles
                    + " complete: " + rowCount + " row(s).");
        }

        @Override public boolean isCancelled() {
            return false;
        }
    }
}
