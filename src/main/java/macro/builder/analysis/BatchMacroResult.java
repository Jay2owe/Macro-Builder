package macro.builder.analysis;

import ij.ImagePlus;

import java.io.File;

public final class BatchMacroResult {

    public enum Status {
        SUCCESS,
        FAILED,
        CANCELLED
    }

    public final BatchMacroInput input;
    public final File outputFile;
    public final Status status;
    public final String error;
    public final int width;
    public final int height;
    public final int channels;
    public final int slices;
    public final int frames;

    private BatchMacroResult(
            BatchMacroInput input,
            File outputFile,
            Status status,
            String error,
            int width,
            int height,
            int channels,
            int slices,
            int frames) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == Status.SUCCESS && outputFile == null) {
            throw new IllegalArgumentException("successful results need an outputFile");
        }
        if (status == Status.FAILED && (error == null || error.trim().isEmpty())) {
            throw new IllegalArgumentException("failed results need an error");
        }
        this.input = input;
        this.outputFile = outputFile;
        this.status = status;
        this.error = status == Status.SUCCESS ? "" : cleanMessage(error);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.channels = Math.max(0, channels);
        this.slices = Math.max(0, slices);
        this.frames = Math.max(0, frames);
    }

    public static BatchMacroResult success(BatchMacroInput input, File outputFile) {
        return new BatchMacroResult(
                input,
                outputFile,
                Status.SUCCESS,
                "",
                inputWidth(input),
                inputHeight(input),
                inputChannels(input),
                inputSlices(input),
                inputFrames(input));
    }

    public static BatchMacroResult success(BatchMacroInput input, File outputFile, ImagePlus image) {
        return new BatchMacroResult(
                input,
                outputFile,
                Status.SUCCESS,
                "",
                imageWidth(input, image),
                imageHeight(input, image),
                imageChannels(input, image),
                imageSlices(input, image),
                imageFrames(input, image));
    }

    public static BatchMacroResult failed(BatchMacroInput input, String error) {
        return new BatchMacroResult(
                input,
                null,
                Status.FAILED,
                error,
                inputWidth(input),
                inputHeight(input),
                inputChannels(input),
                inputSlices(input),
                inputFrames(input));
    }

    public static BatchMacroResult failed(BatchMacroInput input, String error, ImagePlus image) {
        return new BatchMacroResult(
                input,
                null,
                Status.FAILED,
                error,
                imageWidth(input, image),
                imageHeight(input, image),
                imageChannels(input, image),
                imageSlices(input, image),
                imageFrames(input, image));
    }

    public static BatchMacroResult cancelled(BatchMacroInput input, String error) {
        return new BatchMacroResult(
                input,
                null,
                Status.CANCELLED,
                error,
                inputWidth(input),
                inputHeight(input),
                inputChannels(input),
                inputSlices(input),
                inputFrames(input));
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    private static int imageWidth(BatchMacroInput input, ImagePlus image) {
        return image == null ? inputWidth(input) : image.getWidth();
    }

    private static int imageHeight(BatchMacroInput input, ImagePlus image) {
        return image == null ? inputHeight(input) : image.getHeight();
    }

    private static int imageChannels(BatchMacroInput input, ImagePlus image) {
        return image == null ? inputChannels(input) : Math.max(1, image.getNChannels());
    }

    private static int imageSlices(BatchMacroInput input, ImagePlus image) {
        return image == null ? inputSlices(input) : Math.max(1, image.getNSlices());
    }

    private static int imageFrames(BatchMacroInput input, ImagePlus image) {
        return image == null ? inputFrames(input) : Math.max(1, image.getNFrames());
    }

    private static int inputWidth(BatchMacroInput input) {
        return input == null ? 0 : input.width;
    }

    private static int inputHeight(BatchMacroInput input) {
        return input == null ? 0 : input.height;
    }

    private static int inputChannels(BatchMacroInput input) {
        return input == null ? 0 : input.channels;
    }

    private static int inputSlices(BatchMacroInput input) {
        return input == null ? 0 : input.slices;
    }

    private static int inputFrames(BatchMacroInput input) {
        return input == null ? 0 : input.frames;
    }

    private static String cleanMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }
}
