package macro.builder.analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import macro.builder.image.BioFormatsSeriesProvider;
import macro.builder.image.FilterExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BatchMacroRunner {

    private static final String OUTPUT_SUFFIX = "_MacroBuilder";
    private static final String TIFF_EXTENSION = ".tif";

    private final BioFormatsSeriesProvider seriesProvider;

    public BatchMacroRunner() {
        this(new BioFormatsSeriesProvider());
    }

    BatchMacroRunner(BioFormatsSeriesProvider seriesProvider) {
        if (seriesProvider == null) {
            throw new IllegalArgumentException("seriesProvider must not be null");
        }
        this.seriesProvider = seriesProvider;
    }

    public interface Progress {
        void onStarted(int totalItems);
        void onItemStarted(BatchMacroInput input, int index, int totalItems);
        void onItemProgress(BatchMacroInput input, int index, int totalItems, String message);
        void onItemFinished(BatchMacroInput input, int index, int totalItems, BatchMacroResult result);
        boolean isCancelled();
    }

    public List<BatchMacroResult> run(
            List<BatchMacroInput> inputs,
            String macro,
            File outputDirectory,
            Progress progress) {
        String macroContent = requireMacro(macro);
        File outDir = requireOutputDirectory(outputDirectory);
        List<BatchMacroInput> batchInputs = nonNullInputs(inputs);
        Progress callback = progress == null ? new NoOpProgress() : progress;
        List<BatchMacroResult> results = new ArrayList<BatchMacroResult>();
        Set<String> usedOutputPaths = new HashSet<String>();

        int total = batchInputs.size();
        callback.onStarted(total);
        for (int i = 0; i < total; i++) {
            if (callback.isCancelled()) {
                break;
            }
            BatchMacroInput input = batchInputs.get(i);
            int index = i + 1;
            callback.onItemStarted(input, index, total);
            BatchMacroResult result = runOneInput(
                    input,
                    macroContent,
                    outDir,
                    usedOutputPaths,
                    callback,
                    index,
                    total);
            results.add(result);
            callback.onItemFinished(input, index, total, result);
        }
        return results;
    }

    public static String buildCsv(List<BatchMacroResult> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("source,kind,series_index,series_name,width,height,channels,slices,frames,")
                .append("output,status,error\n");
        if (results == null) {
            return csv.toString();
        }
        for (BatchMacroResult result : results) {
            if (result == null) {
                continue;
            }
            BatchMacroInput input = result.input;
            String[] values = new String[]{
                    input.file.getAbsolutePath(),
                    input.kind.name(),
                    input.seriesIndex < 0 ? "" : Integer.toString(input.seriesIndex),
                    input.seriesName,
                    positiveInteger(result.width),
                    positiveInteger(result.height),
                    positiveInteger(result.channels),
                    positiveInteger(result.slices),
                    positiveInteger(result.frames),
                    result.outputFile == null ? "" : result.outputFile.getAbsolutePath(),
                    result.status.name(),
                    result.error
            };
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    csv.append(',');
                }
                csv.append(csvEscape(values[i]));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    public static void writeCsv(File csvFile, List<BatchMacroResult> results) throws IOException {
        if (csvFile == null) {
            throw new IllegalArgumentException("csvFile must not be null");
        }
        File parent = csvFile.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create CSV folder: " + parent.getAbsolutePath());
        }
        Files.write(csvFile.toPath(), buildCsv(results).getBytes(StandardCharsets.UTF_8));
    }

    private BatchMacroResult runOneInput(
            final BatchMacroInput input,
            final String macro,
            File outputDirectory,
            Set<String> usedOutputPaths,
            final Progress progress,
            final int index,
            final int totalItems) {
        if (input.kind == BatchMacroInput.Kind.FILE) {
            if (!input.file.isFile()) {
                return BatchMacroResult.failed(input, "This batch item is not a file.");
            }
            if (!BatchMacroScanner.isDirectImageFile(input.file)) {
                return BatchMacroResult.failed(input, "Unsupported image format for macro batch runs.");
            }
        } else if (input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
            if (!input.file.isFile()) {
                return BatchMacroResult.failed(input, "Container file does not exist.");
            }
        } else {
            return BatchMacroResult.failed(input, "Unsupported batch input type.");
        }

        ImagePlus image = null;
        try {
            image = openInput(input);
            if (image == null || image.getStack() == null || image.getStackSize() == 0) {
                return BatchMacroResult.failed(input, openFailureMessage(input));
            }

            progress.onItemProgress(input, index, totalItems, "Running macro...");
            FilterExecutor.runThreadSafe(image, macro, new FilterExecutor.Progress() {
                @Override public void setIndeterminate(String message) {
                    progress.onItemProgress(input, index, totalItems, message);
                }

                @Override public void setProgress(int completedSteps, int totalSteps, String message) {
                    progress.onItemProgress(input, index, totalItems, message);
                }
            });

            File outputFile = uniqueOutputFile(outputFileFor(input, outputDirectory), usedOutputPaths);
            progress.onItemProgress(input, index, totalItems, "Saving TIFF...");
            saveTiff(image, outputFile);
            return BatchMacroResult.success(input, outputFile, image);
        } catch (RuntimeException ex) {
            return BatchMacroResult.failed(input, cleanMessage(ex), image);
        } finally {
            closeImageQuietly(image);
        }
    }

    private ImagePlus openInput(BatchMacroInput input) {
        if (input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
            return seriesProvider.openSeries(input);
        }
        return IJ.openImage(input.file.getAbsolutePath());
    }

    private static List<BatchMacroInput> nonNullInputs(List<BatchMacroInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Collections.emptyList();
        }
        List<BatchMacroInput> rows = new ArrayList<BatchMacroInput>();
        for (BatchMacroInput input : inputs) {
            if (input != null) {
                rows.add(input);
            }
        }
        return rows;
    }

    private static String requireMacro(String macro) {
        if (macro == null || macro.trim().isEmpty()) {
            throw new IllegalArgumentException("macro must not be blank");
        }
        return macro;
    }

    private static File requireOutputDirectory(File outputDirectory) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory must not be null");
        }
        File directory = outputDirectory.getAbsoluteFile();
        if (directory.exists() && !directory.isDirectory()) {
            throw new IllegalArgumentException("outputDirectory must be a folder");
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create output folder: "
                    + directory.getAbsolutePath());
        }
        return directory;
    }

    static File outputFileFor(BatchMacroInput input, File outputDirectory) {
        if (input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
            return containerOutputFileFor(input, outputDirectory);
        }
        String relativePath = input.relativePath == null || input.relativePath.trim().isEmpty()
                ? input.file.getName()
                : input.relativePath;
        String normalized = relativePath.replace('\\', '/');
        String[] parts = normalized.split("/");
        File folder = outputDirectory;
        for (int i = 0; i < parts.length - 1; i++) {
            folder = new File(folder, safePathSegment(parts[i]));
        }
        String sourceName = parts.length == 0 || parts[parts.length - 1].trim().isEmpty()
                ? input.file.getName()
                : parts[parts.length - 1];
        return new File(folder, safeBaseName(sourceName) + OUTPUT_SUFFIX + TIFF_EXTENSION);
    }

    private static File containerOutputFileFor(BatchMacroInput input, File outputDirectory) {
        String base = safeBaseName(input.file.getName());
        String seriesPart = String.format(Locale.US, "_s%03d", input.seriesIndex + 1);
        String namePart = input.seriesName == null || input.seriesName.trim().isEmpty()
                ? ""
                : "_" + safePathSegment(input.seriesName);
        return new File(outputDirectory, base + seriesPart + namePart + OUTPUT_SUFFIX + TIFF_EXTENSION);
    }

    private static File uniqueOutputFile(File preferred, Set<String> usedOutputPaths) {
        String key = canonicalKey(preferred);
        if (usedOutputPaths.add(key)) {
            return preferred;
        }

        File parent = preferred.getParentFile();
        String name = preferred.getName();
        String base = stripExtension(name);
        for (int copy = 2; ; copy++) {
            File candidate = new File(parent, base + "_" + copy + TIFF_EXTENSION);
            if (usedOutputPaths.add(canonicalKey(candidate))) {
                return candidate;
            }
        }
    }

    private static void saveTiff(ImagePlus image, File outputFile) {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create output folder: "
                    + parent.getAbsolutePath());
        }
        FileSaver saver = new FileSaver(image);
        boolean saved = image.getStackSize() > 1
                ? saver.saveAsTiffStack(outputFile.getAbsolutePath())
                : saver.saveAsTiff(outputFile.getAbsolutePath());
        if (!saved) {
            throw new IllegalStateException("Could not save TIFF: "
                    + outputFile.getAbsolutePath());
        }
    }

    private static String safeBaseName(String name) {
        return safePathSegment(stripExtension(name));
    }

    private static String stripExtension(String name) {
        String text = name == null ? "" : name;
        int dot = text.lastIndexOf('.');
        return dot > 0 ? text.substring(0, dot) : text;
    }

    private static String safePathSegment(String segment) {
        String text = segment == null ? "" : segment.trim();
        if (text.isEmpty() || ".".equals(text) || "..".equals(text)) {
            return "_";
        }
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.'
                    || ch == '_'
                    || ch == '-') {
                safe.append(ch);
            } else {
                safe.append('_');
            }
        }
        return safe.length() == 0 ? "_" : safe.toString();
    }

    private static String canonicalKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ioe) {
            return file.getAbsolutePath();
        }
    }

    private static void closeImageQuietly(ImagePlus image) {
        if (image == null) {
            return;
        }
        try {
            image.changes = false;
            if (image.getWindow() != null) {
                image.close();
            } else {
                image.flush();
            }
        } catch (Throwable ignored) {
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

    private static String openFailureMessage(BatchMacroInput input) {
        if (input != null && input.kind == BatchMacroInput.Kind.CONTAINER_SERIES) {
            return "Bio-Formats did not open the selected container series.";
        }
        return "Fiji could not open this image file.";
    }

    private static String positiveInteger(int value) {
        return value > 0 ? Integer.toString(value) : "";
    }

    private static String csvEscape(String value) {
        String text = value == null ? "" : value;
        boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        if (!quote) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static final class NoOpProgress implements Progress {
        @Override public void onStarted(int totalItems) {
        }

        @Override public void onItemStarted(BatchMacroInput input, int index, int totalItems) {
        }

        @Override public void onItemProgress(BatchMacroInput input, int index, int totalItems, String message) {
        }

        @Override public void onItemFinished(
                BatchMacroInput input,
                int index,
                int totalItems,
                BatchMacroResult result) {
        }

        @Override public boolean isCancelled() {
            return false;
        }
    }
}
