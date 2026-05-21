package macro.builder.image.variation;

/**
 * Result of a {@link MemoryEstimator#estimate} call.
 *
 * <p>Immutable value type. Carries the byte projection used to decide whether a
 * variant run should be forced into ROI mode.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code sourceBytes}        — raw byte cost of one full copy of the source image
 *                                    ({@code width * height * stackSize * bytesPerPixel}).
 *   <li>{@code projectedBytes}     — total working-set projection across all variants:
 *                                    {@code sourceBytes * variantCount * 1.3} (the 1.3
 *                                    is overhead for intermediate per-line clones in
 *                                    {@code FilterExecutor.runDagThreadSafe}).
 *   <li>{@code maxHeap}            — the JVM ceiling against which {@code projectedBytes}
 *                                    is judged (typically {@code IJ.maxMemory()}).
 *   <li>{@code headroomFraction}   — {@code projectedBytes / maxHeap}.
 *   <li>{@code exceedsBudget}      — true when {@code headroomFraction > 0.25}, the
 *                                    threshold at which the dialog forces ROI mode.
 *   <li>{@code humanReadable}      — one-line summary suitable for the dialog status
 *                                    line, e.g. {@code "9 variants × 1.2 GiB = 14.0 GiB
 *                                    (43% of 32.0 GiB heap) — ROI mode required"}.
 * </ul>
 */
public final class MemoryEstimate {

    public final long sourceBytes;
    public final long projectedBytes;
    public final long maxHeap;
    public final double headroomFraction;
    public final boolean exceedsBudget;
    public final String humanReadable;

    public MemoryEstimate(long sourceBytes, long projectedBytes, long maxHeap,
                          double headroomFraction, boolean exceedsBudget,
                          String humanReadable) {
        this.sourceBytes = sourceBytes;
        this.projectedBytes = projectedBytes;
        this.maxHeap = maxHeap;
        this.headroomFraction = headroomFraction;
        this.exceedsBudget = exceedsBudget;
        this.humanReadable = humanReadable == null ? "" : humanReadable;
    }

    @Override
    public String toString() {
        return humanReadable;
    }
}
