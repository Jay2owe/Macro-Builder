package macro.builder.image.variation;

import macro.builder.image.FilterMacroParser.OpType;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hardcoded heuristic mapping each {@link OpType} to filters that are reasonable
 * substitutes for it inside a variation run. The dialog uses this to populate
 * the swap-panel's alternatives list.
 *
 * <p>Categories are intentionally narrow: 2D filters do not bleed into the 3D
 * filter list, morphology operations stay within morphology, and unique
 * operations (rolling-ball background subtraction, contrast normalisation,
 * bit-depth conversion) return a single-element list — the heuristic is "no
 * obvious near-equivalent" rather than guessing.
 *
 * <p>The result list always contains the input {@code current} when the type is
 * modelled, so callers can render it as the (un-tickable) baseline alongside
 * its alternatives. Unmodelled types return an empty list.
 */
public final class FilterCompatibility {

    private FilterCompatibility() {}

    /**
     * Returns the broad-category siblings of {@code current}. Includes
     * {@code current} itself when modelled; empty when {@code current} has no
     * sensible substitutes (or is null/unknown).
     */
    public static List<OpType> alternativesFor(OpType current) {
        if (current == null) return Collections.emptyList();
        switch (current) {
            // 2D rank/convolution filters acting on intensity.
            case GAUSSIAN_BLUR:
            case MEDIAN:
            case MEAN:
            case UNSHARP_MASK:
                return Arrays.asList(
                        OpType.GAUSSIAN_BLUR,
                        OpType.MEDIAN,
                        OpType.MEAN,
                        OpType.UNSHARP_MASK);

            // 2D rank min/max/variance — separated from blur set because they
            // are non-linear and tend to be used for different purposes.
            case MINIMUM:
            case MAXIMUM:
            case VARIANCE:
                return Arrays.asList(
                        OpType.MINIMUM,
                        OpType.MAXIMUM,
                        OpType.VARIANCE);

            // 3D filters — never substitute a 2D filter here.
            case GAUSSIAN_BLUR_3D:
            case MEDIAN_3D:
            case MINIMUM_3D:
                return Arrays.asList(
                        OpType.GAUSSIAN_BLUR_3D,
                        OpType.MEDIAN_3D,
                        OpType.MINIMUM_3D);

            // Binary morphology — these only make sense after a threshold step.
            case DILATE:
            case ERODE:
            case OPEN:
            case CLOSE_:
                return Arrays.asList(
                        OpType.DILATE,
                        OpType.ERODE,
                        OpType.OPEN,
                        OpType.CLOSE_);

            // Pixel math — caller can swap among these freely.
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
                return Arrays.asList(
                        OpType.ADD,
                        OpType.SUBTRACT,
                        OpType.MULTIPLY,
                        OpType.DIVIDE);

            // Bit-depth conversion — substitution stays within conversions.
            case CONVERT_8BIT:
            case CONVERT_16BIT:
            case CONVERT_32BIT:
                return Arrays.asList(
                        OpType.CONVERT_8BIT,
                        OpType.CONVERT_16BIT,
                        OpType.CONVERT_32BIT);

            // Singletons — no near-equivalent.
            case SUBTRACT_BACKGROUND:
                return Collections.singletonList(OpType.SUBTRACT_BACKGROUND);
            case AUTO_LOCAL_THRESHOLD:
                return Collections.singletonList(OpType.AUTO_LOCAL_THRESHOLD);
            case ENHANCE_CONTRAST:
                return Collections.singletonList(OpType.ENHANCE_CONTRAST);
            case INVERT:
                return Collections.singletonList(OpType.INVERT);
            case FILL_HOLES:
                return Collections.singletonList(OpType.FILL_HOLES);
            case SKELETONIZE:
                return Collections.singletonList(OpType.SKELETONIZE);

            default:
                return Collections.emptyList();
        }
    }

    /**
     * Convenience: alternatives minus {@code current}, preserving order. Useful
     * when the dialog wants to display only types the user could actually
     * select instead of the baseline.
     */
    public static List<OpType> alternativesExcludingBaseline(OpType current) {
        List<OpType> all = alternativesFor(current);
        if (all.isEmpty()) return all;
        Set<OpType> out = new LinkedHashSet<OpType>(all);
        out.remove(current);
        return Collections.unmodifiableList(new java.util.ArrayList<OpType>(out));
    }
}
