package macro.builder.analysis;

public final class HistogramQualityScorer {

    private HistogramQualityScorer() {
    }

    public static double separation(int[] histogram, double threshold, ShootoutContext context) {
        if (!isUsable(histogram, threshold, context)) {
            return 0.0;
        }

        int split = binForThreshold(threshold, context, histogram.length);
        if (split <= 0 || split >= histogram.length) {
            return 0.0;
        }

        long lowerCount = sum(histogram, 0, split);
        long upperCount = sum(histogram, split, histogram.length);
        if (lowerCount <= 0L || upperCount <= 0L) {
            return 0.0;
        }

        double overallMean = mean(histogram, 0, histogram.length, lowerCount + upperCount);
        double lowerMean = mean(histogram, 0, split, lowerCount);
        double upperMean = mean(histogram, split, histogram.length, upperCount);
        double between =
                (double) lowerCount * square(lowerMean - overallMean)
                        + (double) upperCount * square(upperMean - overallMean);
        double total = totalVariance(histogram, overallMean);
        if (!(total > 0.0)) {
            return 0.0;
        }

        double varianceScore = clamp01(between / total);
        return clamp01(varianceScore * valleyScore(histogram, split));
    }

    public static double distinctness(int[] histogram, double threshold, ShootoutContext context) {
        if (!isUsable(histogram, threshold, context) || histogram.length <= 1) {
            return 0.0;
        }

        int split = binForThreshold(threshold, context, histogram.length);
        if (split <= 0 || split >= histogram.length) {
            return 0.0;
        }

        long lowerCount = sum(histogram, 0, split);
        long upperCount = sum(histogram, split, histogram.length);
        if (lowerCount <= 0L || upperCount <= 0L) {
            return 0.0;
        }

        double entropySum = entropy(histogram, 0, split, lowerCount)
                + entropy(histogram, split, histogram.length, upperCount);
        double normalisedEntropy = entropySum / (2.0 * Math.log(histogram.length));
        return clamp01(1.0 - normalisedEntropy);
    }

    private static boolean isUsable(int[] histogram, double threshold, ShootoutContext context) {
        return histogram != null
                && histogram.length > 0
                && context != null
                && isFinite(threshold)
                && isFinite(context.rangeMin)
                && isFinite(context.rangeMax)
                && context.rangeMax > context.rangeMin;
    }

    private static int binForThreshold(double threshold, ShootoutContext context, int binCount) {
        if (threshold < context.rangeMin) {
            return 0;
        }
        if (threshold > context.rangeMax) {
            return binCount;
        }
        double position = (threshold - context.rangeMin) * (binCount - 1.0)
                / (context.rangeMax - context.rangeMin);
        int bin = (int) Math.floor(position);
        if (bin < 0) {
            return 0;
        }
        if (bin >= binCount) {
            return binCount - 1;
        }
        return bin;
    }

    private static long sum(int[] histogram, int startInclusive, int endExclusive) {
        long total = 0L;
        for (int i = startInclusive; i < endExclusive; i++) {
            if (histogram[i] > 0) {
                total += histogram[i];
            }
        }
        return total;
    }

    private static double mean(int[] histogram, int startInclusive, int endExclusive, long total) {
        if (total <= 0L) {
            return 0.0;
        }
        double weighted = 0.0;
        for (int i = startInclusive; i < endExclusive; i++) {
            if (histogram[i] > 0) {
                weighted += (double) i * (double) histogram[i];
            }
        }
        return weighted / (double) total;
    }

    private static double totalVariance(int[] histogram, double mean) {
        double total = 0.0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > 0) {
                total += (double) histogram[i] * square(i - mean);
            }
        }
        return total;
    }

    private static double entropy(int[] histogram, int startInclusive, int endExclusive, long total) {
        if (total <= 0L) {
            return 0.0;
        }
        double entropy = 0.0;
        for (int i = startInclusive; i < endExclusive; i++) {
            if (histogram[i] > 0) {
                double p = (double) histogram[i] / (double) total;
                entropy -= p * Math.log(p);
            }
        }
        return entropy;
    }

    private static double valleyScore(int[] histogram, int split) {
        int leftPeak = max(histogram, 0, split);
        int rightPeak = max(histogram, split, histogram.length);
        int peak = Math.min(leftPeak, rightPeak);
        if (peak <= 0) {
            return 0.0;
        }
        int start = Math.max(0, split - 1);
        int end = Math.min(histogram.length, split + 2);
        double local = 0.0;
        for (int i = start; i < end; i++) {
            local += Math.max(0, histogram[i]);
        }
        local /= (double) (end - start);
        return clamp01(1.0 - local / (double) peak);
    }

    private static int max(int[] histogram, int startInclusive, int endExclusive) {
        int max = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            if (histogram[i] > max) {
                max = histogram[i];
            }
        }
        return max;
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp01(double value) {
        if (!isFinite(value) || value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
