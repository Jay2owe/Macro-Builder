package macro.builder.analysis;

import java.io.File;

public final class BatchMacroInput {

    public enum Kind {
        FILE,
        CONTAINER_SERIES
    }

    public final Kind kind;
    public final File file;
    public final String relativePath;
    public final int seriesIndex;
    public final String seriesName;
    public final int width;
    public final int height;
    public final int channels;
    public final int slices;
    public final int frames;

    private BatchMacroInput(
            Kind kind,
            File file,
            String relativePath,
            int seriesIndex,
            String seriesName,
            int width,
            int height,
            int channels,
            int slices,
            int frames) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (seriesIndex < -1) {
            throw new IllegalArgumentException("seriesIndex must be -1 or greater");
        }
        this.kind = kind;
        this.file = file;
        this.relativePath = relativePath == null || relativePath.isEmpty()
                ? file.getName()
                : relativePath;
        this.seriesIndex = seriesIndex;
        this.seriesName = seriesName == null ? "" : seriesName;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.channels = Math.max(0, channels);
        this.slices = Math.max(0, slices);
        this.frames = Math.max(0, frames);
    }

    public static BatchMacroInput file(File file, String relativePath) {
        return new BatchMacroInput(
                Kind.FILE,
                file,
                relativePath,
                -1,
                "",
                0,
                0,
                0,
                0,
                0);
    }

    public static BatchMacroInput containerSeries(
            File container,
            int seriesIndex,
            String seriesName,
            int width,
            int height,
            int channels,
            int slices,
            int frames) {
        if (container == null) {
            throw new IllegalArgumentException("container must not be null");
        }
        if (seriesIndex < 0) {
            throw new IllegalArgumentException("seriesIndex must be zero or greater");
        }
        return new BatchMacroInput(
                Kind.CONTAINER_SERIES,
                container,
                container.getName(),
                seriesIndex,
                seriesName,
                width,
                height,
                channels,
                slices,
                frames);
    }

    public boolean isFile() {
        return kind == Kind.FILE;
    }

    public boolean isContainerSeries() {
        return kind == Kind.CONTAINER_SERIES;
    }
}
