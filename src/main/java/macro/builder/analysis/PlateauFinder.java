package macro.builder.analysis;

/*
 * Kneedle-style plateau detector for Test Counts.
 *
 * Algorithm reference: https://github.com/etam4260/kneedle/blob/main/R/kneedle.R
 * License reference: https://github.com/etam4260/kneedle/blob/main/DESCRIPTION
 * The etam4260/kneedle package is MIT-licensed ("MIT + file LICENSE").
 * Copyright holder: 2022 kneedle authors.
 *
 * This Java implementation is a small clean-room adaptation for the plugin's
 * count-vs-threshold grid. It returns the midpoint of the longest region where
 * neighbouring counts barely change, rather than exposing a general knee API.
 */
public final class PlateauFinder {
    public static final String DEFAULT_REASON = "count barely changed across this region";

    private static final double EPSILON_FRACTION_OF_MAX_COUNT = 0.03;
    private static final double MIN_LOW_COUNT_TOLERANCE = 0.5;
    private static final int MIN_PLATEAU_POINTS = 3;

    private PlateauFinder() {
    }

    public static int findPlateauIndex(double[] thresholds, int[] counts) {
        if (thresholds == null || counts == null) {
            throw new IllegalArgumentException("thresholds and counts must not be null");
        }
        if (thresholds.length != counts.length) {
            throw new IllegalArgumentException("thresholds and counts must have the same length");
        }
        if (counts.length < MIN_PLATEAU_POINTS) {
            return -1;
        }

        int maxCount = maxCount(counts);
        if (maxCount <= 0) {
            return -1;
        }

        double tolerance = Math.max(
                MIN_LOW_COUNT_TOLERANCE,
                maxCount * EPSILON_FRACTION_OF_MAX_COUNT);
        Plateau best = null;
        Plateau current = null;

        for (int i = 0; i < counts.length - 1; i++) {
            if (isStablePair(thresholds, counts, i, tolerance)) {
                if (current == null) {
                    current = new Plateau(i, i + 1);
                } else {
                    current = new Plateau(current.start, i + 1);
                }
            } else {
                best = better(best, current, counts);
                current = null;
            }
        }
        best = better(best, current, counts);
        if (best == null || best.pointCount() < MIN_PLATEAU_POINTS) {
            return -1;
        }
        return (best.start + best.end) / 2;
    }

    private static boolean isStablePair(double[] thresholds, int[] counts, int index, double tolerance) {
        double left = thresholds[index];
        double right = thresholds[index + 1];
        if (Double.isNaN(left) || Double.isNaN(right)
                || Double.isInfinite(left) || Double.isInfinite(right)) {
            return false;
        }
        return Math.abs(counts[index + 1] - counts[index]) <= tolerance;
    }

    private static Plateau better(Plateau best, Plateau candidate, int[] counts) {
        if (candidate == null) {
            return best;
        }
        if (best == null) {
            return candidate;
        }
        int candidatePoints = candidate.pointCount();
        int bestPoints = best.pointCount();
        if (candidatePoints > bestPoints) {
            return candidate;
        }
        if (candidatePoints < bestPoints) {
            return best;
        }
        if (spread(counts, candidate) < spread(counts, best)) {
            return candidate;
        }
        return best;
    }

    private static int maxCount(int[] counts) {
        int max = 0;
        for (int i = 0; i < counts.length; i++) {
            int value = Math.abs(counts[i]);
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private static int spread(int[] counts, Plateau plateau) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = plateau.start; i <= plateau.end; i++) {
            if (counts[i] < min) {
                min = counts[i];
            }
            if (counts[i] > max) {
                max = counts[i];
            }
        }
        return max - min;
    }

    private static final class Plateau {
        final int start;
        final int end;

        Plateau(int start, int end) {
            this.start = start;
            this.end = end;
        }

        int pointCount() {
            return end - start + 1;
        }
    }
}
