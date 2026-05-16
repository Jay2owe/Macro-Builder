package macro.builder.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShootoutSettings {

    public static final String FIXED_THRESHOLD_HELP =
            "Fixed thresholds use the macro output's native intensity values. "
                    + "A value of 2000 on a 16-bit image means intensity 2000, not a 0-255 value.";

    public enum CountingMode {
        PARTICLES_2D,
        OBJECTS_3D
    }

    public enum ThresholdMode {
        AUTO_METHODS,
        FIXED_VALUES,
        AUTO_AND_FIXED,
        AUTO_GRID
    }

    public static final int DEFAULT_GRID_STEPS = 10;
    public static final int MIN_GRID_STEPS = 4;
    public static final int MAX_GRID_STEPS = 40;

    public final CountingMode countingMode;
    public final ThresholdMode thresholdMode;
    public final List<String> autoMethods;
    public final List<Double> fixedThresholds;
    public final int gridSteps;
    public final double minSize;
    public final double maxSize;
    public final boolean darkBackground;
    public final List<Integer> channelsToSweep;

    public ShootoutSettings(
            CountingMode countingMode,
            ThresholdMode thresholdMode,
            List<String> autoMethods,
            List<Double> fixedThresholds,
            double minSize,
            double maxSize,
            boolean darkBackground) {
        this(
                countingMode,
                thresholdMode,
                autoMethods,
                fixedThresholds,
                DEFAULT_GRID_STEPS,
                minSize,
                maxSize,
                darkBackground,
                defaultChannelsToSweep());
    }

    public ShootoutSettings(
            CountingMode countingMode,
            ThresholdMode thresholdMode,
            List<String> autoMethods,
            List<Double> fixedThresholds,
            int gridSteps,
            double minSize,
            double maxSize,
            boolean darkBackground) {
        this(
                countingMode,
                thresholdMode,
                autoMethods,
                fixedThresholds,
                gridSteps,
                minSize,
                maxSize,
                darkBackground,
                defaultChannelsToSweep());
    }

    public ShootoutSettings(
            CountingMode countingMode,
            ThresholdMode thresholdMode,
            List<String> autoMethods,
            List<Double> fixedThresholds,
            int gridSteps,
            double minSize,
            double maxSize,
            boolean darkBackground,
            List<Integer> channelsToSweep) {
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
        if (gridSteps < MIN_GRID_STEPS || gridSteps > MAX_GRID_STEPS) {
            throw new IllegalArgumentException("gridSteps must be between "
                    + MIN_GRID_STEPS + " and " + MAX_GRID_STEPS);
        }

        this.countingMode = countingMode;
        this.thresholdMode = thresholdMode;
        this.autoMethods = immutableCopy(autoMethods);
        this.fixedThresholds = immutableDoubleCopy(fixedThresholds);
        this.gridSteps = gridSteps;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.darkBackground = darkBackground;
        this.channelsToSweep = immutableChannelCopy(channelsToSweep);
    }

    public static ShootoutSettings defaults() {
        return new ShootoutSettings(
                CountingMode.PARTICLES_2D,
                ThresholdMode.AUTO_METHODS,
                defaultAutoMethods(),
                Collections.<Double>emptyList(),
                DEFAULT_GRID_STEPS,
                0.0,
                Double.POSITIVE_INFINITY,
                true,
                defaultChannelsToSweep());
    }

    public ShootoutSettings withChannelsToSweep(List<Integer> channels) {
        return new ShootoutSettings(
                countingMode,
                thresholdMode,
                autoMethods,
                fixedThresholds,
                gridSteps,
                minSize,
                maxSize,
                darkBackground,
                channels);
    }

    public static List<Integer> defaultChannelsToSweep() {
        return Collections.singletonList(Integer.valueOf(1));
    }

    public static List<String> defaultAutoMethods() {
        List<String> methods = new ArrayList<String>();
        methods.add("Default");
        methods.add("Otsu");
        methods.add("Li");
        methods.add("Triangle");
        methods.add("Huang");
        methods.add("Moments");
        methods.add("Yen");
        methods.add("MaxEntropy");
        methods.add("IsoData");
        methods.add("Minimum");
        return Collections.unmodifiableList(methods);
    }

    public static List<Double> parseFixedThresholds(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = text.split(",", -1);
        List<Double> values = new ArrayList<Double>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Fixed threshold values must not be blank.");
            }
            double value;
            try {
                value = Double.parseDouble(trimmed);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Fixed threshold '" + trimmed + "' is not a number.");
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Fixed threshold '" + trimmed + "' must be a finite number.");
            }
            values.add(Double.valueOf(value));
        }
        return Collections.unmodifiableList(values);
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

    private static List<Integer> immutableChannelCopy(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return defaultChannelsToSweep();
        }
        List<Integer> copy = new ArrayList<Integer>(values.size());
        for (Integer value : values) {
            if (value == null || value.intValue() < 1) {
                throw new IllegalArgumentException("channelsToSweep must contain positive 1-based channels");
            }
            if (!copy.contains(value)) {
                copy.add(value);
            }
        }
        if (copy.isEmpty()) {
            return defaultChannelsToSweep();
        }
        return Collections.unmodifiableList(copy);
    }
}
