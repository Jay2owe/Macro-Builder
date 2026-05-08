package macro.builder.ui;

import ij.ImagePlus;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BioFormatsSeriesSelector {

    private BioFormatsSeriesSelector() {}

    public static OpenResult chooseAndOpen(Component parent, File file) {
        if (file == null) return OpenResult.cancelled();
        List<SeriesInfo> series;
        try {
            series = readSeries(file);
        } catch (Throwable t) {
            return OpenResult.failed("Fiji could not read the series list with Bio-Formats.\n\n"
                    + "A normal Fiji installation includes Bio-Formats, but this Fiji instance may not.\n\n"
                    + cleanMessage(t));
        }
        if (series.isEmpty()) {
            return OpenResult.failed("Bio-Formats did not find any image series in:\n"
                    + file.getAbsolutePath());
        }

        SeriesInfo[] choices = series.toArray(new SeriesInfo[series.size()]);
        Object selected = JOptionPane.showInputDialog(parent,
                "Choose the series to open:",
                "Open Bio-Formats series",
                JOptionPane.PLAIN_MESSAGE,
                null,
                choices,
                choices[0]);
        if (!(selected instanceof SeriesInfo)) return OpenResult.cancelled();

        SeriesInfo chosen = (SeriesInfo) selected;
        try {
            ImagePlus[] opened = openSeries(file, chosen.index, series.size());
            if (opened == null || opened.length == 0 || opened[0] == null) {
                return OpenResult.failed("Bio-Formats did not open the selected series.");
            }
            for (int i = 1; i < opened.length; i++) {
                closeQuietly(opened[i]);
            }
            return OpenResult.opened(opened[0], chosen.toString());
        } catch (Throwable t) {
            return OpenResult.failed("Fiji could not open the selected Bio-Formats series.\n\n"
                    + cleanMessage(t));
        }
    }

    static List<SeriesInfo> readSeries(File file) throws Exception {
        Class<?> readerClass = Class.forName("loci.formats.ImageReader");
        Object reader = readerClass.getDeclaredConstructor().newInstance();
        try {
            invoke(reader, "setId", new Class<?>[] { String.class }, file.getAbsolutePath());
            int count = intValue(invoke(reader, "getSeriesCount", new Class<?>[0]));
            List<SeriesInfo> out = new ArrayList<SeriesInfo>();
            for (int i = 0; i < count; i++) {
                invoke(reader, "setSeries", new Class<?>[] { int.class }, Integer.valueOf(i));
                out.add(new SeriesInfo(i,
                        seriesName(reader),
                        intValue(invoke(reader, "getSizeX", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeY", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeC", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeZ", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeT", new Class<?>[0]))));
            }
            return out;
        } finally {
            closeReader(reader);
        }
    }

    private static ImagePlus[] openSeries(File file, int seriesIndex, int seriesCount) throws Exception {
        Class<?> optionsClass = Class.forName("loci.plugins.in.ImporterOptions");
        Object options = optionsClass.getDeclaredConstructor().newInstance();
        invoke(options, "setId", new Class<?>[] { String.class }, file.getAbsolutePath());
        invokeIfPresent(options, "setQuiet", new Class<?>[] { boolean.class }, Boolean.TRUE);
        invokeIfPresent(options, "setWindowless", new Class<?>[] { boolean.class }, Boolean.TRUE);
        invokeIfPresent(options, "setOpenAllSeries", new Class<?>[] { boolean.class }, Boolean.FALSE);
        for (int i = 0; i < Math.max(seriesCount, seriesIndex + 1); i++) {
            invokeIfPresent(options, "setSeriesOn",
                    new Class<?>[] { int.class, boolean.class },
                    Integer.valueOf(i), Boolean.valueOf(i == seriesIndex));
        }

        Class<?> bfClass = Class.forName("loci.plugins.BF");
        Object opened = bfClass.getMethod("openImagePlus", optionsClass).invoke(null, options);
        return opened instanceof ImagePlus[] ? (ImagePlus[]) opened : new ImagePlus[0];
    }

    private static String seriesName(Object reader) {
        try {
            Object value = invoke(reader, "getSeriesMetadata", new Class<?>[0]);
            if (value instanceof Map<?, ?>) {
                return seriesNameFromMetadata((Map<?, ?>) value);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    static String seriesNameFromMetadata(Map<?, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) return "";
        String fallback = "";
        for (Map.Entry<?, ?> entry : metadata.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            if (value.length() == 0) continue;
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.equals("name") || lower.equals("image name")
                    || lower.equals("series name") || lower.endsWith(" name")) {
                return value;
            }
            if (fallback.length() == 0
                    && (lower.indexOf("name") >= 0 || lower.indexOf("series") >= 0)) {
                fallback = value;
            }
        }
        return fallback;
    }

    private static void closeReader(Object reader) {
        if (reader == null) return;
        try {
            invoke(reader, "close", new Class<?>[] { boolean.class }, Boolean.FALSE);
            return;
        } catch (Throwable ignored) {
        }
        try {
            invoke(reader, "close", new Class<?>[0]);
        } catch (Throwable ignored) {
        }
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getMethod(method, types);
        return m.invoke(target, args);
    }

    private static void invokeIfPresent(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        try {
            invoke(target, method, types, args);
        } catch (NoSuchMethodException ignored) {
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static void closeQuietly(ImagePlus image) {
        if (image == null) return;
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

    private static String cleanMessage(Throwable t) {
        if (t == null) return "";
        if (t instanceof InvocationTargetException
                && ((InvocationTargetException) t).getCause() != null) {
            return cleanMessage(((InvocationTargetException) t).getCause());
        }
        String message = t.getMessage();
        return message == null || message.trim().isEmpty()
                ? t.getClass().getSimpleName()
                : message;
    }

    public static final class OpenResult {
        public final ImagePlus image;
        public final boolean cancelled;
        public final String message;
        public final String seriesLabel;

        private OpenResult(ImagePlus image, boolean cancelled, String message, String seriesLabel) {
            this.image = image;
            this.cancelled = cancelled;
            this.message = message;
            this.seriesLabel = seriesLabel == null ? "" : seriesLabel;
        }

        static OpenResult opened(ImagePlus image, String seriesLabel) {
            return new OpenResult(image, false, null, seriesLabel);
        }

        static OpenResult cancelled() {
            return new OpenResult(null, true, null, "");
        }

        static OpenResult failed(String message) {
            return new OpenResult(null, false, message, "");
        }
    }

    static final class SeriesInfo {
        final int index;
        final String name;
        final int width;
        final int height;
        final int channels;
        final int slices;
        final int frames;

        SeriesInfo(int index, String name, int width, int height, int channels, int slices, int frames) {
            this.index = Math.max(0, index);
            this.name = name == null ? "" : name.trim();
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.channels = Math.max(1, channels);
            this.slices = Math.max(1, slices);
            this.frames = Math.max(1, frames);
        }

        @Override public String toString() {
            String label = "Series " + (index + 1);
            if (name.length() > 0) label += ": " + name;
            return label + " (" + width + " x " + height
                    + ", C=" + channels
                    + ", Z=" + slices
                    + ", T=" + frames + ")";
        }
    }
}
