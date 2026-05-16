package macro.builder.analysis;

import ij.ImagePlus;

public final class ShootoutContext {
    public final ImagePlus processed;
    public final int[] histogram;
    public final double rangeMin;
    public final double rangeMax;
    public final boolean isFloat;

    public ShootoutContext(
            ImagePlus processed,
            int[] histogram,
            double rangeMin,
            double rangeMax,
            boolean isFloat) {
        if (processed == null) {
            throw new IllegalArgumentException("processed must not be null");
        }
        if (histogram == null) {
            throw new IllegalArgumentException("histogram must not be null");
        }
        this.processed = processed;
        this.histogram = histogram.clone();
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.isFloat = isFloat;
    }
}
