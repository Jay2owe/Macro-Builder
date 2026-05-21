package macro.builder.image.variation;

import ij.ImagePlus;

/**
 * One variant's outcome: the {@link VariantPlan} that produced it, the
 * {@link ImagePlus} output (or {@code null} on failure), an error
 * (or {@code null} on success), and the wall-clock time it took.
 *
 * <p>{@code output} and {@code error} are mutually exclusive: exactly one is
 * non-null on a normal result. (A successful run with a {@code null} output
 * from {@code runDagThreadSafe} is also recorded as an error so callers never
 * have to test for both at once.)
 */
public final class VariantResult {

    public final VariantPlan plan;
    public final ImagePlus output;
    public final Throwable error;
    public final long elapsedMillis;

    public VariantResult(VariantPlan plan, ImagePlus output, Throwable error, long elapsedMillis) {
        if (plan == null) throw new IllegalArgumentException("plan must not be null");
        this.plan = plan;
        this.output = output;
        this.error = error;
        this.elapsedMillis = elapsedMillis;
    }

    public boolean isSuccess() {
        return error == null && output != null;
    }
}
