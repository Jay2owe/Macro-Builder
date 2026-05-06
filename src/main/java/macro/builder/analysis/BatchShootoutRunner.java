package macro.builder.analysis;

import ij.IJ;
import ij.ImagePlus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BatchShootoutRunner {

    public static final String[] DIRECT_IMAGE_EXTENSIONS = {
            "tif", "tiff", "png", "jpg", "jpeg", "gif", "bmp", "ics", "ids"
    };

    private static final String[] BIO_FORMATS_CONTAINER_EXTENSIONS = {
            "lif", "czi", "nd2", "oib", "oif", "lsm", "zvi", "ome",
            "ims", "vsi", "lei", "mvd2", "mrxs", "svs", "scn"
    };

    public interface Progress {
        void onStarted(int totalFiles);
        void onFileStarted(File file, int index, int totalFiles);
        void onFileFinished(File file, int index, int totalFiles, int rowCount);
        boolean isCancelled();
    }

    public List<BatchShootoutResult> run(
            List<File> files,
            String macro,
            ShootoutSettings settings,
            Progress progress) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        Progress callback = progress == null ? new NoOpProgress() : progress;
        List<File> batchFiles = collectBatchFiles(files);
        List<BatchShootoutResult> rows = new ArrayList<BatchShootoutResult>();
        int total = batchFiles.size();
        callback.onStarted(total);

        for (int i = 0; i < total; i++) {
            if (callback.isCancelled()) {
                break;
            }
            File file = batchFiles.get(i);
            callback.onFileStarted(file, i + 1, total);
            List<BatchShootoutResult> fileRows = runOneFile(file, macro, settings);
            rows.addAll(fileRows);
            callback.onFileFinished(file, i + 1, total, fileRows.size());
        }

        return rows;
    }

    public static List<File> collectBatchFiles(List<File> selections) {
        if (selections == null || selections.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, File> unique = new LinkedHashMap<String, File>();
        for (File selected : selections) {
            if (selected == null) {
                continue;
            }
            if (selected.isDirectory()) {
                addDirectoryFiles(unique, selected);
            } else {
                addUnique(unique, selected);
            }
        }
        return new ArrayList<File>(unique.values());
    }

    public static boolean isDirectImageFile(File file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        if (isBioFormatsContainer(file)) {
            return false;
        }
        String extension = extension(file);
        for (String directExtension : DIRECT_IMAGE_EXTENSIONS) {
            if (directExtension.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBioFormatsContainer(File file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ome.tif") || name.endsWith(".ome.tiff")) {
            return true;
        }
        String extension = extension(file);
        for (String containerExtension : BIO_FORMATS_CONTAINER_EXTENSIONS) {
            if (containerExtension.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    public static String buildCsv(List<BatchShootoutResult> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("file,title,width,height,channels,slices,frames,counting_mode,variant,")
                .append("threshold_value,count,mean_size,coverage,range_min,range_max,status,error\n");
        if (rows == null) {
            return csv.toString();
        }
        for (BatchShootoutResult row : rows) {
            ObjectCounter.CountSummary count = row.countSummary;
            String[] values = new String[]{
                    row.filePath,
                    row.title,
                    positiveInteger(row.width),
                    positiveInteger(row.height),
                    positiveInteger(row.channels),
                    positiveInteger(row.slices),
                    positiveInteger(row.frames),
                    row.countingMode.name(),
                    row.variant,
                    row.thresholdValue == null ? "" : formatNumber(row.thresholdValue.doubleValue()),
                    count == null ? "" : Integer.toString(count.count),
                    count == null ? "" : formatNumber(count.meanSize),
                    count == null ? "" : formatNumber(count.coverage),
                    isFinite(row.imageMinimum) ? formatNumber(row.imageMinimum) : "",
                    isFinite(row.imageMaximum) ? formatNumber(row.imageMaximum) : "",
                    row.status.name(),
                    row.error
            };
            for (int i = 0; i < values.length; i++) {
                if (i > 0) csv.append(',');
                csv.append(csvEscape(values[i]));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private static List<BatchShootoutResult> runOneFile(
            File file,
            String macro,
            ShootoutSettings settings) {
        if (file == null) {
            return singletonFailure(null, settings, "No file was selected.");
        }
        if (!file.isFile()) {
            return singletonFailure(file, settings, "This batch item is not a file.");
        }
        if (isBioFormatsContainer(file)) {
            return singletonFailure(file, settings,
                    "Bio-Formats containers are skipped in batch mode. Open this file individually first.");
        }
        if (!isDirectImageFile(file)) {
            return singletonFailure(file, settings, "Unsupported image format for batch count testing.");
        }

        ImagePlus image = null;
        List<ShootoutResult> shootoutRows = null;
        try {
            image = IJ.openImage(file.getAbsolutePath());
            if (image == null || image.getStack() == null) {
                return singletonFailure(file, settings, "Fiji could not open this image file.");
            }

            shootoutRows = new ThresholdShootoutRunner().run(image, macro == null ? "" : macro, settings);
            if (shootoutRows.isEmpty()) {
                return singletonFailure(file, settings, "No threshold variants were run.");
            }

            List<BatchShootoutResult> rows = new ArrayList<BatchShootoutResult>(shootoutRows.size());
            for (ShootoutResult result : shootoutRows) {
                rows.add(BatchShootoutResult.from(file, image, result));
            }
            return rows;
        } catch (RuntimeException ex) {
            return singletonFailure(file, settings, cleanMessage(ex));
        } finally {
            closeShootoutMasks(shootoutRows);
            closeImageQuietly(image);
        }
    }

    private static List<BatchShootoutResult> singletonFailure(
            File file,
            ShootoutSettings settings,
            String error) {
        return Collections.singletonList(BatchShootoutResult.failure(file, settings.countingMode, error));
    }

    private static void addDirectoryFiles(Map<String, File> unique, File directory) {
        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return;
        }
        Arrays.sort(children, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File child : children) {
            if (child.isFile() && (isDirectImageFile(child) || isBioFormatsContainer(child))) {
                addUnique(unique, child);
            }
        }
    }

    private static void addUnique(Map<String, File> unique, File file) {
        unique.put(canonicalKey(file), file);
    }

    private static String canonicalKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ioe) {
            return file.getAbsolutePath();
        }
    }

    private static String extension(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static void closeShootoutMasks(List<ShootoutResult> rows) {
        if (rows == null) {
            return;
        }
        for (ShootoutResult row : rows) {
            if (row != null) {
                closeImageQuietly(row.maskPreview);
            }
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
        return cleanMessage(message);
    }

    private static String cleanMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Unknown error";
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static String positiveInteger(int value) {
        return value > 0 ? Integer.toString(value) : "";
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        if (Double.isInfinite(value)) {
            return value > 0.0 ? "Infinity" : "-Infinity";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1000000000000000.0) {
            return Long.toString(Math.round(value));
        }
        String formatted = String.format(Locale.US, "%.6f", value);
        while (formatted.indexOf('.') >= 0 && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
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
        @Override public void onStarted(int totalFiles) {
        }

        @Override public void onFileStarted(File file, int index, int totalFiles) {
        }

        @Override public void onFileFinished(File file, int index, int totalFiles, int rowCount) {
        }

        @Override public boolean isCancelled() {
            return false;
        }
    }
}
