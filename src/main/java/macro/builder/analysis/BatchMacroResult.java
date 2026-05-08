package macro.builder.analysis;

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

    private BatchMacroResult(
            BatchMacroInput input,
            File outputFile,
            Status status,
            String error) {
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
    }

    public static BatchMacroResult success(BatchMacroInput input, File outputFile) {
        return new BatchMacroResult(input, outputFile, Status.SUCCESS, "");
    }

    public static BatchMacroResult failed(BatchMacroInput input, String error) {
        return new BatchMacroResult(input, null, Status.FAILED, error);
    }

    public static BatchMacroResult cancelled(BatchMacroInput input, String error) {
        return new BatchMacroResult(input, null, Status.CANCELLED, error);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    private static String cleanMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        return message.trim().replace('\n', ' ').replace('\r', ' ');
    }
}
