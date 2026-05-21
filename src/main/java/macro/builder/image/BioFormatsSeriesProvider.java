package macro.builder.image;

import ij.ImagePlus;
import macro.builder.analysis.BatchMacroInput;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BioFormatsSeriesProvider {

    private static final String MISSING_RUNTIME_MESSAGE =
            "Bio-Formats is not available. A normal Fiji installation includes Bio-Formats, "
                    + "but this Fiji instance may not.";
    private static final String LIST_FAILURE_MESSAGE =
            "Bio-Formats could not list images in this container. A normal Fiji installation "
                    + "includes Bio-Formats, but this Fiji instance may not.";
    private static final String OPEN_FAILURE_MESSAGE =
            "Bio-Formats could not open the selected image from this container.";

    public boolean isAvailable() {
        return classAvailable("loci.formats.ImageReader")
                && classAvailable("loci.plugins.BF")
                && classAvailable("loci.plugins.in.ImporterOptions");
    }

    public String unavailableMessage() {
        return MISSING_RUNTIME_MESSAGE;
    }

    public List<BatchMacroInput> listSeries(File container) {
        requireContainer(container);
        ensureAvailable();

        Object reader = null;
        try {
            Class<?> readerClass = Class.forName("loci.formats.ImageReader");
            reader = readerClass.getDeclaredConstructor().newInstance();
            invoke(reader, "setId", new Class<?>[] { String.class }, container.getAbsolutePath());
            int count = intValue(invoke(reader, "getSeriesCount", new Class<?>[0]));
            List<BatchMacroInput> rows = new ArrayList<BatchMacroInput>();
            for (int i = 0; i < count; i++) {
                invoke(reader, "setSeries", new Class<?>[] { int.class }, Integer.valueOf(i));
                rows.add(BatchMacroInput.containerSeries(
                        container,
                        i,
                        seriesName(reader),
                        intValue(invoke(reader, "getSizeX", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeY", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeC", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeZ", new Class<?>[0])),
                        intValue(invoke(reader, "getSizeT", new Class<?>[0]))));
            }
            return rows;
        } catch (Throwable t) {
            throw userFacingException(LIST_FAILURE_MESSAGE, t);
        } finally {
            closeReader(reader);
        }
    }

    public ImagePlus openSeries(BatchMacroInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (!input.isContainerSeries()) {
            throw new IllegalArgumentException("input must be a container series");
        }
        requireContainer(input.file);
        ensureAvailable();

        try {
            Class<?> optionsClass = Class.forName("loci.plugins.in.ImporterOptions");
            Object options = optionsClass.getDeclaredConstructor().newInstance();
            invoke(options, "setId", new Class<?>[] { String.class }, input.file.getAbsolutePath());
            invokeIfPresent(options, "setQuiet", new Class<?>[] { boolean.class }, Boolean.TRUE);
            invokeIfPresent(options, "setWindowless", new Class<?>[] { boolean.class }, Boolean.TRUE);
            invokeIfPresent(options, "setOpenAllSeries", new Class<?>[] { boolean.class }, Boolean.FALSE);
            int seriesCount = Math.max(input.seriesIndex + 1, seriesCountOrMinimum(input.file, input.seriesIndex + 1));
            for (int i = 0; i < seriesCount; i++) {
                invokeIfPresent(options, "setSeriesOn",
                        new Class<?>[] { int.class, boolean.class },
                        Integer.valueOf(i),
                        Boolean.valueOf(i == input.seriesIndex));
            }

            Class<?> bfClass = Class.forName("loci.plugins.BF");
            Object opened = bfClass.getMethod("openImagePlus", optionsClass).invoke(null, options);
            ImagePlus[] images = opened instanceof ImagePlus[] ? (ImagePlus[]) opened : new ImagePlus[0];
            ImagePlus selected = firstImage(images);
            closeUnselected(images, selected);
            if (selected == null) {
                throw new IllegalStateException("Bio-Formats did not open the selected series.");
            }
            return selected;
        } catch (Throwable t) {
            throw userFacingException(OPEN_FAILURE_MESSAGE, t);
        }
    }

    private static int seriesCountOrMinimum(File container, int minimum) {
        Object reader = null;
        try {
            Class<?> readerClass = Class.forName("loci.formats.ImageReader");
            reader = readerClass.getDeclaredConstructor().newInstance();
            invoke(reader, "setId", new Class<?>[] { String.class }, container.getAbsolutePath());
            return Math.max(minimum, intValue(invoke(reader, "getSeriesCount", new Class<?>[0])));
        } catch (Throwable ignored) {
            return minimum;
        } finally {
            closeReader(reader);
        }
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

    private static void ensureAvailable() {
        if (!classAvailable("loci.formats.ImageReader")
                || !classAvailable("loci.plugins.BF")
                || !classAvailable("loci.plugins.in.ImporterOptions")) {
            throw new IllegalStateException(MISSING_RUNTIME_MESSAGE);
        }
    }

    private static boolean classAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void requireContainer(File container) {
        if (container == null) {
            throw new IllegalArgumentException("container must not be null");
        }
        if (!container.isFile()) {
            throw new IllegalArgumentException("Container file does not exist: "
                    + container.getAbsolutePath());
        }
    }

    private static ImagePlus firstImage(ImagePlus[] images) {
        if (images == null) return null;
        for (ImagePlus image : images) {
            if (image != null) return image;
        }
        return null;
    }

    private static void closeUnselected(ImagePlus[] images, ImagePlus selected) {
        if (images == null) return;
        for (ImagePlus image : images) {
            if (image != null && image != selected) {
                closeImageQuietly(image);
            }
        }
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

    private static IllegalStateException userFacingException(String prefix, Throwable t) {
        Throwable cause = unwrap(t);
        if (cause instanceof IllegalStateException
                && MISSING_RUNTIME_MESSAGE.equals(cause.getMessage())) {
            return (IllegalStateException) cause;
        }
        return new IllegalStateException(prefix + "\n\n" + cleanMessage(cause), cause);
    }

    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getCause() != null) {
            current = ((InvocationTargetException) current).getCause();
        }
        return current == null ? new IllegalStateException("Unknown error") : current;
    }

    private static String cleanMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static void closeImageQuietly(ImagePlus image) {
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
}
