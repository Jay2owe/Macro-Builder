package macro.builder.analysis;

public final class ShootoutResult {

    public enum Status {
        SUCCESS,
        FAILED
    }

    public final ShootoutSettings.CountingMode countingMode;
    public final String variant;
    public final Double thresholdValue;
    public final ObjectCounter.CountSummary countSummary;
    public final Status status;
    public final String error;

    private ShootoutResult(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            ObjectCounter.CountSummary countSummary,
            Status status,
            String error) {
        if (countingMode == null) {
            throw new IllegalArgumentException("countingMode must not be null");
        }
        if (variant == null || variant.trim().isEmpty()) {
            throw new IllegalArgumentException("variant must not be blank");
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

        this.countingMode = countingMode;
        this.variant = variant;
        this.thresholdValue = thresholdValue;
        this.countSummary = countSummary;
        this.status = status;
        this.error = error;
    }

    public static ShootoutResult success(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            ObjectCounter.CountSummary countSummary) {
        return new ShootoutResult(countingMode, variant, thresholdValue, countSummary, Status.SUCCESS, null);
    }

    public static ShootoutResult failure(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            String error) {
        return new ShootoutResult(countingMode, variant, thresholdValue, null, Status.FAILED, error);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
