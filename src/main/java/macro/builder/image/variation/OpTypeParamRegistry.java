package macro.builder.image.variation;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.variation.ParamSpec.Scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static registry mapping each filter {@code OpType} to its numeric parameter
 * specs (defaults, min/max, log/linear scale).
 *
 * <p>Drives both the "sweep parameter" flow (give me a node's knobs and a
 * sensible range) and the "swap filter" flow (give me sensible defaults for
 * the substituted filter).
 *
 * <p>Default values are grounded in {@code FilterExecutor.executeOpOnSlice}
 * and {@code applyFilters3D} fallbacks — when the user supplies no value for a
 * parameter, the executor uses these numbers, so they are the "natural" starting
 * point for a fresh substitution.
 *
 * <p>Limitations:
 * <ul>
 *   <li>Only numeric {@code key=value} parameters are modelled. Quoted-string
 *       keys ({@code method="Bernsen"}), boolean flags ({@code stack},
 *       {@code normalize}), and nested args are ignored by {@link #parseArgs}
 *       and not emitted by {@link #argsForDefaults}.
 *   <li>{@link OpType}s not listed here return an empty {@code paramsOf} list —
 *       they cannot be sweepable in v1, which is acceptable.
 * </ul>
 */
public final class OpTypeParamRegistry {

    private static final Map<OpType, List<ParamSpec>> SPECS;

    static {
        Map<OpType, List<ParamSpec>> m = new EnumMap<OpType, List<ParamSpec>>(OpType.class);

        // GAUSSIAN_BLUR — sigma defaults to 2.0 in FilterExecutor (line ~859).
        // Sigma is naturally log-scaled: 0.5 → edge-preserving, 2 → mild blur,
        // 10 → heavy denoise.
        m.put(OpType.GAUSSIAN_BLUR, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Sigma", "sigma", 2.0, 0.5, 10.0, Scale.LOG, "px", false))));

        // MEDIAN — radius defaults to 2.0 in FilterExecutor.
        m.put(OpType.MEDIAN, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Radius", "radius", 2.0, 1.0, 10.0, Scale.LINEAR, "px", false))));

        // MEAN — radius defaults to 2.0 in FilterExecutor.
        m.put(OpType.MEAN, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Radius", "radius", 2.0, 1.0, 10.0, Scale.LINEAR, "px", false))));

        // SUBTRACT_BACKGROUND — rolling-ball radius defaults to 20.0 in
        // FilterExecutor. ImageJ's rolling-ball UI takes an integer.
        m.put(OpType.SUBTRACT_BACKGROUND, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Rolling-ball radius", "rolling", 20.0, 5.0, 500.0,
                        Scale.LOG, "px", true))));

        // UNSHARP_MASK — radius defaults to 10.0, mask weight to 0.60 in
        // FilterExecutor.
        m.put(OpType.UNSHARP_MASK, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Radius", "radius", 10.0, 1.0, 50.0, Scale.LOG, "px", false),
                new ParamSpec("Mask weight", "mask", 0.60, 0.1, 0.9,
                        Scale.LINEAR, "", false))));

        // GAUSSIAN_BLUR_3D — x and y default to 2.0, z to 1.0 (FilterExecutor
        // line ~1035). Each axis has its own sigma in voxel units.
        m.put(OpType.GAUSSIAN_BLUR_3D, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Sigma X", "x", 2.0, 0.5, 10.0, Scale.LOG, "px", false),
                new ParamSpec("Sigma Y", "y", 2.0, 0.5, 10.0, Scale.LOG, "px", false),
                new ParamSpec("Sigma Z", "z", 1.0, 0.5, 5.0, Scale.LOG, "px", false))));

        // MEDIAN_3D — applyFilters3D fallbacks: x=2.0, y=2.0, z=1.0.
        m.put(OpType.MEDIAN_3D, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Radius X", "x", 2.0, 1.0, 10.0, Scale.LINEAR, "px", false),
                new ParamSpec("Radius Y", "y", 2.0, 1.0, 10.0, Scale.LINEAR, "px", false),
                new ParamSpec("Radius Z", "z", 1.0, 1.0, 5.0, Scale.LINEAR, "px", false))));

        // MINIMUM_3D — same axis-radius shape as MEDIAN_3D.
        m.put(OpType.MINIMUM_3D, Collections.unmodifiableList(Arrays.asList(
                new ParamSpec("Radius X", "x", 2.0, 1.0, 10.0, Scale.LINEAR, "px", false),
                new ParamSpec("Radius Y", "y", 2.0, 1.0, 10.0, Scale.LINEAR, "px", false),
                new ParamSpec("Radius Z", "z", 1.0, 1.0, 5.0, Scale.LINEAR, "px", false))));

        SPECS = Collections.unmodifiableMap(m);
    }

    private OpTypeParamRegistry() {}

    /**
     * Returns the parameter specs for {@code type}. Empty list if the type is
     * unmodelled (does not throw).
     */
    public static List<ParamSpec> paramsOf(OpType type) {
        if (type == null) return Collections.emptyList();
        List<ParamSpec> specs = SPECS.get(type);
        return specs == null ? Collections.<ParamSpec>emptyList() : specs;
    }

    /**
     * Canonical args string built from {@code defaultValue} of every parameter,
     * in {@link #paramsOf} order. Empty string for unmodelled types.
     */
    public static String argsForDefaults(OpType type) {
        StringBuilder sb = new StringBuilder();
        for (ParamSpec p : paramsOf(type)) {
            if (sb.length() > 0) sb.append(' ');
            appendKeyValue(sb, p, p.defaultValue);
        }
        return sb.toString();
    }

    /**
     * Extracts the numeric values of every modelled parameter from {@code args}.
     * Missing keys are filled with their {@link ParamSpec#defaultValue}, so the
     * returned map always contains every key from {@link #paramsOf}.
     *
     * <p>Non-numeric tokens, quoted strings, and bare boolean flags are ignored.
     * Returns an empty map for unmodelled types.
     */
    public static Map<String, Double> parseArgs(OpType type, String args) {
        List<ParamSpec> specs = paramsOf(type);
        if (specs.isEmpty()) return Collections.emptyMap();
        String safeArgs = args == null ? "" : args;

        Map<String, Double> out = new LinkedHashMap<String, Double>();
        for (ParamSpec p : specs) {
            Double parsed = extractNumeric(safeArgs, p.argKey);
            out.put(p.argKey, parsed == null ? Double.valueOf(p.defaultValue) : parsed);
        }
        return out;
    }

    /**
     * Renders an args string from the supplied {@code values} map, in the
     * {@link #paramsOf} order. Missing keys fall back to {@link ParamSpec#defaultValue}.
     */
    public static String renderArgs(OpType type, Map<String, Double> values) {
        Map<String, Double> safeValues = values == null ? Collections.<String, Double>emptyMap() : values;
        StringBuilder sb = new StringBuilder();
        for (ParamSpec p : paramsOf(type)) {
            Double v = safeValues.get(p.argKey);
            double effective = v == null ? p.defaultValue : v.doubleValue();
            if (sb.length() > 0) sb.append(' ');
            appendKeyValue(sb, p, effective);
        }
        return sb.toString();
    }

    private static void appendKeyValue(StringBuilder sb, ParamSpec p, double value) {
        sb.append(p.argKey).append('=');
        if (p.isInteger) sb.append((int) value);
        else sb.append(value);
    }

    /** Numeric-value extractor anchored on a word boundary, mirroring {@code Op.getParam}. */
    private static Double extractNumeric(String args, String key) {
        Pattern p = Pattern.compile(
                "(?:^|[^A-Za-z0-9_])" + Pattern.quote(key)
                        + "\\s*=\\s*(-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)");
        Matcher m = p.matcher(args);
        if (m.find()) {
            try {
                return Double.valueOf(m.group(1));
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
        return null;
    }

    /** All OpTypes with a non-empty registry entry. Useful for tests. */
    static List<OpType> coveredTypes() {
        return Collections.unmodifiableList(new ArrayList<OpType>(SPECS.keySet()));
    }
}
