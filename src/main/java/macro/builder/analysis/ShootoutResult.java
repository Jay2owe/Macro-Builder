package macro.builder.analysis;

import ij.ImagePlus;

public final class ShootoutResult {

    public enum Status {
        SUCCESS,
        FAILED
    }

    public final ShootoutSettings.CountingMode countingMode;
    public final String variant;
    public final String thresholdLabel;
    public final Double thresholdValue;
    public final double imageMinimum;
    public final double imageMaximum;
    public final ImagePlus maskPreview;
    public final ObjectCounter.CountSummary countSummary;
    public final Status status;
    public final String error;
    public final boolean recommended;
    public final String recommendationReason;

    private ShootoutResult(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            double imageMinimum,
            double imageMaximum,
            ImagePlus maskPreview,
            ObjectCounter.CountSummary countSummary,
            Status status,
            String error,
            boolean recommended,
            String recommendationReason) {
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
        if (recommended && (recommendationReason == null || recommendationReason.trim().isEmpty())) {
            throw new IllegalArgumentException("recommended results need a reason");
        }

        this.countingMode = countingMode;
        this.variant = variant;
        this.thresholdLabel = variant;
        this.thresholdValue = thresholdValue;
        this.imageMinimum = imageMinimum;
        this.imageMaximum = imageMaximum;
        this.maskPreview = maskPreview;
        this.countSummary = countSummary;
        this.status = status;
        this.error = error;
        this.recommended = recommended;
        this.recommendationReason = recommendationReason == null ? "" : recommendationReason;
    }

    public static ShootoutResult success(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            ObjectCounter.CountSummary countSummary) {
        return success(countingMode, variant, thresholdValue, Double.NaN, Double.NaN, null, countSummary);
    }

    public static ShootoutResult success(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            double imageMinimum,
            double imageMaximum,
            ImagePlus maskPreview,
            ObjectCounter.CountSummary countSummary) {
        return new ShootoutResult(
                countingMode,
                variant,
                thresholdValue,
                imageMinimum,
                imageMaximum,
                maskPreview,
                countSummary,
                Status.SUCCESS,
                null,
                false,
                "");
    }

    public static ShootoutResult failure(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            String error) {
        return failure(countingMode, variant, thresholdValue, Double.NaN, Double.NaN, error);
    }

    public static ShootoutResult failure(
            ShootoutSettings.CountingMode countingMode,
            String variant,
            Double thresholdValue,
            double imageMinimum,
            double imageMaximum,
            String error) {
        return new ShootoutResult(
                countingMode,
                variant,
                thresholdValue,
                imageMinimum,
                imageMaximum,
                null,
                null,
                Status.FAILED,
                error,
                false,
                "");
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public ShootoutResult withRecommendation(String reason) {
        return new ShootoutResult(
                countingMode,
                variant,
                thresholdValue,
                imageMinimum,
                imageMaximum,
                maskPreview,
                countSummary,
                status,
                error,
                true,
                reason);
    }
}
