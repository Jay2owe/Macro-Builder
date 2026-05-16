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
    public final double precision;
    public final double recall;
    public final double f1;
    public final int[] perObjectStatus;
    public final double separationScore;
    public final double distinctnessScore;
    public final double fragilityScore;
    public final int[] fragilityCountRange;
    public final double agreementScore;

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
            String recommendationReason,
            double precision,
            double recall,
            double f1,
            int[] perObjectStatus,
            double separationScore,
            double distinctnessScore,
            double fragilityScore,
            int[] fragilityCountRange,
            double agreementScore) {
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
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.perObjectStatus = perObjectStatus == null ? null : perObjectStatus.clone();
        this.separationScore = separationScore;
        this.distinctnessScore = distinctnessScore;
        this.fragilityScore = fragilityScore;
        this.fragilityCountRange = fragilityCountRange == null ? null : fragilityCountRange.clone();
        this.agreementScore = agreementScore;
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
                "",
                Double.NaN,
                Double.NaN,
                Double.NaN,
                null,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                null,
                Double.NaN);
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
                "",
                Double.NaN,
                Double.NaN,
                Double.NaN,
                null,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                null,
                Double.NaN);
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
                reason,
                precision,
                recall,
                f1,
                perObjectStatus,
                separationScore,
                distinctnessScore,
                fragilityScore,
                fragilityCountRange,
                agreementScore);
    }

    public ShootoutResult withoutRecommendation() {
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
                false,
                "",
                precision,
                recall,
                f1,
                perObjectStatus,
                separationScore,
                distinctnessScore,
                fragilityScore,
                fragilityCountRange,
                agreementScore);
    }

    public ShootoutResult withGroundTruthScore(GroundTruthScorer.ScoreSummary score) {
        if (score == null) {
            return this;
        }
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
                recommended,
                recommendationReason,
                score.precision,
                score.recall,
                score.f1,
                score.perObjectStatus,
                separationScore,
                distinctnessScore,
                fragilityScore,
                fragilityCountRange,
                agreementScore);
    }

    public ShootoutResult withQualityScores(double separationScore, double distinctnessScore) {
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
                recommended,
                recommendationReason,
                precision,
                recall,
                f1,
                perObjectStatus,
                separationScore,
                distinctnessScore,
                fragilityScore,
                fragilityCountRange,
                agreementScore);
    }

    public ShootoutResult withFragility(double fragilityScore, int[] fragilityCountRange) {
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
                recommended,
                recommendationReason,
                precision,
                recall,
                f1,
                perObjectStatus,
                separationScore,
                distinctnessScore,
                fragilityScore,
                fragilityCountRange,
                agreementScore);
    }

    public ShootoutResult withAgreement(double agreementScore) {
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
                recommended,
                recommendationReason,
                precision,
                recall,
                f1,
                perObjectStatus,
                separationScore,
                distinctnessScore,
                fragilityScore,
                fragilityCountRange,
                agreementScore);
    }

    public ShootoutResult withoutMaskPreview() {
        return new ShootoutResult(
                countingMode,
                variant,
                thresholdValue,
                imageMinimum,
                imageMaximum,
                null,
                countSummary,
                status,
                error,
                recommended,
                recommendationReason,
                precision,
                recall,
                f1,
                perObjectStatus,
                separationScore,
                distinctnessScore,
                fragilityScore,
                fragilityCountRange,
                agreementScore);
    }
}
