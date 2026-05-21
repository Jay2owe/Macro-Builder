package macro.builder.analysis;

import ij.ImagePlus;

import java.io.File;

public final class BatchShootoutResult {

    public enum Status {
        SUCCESS,
        FAILED
    }

    public final File file;
    public final String filePath;
    public final String title;
    public final int width;
    public final int height;
    public final int channels;
    public final int slices;
    public final int frames;
    public final int seriesIndex;
    public final int channelIndex;
    public final ShootoutSettings.CountingMode countingMode;
    public final String variant;
    public final Double thresholdValue;
    public final double imageMinimum;
    public final double imageMaximum;
    public final ObjectCounter.CountSummary countSummary;
    public final Status status;
    public final String error;

    private BatchShootoutResult(
            File file,
            String title,
            int width,
            int height,
            int channels,
            int slices,
            int frames,
            int seriesIndex,
            int channelIndex,
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            double imageMinimum,
            double imageMaximum,
            ObjectCounter.CountSummary countSummary,
            Status status,
            String error) {
        if (countingMode == null) {
            throw new IllegalArgumentException("countingMode must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == Status.SUCCESS && countSummary == null) {
            throw new IllegalArgumentException("successful results need a countSummary");
        }
        if (status == Status.FAILED && (error == null || error.trim().isEmpty())) {
            throw new IllegalArgumentException("failed results need an error");
        }
        if (seriesIndex < -1) {
            throw new IllegalArgumentException("seriesIndex must be -1 or greater");
        }
        if (channelIndex < 0) {
            throw new IllegalArgumentException("channelIndex must be zero or greater");
        }

        this.file = file;
        this.filePath = file == null ? "" : file.getAbsolutePath();
        this.title = title == null ? "" : title;
        this.width = width;
        this.height = height;
        this.channels = channels;
        this.slices = slices;
        this.frames = frames;
        this.seriesIndex = seriesIndex;
        this.channelIndex = channelIndex;
        this.countingMode = countingMode;
        this.variant = variant == null ? "" : variant;
        this.thresholdValue = thresholdValue;
        this.imageMinimum = imageMinimum;
        this.imageMaximum = imageMaximum;
        this.countSummary = countSummary;
        this.status = status;
        this.error = status == Status.FAILED ? error.trim() : "";
    }

    public static BatchShootoutResult from(File file, ImagePlus image, ShootoutResult result) {
        return from(file, image, result, -1, 1);
    }

    public static BatchShootoutResult from(
            File file,
            ImagePlus image,
            ShootoutResult result,
            int seriesIndex,
            int channelIndex) {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        return new BatchShootoutResult(
                file,
                imageTitle(image),
                image.getWidth(),
                image.getHeight(),
                Math.max(1, image.getNChannels()),
                Math.max(1, image.getNSlices()),
                Math.max(1, image.getNFrames()),
                seriesIndex,
                channelIndex,
                result.countingMode,
                result.variant,
                result.thresholdValue,
                result.imageMinimum,
                result.imageMaximum,
                result.countSummary,
                result.isSuccess() ? Status.SUCCESS : Status.FAILED,
                result.error);
    }

    public static BatchShootoutResult failure(
            File file,
            ShootoutSettings.CountingMode countingMode,
            String error) {
        return failure(file, countingMode, error, -1, 1);
    }

    public static BatchShootoutResult failure(
            File file,
            ShootoutSettings.CountingMode countingMode,
            String error,
            int seriesIndex,
            int channelIndex) {
        return new BatchShootoutResult(
                file,
                "",
                0,
                0,
                0,
                0,
                0,
                seriesIndex,
                channelIndex,
                countingMode,
                "",
                null,
                Double.NaN,
                Double.NaN,
                null,
                Status.FAILED,
                cleanMessage(error));
    }

    public static BatchShootoutResult failure(
            File file,
            ImagePlus image,
            ShootoutSettings.CountingMode countingMode,
            String error,
            int seriesIndex,
            int channelIndex) {
        if (image == null) {
            return failure(file, countingMode, error, seriesIndex, channelIndex);
        }
        return new BatchShootoutResult(
                file,
                imageTitle(image),
                image.getWidth(),
                image.getHeight(),
                Math.max(1, image.getNChannels()),
                Math.max(1, image.getNSlices()),
                Math.max(1, image.getNFrames()),
                seriesIndex,
                channelIndex,
                countingMode,
                "",
                null,
                Double.NaN,
                Double.NaN,
                null,
                Status.FAILED,
                cleanMessage(error));
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    private static String imageTitle(ImagePlus image) {
        String title = image.getTitle();
        return title == null ? "" : title;
    }

    private static String cleanMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Unknown error";
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }
}
