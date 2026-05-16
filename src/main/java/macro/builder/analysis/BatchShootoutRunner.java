package macro.builder.analysis;

import ij.IJ;
import ij.ImagePlus;
import macro.builder.image.BioFormatsSeriesProvider;

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

    private final BioFormatsSeriesProvider seriesProvider;

    public interface Progress {
        void onStarted(int totalFiles);
        void onFileStarted(File file, int index, int totalFiles);
        default void onChannelStarted(
                File file,
                int index,
                int totalFiles,
                int seriesIndex,
                int totalSeries,
                int channel) {
        }
        void onFileFinished(File file, int index, int totalFiles, int rowCount);
        boolean isCancelled();
    }

    public BatchShootoutRunner() {
        this(new BioFormatsSeriesProvider());
    }

    BatchShootoutRunner(BioFormatsSeriesProvider seriesProvider) {
        if (seriesProvider == null) {
            throw new IllegalArgumentException("seriesProvider must not be null");
        }
        this.seriesProvider = seriesProvider;
    }

    public List<BatchShootoutResult> run(
            List<File> files,
            String macro,
            ShootoutSettings settings,
            Progress progress) {
        return run(files, macro, settings, 1, progress);
    }

    public List<BatchShootoutResult> run(
            List<File> files,
            String macro,
            ShootoutSettings settings,
            int primaryChannel,
            Progress progress) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        Progress callback = progress == null ? new NoOpProgress() : progress;
        List<File> batchFiles = collectBatchFiles(files);
        List<BatchEntry> entries = collectBatchEntries(batchFiles, seriesProvider);
        List<Integer> channelsToSweep = effectiveChannels(settings, primaryChannel);
        List<BatchShootoutResult> rows = new ArrayList<BatchShootoutResult>();
        int total = batchFiles.size();
        callback.onStarted(total);

        int entryIndex = 0;
        for (int i = 0; i < total; i++) {
            if (callback.isCancelled()) {
                break;
            }
            File file = batchFiles.get(i);
            callback.onFileStarted(file, i + 1, total);
            List<BatchShootoutResult> fileRows = new ArrayList<BatchShootoutResult>();
            while (entryIndex < entries.size() && entries.get(entryIndex).fileIndex == i + 1) {
                if (callback.isCancelled()) {
                    break;
                }
                fileRows.addAll(runOneEntry(entries.get(entryIndex), macro, settings, channelsToSweep, callback));
                entryIndex++;
            }
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

    static List<BatchEntry> collectBatchEntries(
            List<File> batchFiles,
            BioFormatsSeriesProvider seriesProvider) {
        if (batchFiles == null || batchFiles.isEmpty()) {
            return Collections.emptyList();
        }
        BioFormatsSeriesProvider provider = seriesProvider == null
                ? new BioFormatsSeriesProvider()
                : seriesProvider;
        List<BatchEntry> entries = new ArrayList<BatchEntry>();
        int totalFiles = batchFiles.size();
        for (int fileIndex = 0; fileIndex < batchFiles.size(); fileIndex++) {
            File file = batchFiles.get(fileIndex);
            int oneBasedFileIndex = fileIndex + 1;
            if (isBioFormatsContainer(file)) {
                entries.addAll(containerEntries(file, oneBasedFileIndex, totalFiles, provider));
            } else {
                entries.add(BatchEntry.file(file, oneBasedFileIndex, totalFiles));
            }
        }
        return entries;
    }

    private static List<BatchEntry> containerEntries(
            File file,
            int fileIndex,
            int totalFiles,
            BioFormatsSeriesProvider provider) {
        if (file == null || !file.isFile()) {
            return Collections.singletonList(BatchEntry.failure(
                    file,
                    fileIndex,
                    totalFiles,
                    "Container file does not exist."));
        }
        try {
            List<BatchMacroInput> series = provider.listSeries(file);
            if (series == null || series.isEmpty()) {
                return Collections.singletonList(BatchEntry.failure(
                        file,
                        fileIndex,
                        totalFiles,
                        "Bio-Formats found no series in this container."));
            }
            List<BatchEntry> entries = new ArrayList<BatchEntry>(series.size());
            for (int i = 0; i < series.size(); i++) {
                entries.add(BatchEntry.containerSeries(
                        series.get(i),
                        fileIndex,
                        totalFiles,
                        i + 1,
                        series.size()));
            }
            return entries;
        } catch (RuntimeException ex) {
            return Collections.singletonList(BatchEntry.failure(
                    file,
                    fileIndex,
                    totalFiles,
                    cleanMessage(ex)));
        }
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
                .append("threshold_value,count,mean_size,coverage,range_min,range_max,status,error,")
                .append("series_index,channel_index\n");
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
                    row.error,
                    row.seriesIndex < 0 ? "" : Integer.toString(row.seriesIndex),
                    row.channelIndex > 0 ? Integer.toString(row.channelIndex) : ""
            };
            for (int i = 0; i < values.length; i++) {
                if (i > 0) csv.append(',');
                csv.append(csvEscape(values[i]));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private List<BatchShootoutResult> runOneEntry(
            BatchEntry entry,
            String macro,
            ShootoutSettings settings,
            List<Integer> channelsToSweep,
            Progress progress) {
        File file = entry == null ? null : entry.file;
        if (file == null) {
            return singletonFailure(null, settings, "No file was selected.", -1, firstChannel(channelsToSweep));
        }
        if (entry.listingError != null) {
            int channel = firstChannel(channelsToSweep);
            progress.onChannelStarted(
                    file,
                    entry.fileIndex,
                    entry.totalFiles,
                    entry.seriesPosition,
                    entry.totalSeries,
                    channel);
            return singletonFailure(file, settings, entry.listingError, entry.seriesIndex(), channel);
        }
        if (!file.isFile()) {
            return singletonFailure(file, settings, "This batch item is not a file.",
                    entry.seriesIndex(), firstChannel(channelsToSweep));
        }
        if (!entry.isContainerSeries() && !isDirectImageFile(file)) {
            return singletonFailure(file, settings, "Unsupported image format for batch count testing.",
                    entry.seriesIndex(), firstChannel(channelsToSweep));
        }

        ImagePlus image = null;
        try {
            image = openEntry(entry);
            if (image == null || image.getStack() == null) {
                return singletonFailure(file, settings, openFailureMessage(entry),
                        entry.seriesIndex(), firstChannel(channelsToSweep));
            }

            return runChannels(entry, image, macro, settings, channelsToSweep, progress);
        } catch (RuntimeException ex) {
            return Collections.singletonList(BatchShootoutResult.failure(
                    file,
                    image,
                    settings.countingMode,
                    cleanMessage(ex),
                    entry.seriesIndex(),
                    firstChannel(channelsToSweep)));
        } finally {
            closeImageQuietly(image);
        }
    }

    private List<BatchShootoutResult> runChannels(
            BatchEntry entry,
            ImagePlus image,
            String macro,
            ShootoutSettings settings,
            List<Integer> channelsToSweep,
            Progress progress) {
        List<BatchShootoutResult> rows = new ArrayList<BatchShootoutResult>();
        int availableChannels = Math.max(1, image.getNChannels());
        boolean missingChannelReported = false;
        ThresholdShootoutRunner runner = new ThresholdShootoutRunner();

        for (Integer channelValue : channelsToSweep) {
            if (progress.isCancelled()) {
                break;
            }
            int channel = channelValue == null ? 1 : channelValue.intValue();
            progress.onChannelStarted(
                    entry.file,
                    entry.fileIndex,
                    entry.totalFiles,
                    entry.seriesPosition,
                    entry.totalSeries,
                    channel);

            if (channel > availableChannels) {
                if (!missingChannelReported) {
                    rows.add(BatchShootoutResult.failure(
                            entry.file,
                            image,
                            settings.countingMode,
                            availableChannelsMessage(availableChannels),
                            entry.seriesIndex(),
                            channel));
                    missingChannelReported = true;
                }
                continue;
            }

            List<ShootoutResult> shootoutRows = null;
            try {
                shootoutRows = runner.run(
                        image,
                        macro == null ? "" : macro,
                        settings,
                        channel,
                        null);
                if (shootoutRows.isEmpty()) {
                    rows.add(BatchShootoutResult.failure(
                            entry.file,
                            image,
                            settings.countingMode,
                            "No threshold variants were run.",
                            entry.seriesIndex(),
                            channel));
                } else {
                    for (ShootoutResult result : shootoutRows) {
                        rows.add(BatchShootoutResult.from(
                                entry.file,
                                image,
                                result,
                                entry.seriesIndex(),
                                channel));
                    }
                }
            } catch (RuntimeException ex) {
                rows.add(BatchShootoutResult.failure(
                        entry.file,
                        image,
                        settings.countingMode,
                        cleanMessage(ex),
                        entry.seriesIndex(),
                        channel));
            } finally {
                closeShootoutMasks(shootoutRows);
            }
        }
        if (rows.isEmpty() && !progress.isCancelled()) {
            rows.add(BatchShootoutResult.failure(
                    entry.file,
                    image,
                    settings.countingMode,
                    "No threshold variants were run.",
                    entry.seriesIndex(),
                    firstChannel(channelsToSweep)));
        }
        return rows;
    }

    private ImagePlus openEntry(BatchEntry entry) {
        if (entry.isContainerSeries()) {
            return seriesProvider.openSeries(entry.input);
        }
        return IJ.openImage(entry.file.getAbsolutePath());
    }

    private static List<Integer> effectiveChannels(ShootoutSettings settings, int primaryChannel) {
        List<Integer> configured = settings.channelsToSweep;
        if (configured == null || configured.isEmpty()) {
            return Collections.singletonList(Integer.valueOf(Math.max(1, primaryChannel)));
        }
        if (configured.size() == 1
                && configured.get(0).intValue() == 1
                && primaryChannel > 1) {
            return Collections.singletonList(Integer.valueOf(primaryChannel));
        }
        return configured;
    }

    private static int firstChannel(List<Integer> channels) {
        if (channels == null || channels.isEmpty() || channels.get(0) == null) {
            return 1;
        }
        return Math.max(1, channels.get(0).intValue());
    }

    private static String availableChannelsMessage(int channels) {
        if (channels <= 1) {
            return "only channel 1 exists in this file";
        }
        return "only channels 1-" + channels + " exist in this file";
    }

    private static String openFailureMessage(BatchEntry entry) {
        if (entry != null && entry.isContainerSeries()) {
            return "Bio-Formats did not open the selected container series.";
        }
        return "Fiji could not open this image file.";
    }

    private static List<BatchShootoutResult> singletonFailure(
            File file,
            ShootoutSettings settings,
            String error,
            int seriesIndex,
            int channelIndex) {
        return Collections.singletonList(BatchShootoutResult.failure(
                file,
                settings.countingMode,
                error,
                seriesIndex,
                channelIndex));
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
        String formatted = String.format(Locale.ROOT, "%.6f", value);
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

    static final class BatchEntry {
        final File file;
        final BatchMacroInput input;
        final int fileIndex;
        final int totalFiles;
        final int seriesPosition;
        final int totalSeries;
        final String listingError;

        private BatchEntry(
                File file,
                BatchMacroInput input,
                int fileIndex,
                int totalFiles,
                int seriesPosition,
                int totalSeries,
                String listingError) {
            this.file = file;
            this.input = input;
            this.fileIndex = fileIndex;
            this.totalFiles = totalFiles;
            this.seriesPosition = Math.max(1, seriesPosition);
            this.totalSeries = Math.max(1, totalSeries);
            this.listingError = listingError;
        }

        static BatchEntry file(File file, int fileIndex, int totalFiles) {
            return new BatchEntry(file, null, fileIndex, totalFiles, 1, 1, null);
        }

        static BatchEntry containerSeries(
                BatchMacroInput input,
                int fileIndex,
                int totalFiles,
                int seriesPosition,
                int totalSeries) {
            File file = input == null ? null : input.file;
            return new BatchEntry(file, input, fileIndex, totalFiles, seriesPosition, totalSeries, null);
        }

        static BatchEntry failure(File file, int fileIndex, int totalFiles, String error) {
            return new BatchEntry(file, null, fileIndex, totalFiles, 1, 1, error);
        }

        boolean isContainerSeries() {
            return input != null && input.isContainerSeries();
        }

        int seriesIndex() {
            return isContainerSeries() ? input.seriesIndex : -1;
        }
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
