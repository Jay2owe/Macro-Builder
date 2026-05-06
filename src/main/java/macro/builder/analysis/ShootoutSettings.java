package macro.builder.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShootoutSettings {

    public enum CountingMode {
        PARTICLES_2D,
        OBJECTS_3D
    }

    public enum ThresholdMode {
        AUTO_METHODS,
        FIXED_VALUES,
        AUTO_AND_FIXED
    }

    public final CountingMode countingMode;
    public final ThresholdMode thresholdMode;
    public final List<String> autoMethods;
    public final List<Double> fixedThresholds;
    public final double minSize;
    public final double maxSize;
    public final boolean darkBackground;

    public ShootoutSettings(
            CountingMode countingMode,
            ThresholdMode thresholdMode,
            List<String> autoMethods,
            List<Double> fixedThresholds,
            double minSize,
            double maxSize,
            boolean darkBackground) {
        if (countingMode == null) {
            throw new IllegalArgumentException("countingMode must not be null");
        }
        if (thresholdMode == null) {
            throw new IllegalArgumentException("thresholdMode must not be null");
        }
        if (Double.isNaN(minSize) || minSize < 0.0) {
            throw new IllegalArgumentException("minSize must be zero or greater");
        }
        if (Double.isNaN(maxSize) || maxSize < minSize) {
            throw new IllegalArgumentException("maxSize must be greater than or equal to minSize");
        }

        this.countingMode = countingMode;
        this.thresholdMode = thresholdMode;
        this.autoMethods = immutableCopy(autoMethods);
        this.fixedThresholds = immutableDoubleCopy(fixedThresholds);
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.darkBackground = darkBackground;
    }

    public static ShootoutSettings defaults() {
        List<String> methods = new ArrayList<String>();
        methods.add("Default");
        methods.add("Otsu");
        methods.add("Triangle");
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.AUTO_METHODS,
                methods,
                Collections.<Double>emptyList(),
                0.0,
                Double.POSITIVE_INFINITY,
                true);
    }

    private static List<String> immutableCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<String>(values.size());
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("autoMethods must not contain null");
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<Double> immutableDoubleCopy(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Double> copy = new ArrayList<Double>(values.size());
        for (Double value : values) {
            if (value == null || Double.isNaN(value.doubleValue())) {
                throw new IllegalArgumentException("fixedThresholds must not contain null or NaN");
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }
}
