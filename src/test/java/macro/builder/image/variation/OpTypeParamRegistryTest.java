package macro.builder.image.variation;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.variation.ParamSpec.Scale;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OpTypeParamRegistryTest {

    private static final List<OpType> REQUIRED_COVERAGE = Arrays.asList(
            OpType.GAUSSIAN_BLUR,
            OpType.MEDIAN,
            OpType.MEAN,
            OpType.MINIMUM,
            OpType.MAXIMUM,
            OpType.VARIANCE,
            OpType.SUBTRACT_BACKGROUND,
            OpType.UNSHARP_MASK,
            OpType.GAUSSIAN_BLUR_3D,
            OpType.MEDIAN_3D,
            OpType.MINIMUM_3D);

    @Test
    public void paramsOfGaussianBlurReturnsSingleSigmaSpec() {
        List<ParamSpec> specs = OpTypeParamRegistry.paramsOf(OpType.GAUSSIAN_BLUR);
        assertEquals(1, specs.size());
        ParamSpec p = specs.get(0);
        assertEquals("Sigma", p.name);
        assertEquals("sigma", p.argKey);
        assertEquals(Scale.LOG, p.scale);
        assertEquals("px", p.unit);
        assertFalse(p.isInteger);
    }

    @Test
    public void argsForDefaultsGaussianBlurIsParseableBackToDefaults() {
        String args = OpTypeParamRegistry.argsForDefaults(OpType.GAUSSIAN_BLUR);
        assertEquals("sigma=2.0", args);

        Map<String, Double> parsed = OpTypeParamRegistry.parseArgs(OpType.GAUSSIAN_BLUR, args);
        assertEquals(Double.valueOf(2.0), parsed.get("sigma"));
    }

    @Test
    public void parseArgsExtractsNumericValueFromOverrideString() {
        Map<String, Double> parsed = OpTypeParamRegistry.parseArgs(OpType.GAUSSIAN_BLUR, "sigma=3.5");
        assertNotNull(parsed.get("sigma"));
        assertEquals(3.5, parsed.get("sigma").doubleValue(), 0.0);
    }

    @Test
    public void parseArgsFillsMissingKeysWithDefaults() {
        // UNSHARP_MASK has two keys (radius, mask). Args supplies only radius.
        // The missing 'mask' key should come back as its default (0.6).
        Map<String, Double> parsed = OpTypeParamRegistry.parseArgs(
                OpType.UNSHARP_MASK, "radius=5.0");
        assertEquals(5.0, parsed.get("radius").doubleValue(), 0.0);
        assertEquals(0.60, parsed.get("mask").doubleValue(), 0.0);
    }

    @Test
    public void parseArgsIgnoresNonNumericTokensAndBooleanFlags() {
        // ImageJ args often contain boolean flags ('stack') and quoted strings —
        // those are not modelled here, so they must be silently ignored. The
        // numeric 'sigma' value must still be extracted.
        Map<String, Double> parsed = OpTypeParamRegistry.parseArgs(
                OpType.GAUSSIAN_BLUR, "sigma=3.0 stack");
        assertEquals(3.0, parsed.get("sigma").doubleValue(), 0.0);
    }

    @Test
    public void renderArgsEmitsKeyEqualsValueForGaussianBlur() {
        Map<String, Double> values = new HashMap<String, Double>();
        values.put("sigma", 4.0);
        assertEquals("sigma=4.0", OpTypeParamRegistry.renderArgs(OpType.GAUSSIAN_BLUR, values));
    }

    @Test
    public void renderArgsFallsBackToDefaultsForUnspecifiedKeys() {
        // UNSHARP_MASK has two keys; we only supply 'radius'. 'mask' must fall
        // back to the registry default (0.6), and the order must follow paramsOf.
        Map<String, Double> values = new HashMap<String, Double>();
        values.put("radius", 5.0);
        String args = OpTypeParamRegistry.renderArgs(OpType.UNSHARP_MASK, values);
        assertEquals("radius=5.0 mask=0.6", args);
    }

    @Test
    public void renderArgsEmitsIntegerForIntegerSpecs() {
        // SUBTRACT_BACKGROUND.rolling is declared isInteger=true; renderArgs
        // must emit a bare integer even when the value is given as a double.
        Map<String, Double> values = new HashMap<String, Double>();
        values.put("rolling", 42.0);
        assertEquals("rolling=42",
                OpTypeParamRegistry.renderArgs(OpType.SUBTRACT_BACKGROUND, values));
    }

    @Test
    public void roundTripParseOfDefaultsRecoversDefaultsForEveryCoveredType() {
        for (OpType t : REQUIRED_COVERAGE) {
            String args = OpTypeParamRegistry.argsForDefaults(t);
            Map<String, Double> parsed = OpTypeParamRegistry.parseArgs(t, args);

            for (ParamSpec spec : OpTypeParamRegistry.paramsOf(t)) {
                Double value = parsed.get(spec.argKey);
                assertNotNull("Round-trip lost key " + spec.argKey + " for " + t, value);
                assertEquals("Round-trip mismatch for " + t + "/" + spec.argKey,
                        spec.defaultValue, value.doubleValue(), 0.0);
            }
        }
    }

    @Test
    public void renderArgsRoundTripsThroughParseArgs() {
        // For every covered op, render(default-map) → parse → defaults.
        for (OpType t : REQUIRED_COVERAGE) {
            Map<String, Double> defaults = new HashMap<String, Double>();
            for (ParamSpec spec : OpTypeParamRegistry.paramsOf(t)) {
                defaults.put(spec.argKey, spec.defaultValue);
            }
            String rendered = OpTypeParamRegistry.renderArgs(t, defaults);
            Map<String, Double> parsed = OpTypeParamRegistry.parseArgs(t, rendered);
            for (ParamSpec spec : OpTypeParamRegistry.paramsOf(t)) {
                assertEquals("renderArgs/parseArgs round-trip failed for " + t + "/" + spec.argKey,
                        spec.defaultValue, parsed.get(spec.argKey).doubleValue(), 0.0);
            }
        }
    }

    @Test
    public void paramsOfUnknownReturnsEmptyList() {
        assertTrue(OpTypeParamRegistry.paramsOf(OpType.UNKNOWN).isEmpty());
    }

    @Test
    public void paramsOfNullReturnsEmptyList() {
        assertTrue(OpTypeParamRegistry.paramsOf(null).isEmpty());
    }

    @Test
    public void argsForDefaultsUnknownReturnsEmptyString() {
        assertEquals("", OpTypeParamRegistry.argsForDefaults(OpType.UNKNOWN));
    }

    @Test
    public void parseArgsUnknownReturnsEmptyMap() {
        assertTrue(OpTypeParamRegistry.parseArgs(OpType.UNKNOWN, "anything=1").isEmpty());
    }

    @Test
    public void renderArgsUnknownReturnsEmptyString() {
        assertEquals("",
                OpTypeParamRegistry.renderArgs(OpType.UNKNOWN, Collections.<String, Double>emptyMap()));
    }

    @Test
    public void renderArgsTolerateNullValueMap() {
        // Null values map should fall back to defaults rather than NPE.
        assertEquals("sigma=2.0",
                OpTypeParamRegistry.renderArgs(OpType.GAUSSIAN_BLUR, null));
    }

    @Test
    public void allRequiredOpTypesHaveNonEmptyParams() {
        for (OpType t : REQUIRED_COVERAGE) {
            assertFalse("Missing coverage for " + t,
                    OpTypeParamRegistry.paramsOf(t).isEmpty());
        }
    }

    @Test
    public void paramSpecRangesAreNonDegenerateAndContainDefault() {
        // Every covered spec should have min <= default <= max — otherwise the
        // sweep generated from this spec would silently exclude its own default.
        for (OpType t : REQUIRED_COVERAGE) {
            for (ParamSpec p : OpTypeParamRegistry.paramsOf(t)) {
                assertTrue(t + "/" + p.argKey + " min must be <= max", p.min <= p.max);
                assertTrue(t + "/" + p.argKey + " default below min",
                        p.defaultValue >= p.min);
                assertTrue(t + "/" + p.argKey + " default above max",
                        p.defaultValue <= p.max);
            }
        }
    }

    @Test
    public void unsharpMaskHasTwoParamsInDeclaredOrder() {
        List<ParamSpec> specs = OpTypeParamRegistry.paramsOf(OpType.UNSHARP_MASK);
        assertEquals(2, specs.size());
        assertEquals("radius", specs.get(0).argKey);
        assertEquals("mask", specs.get(1).argKey);
    }

    @Test
    public void gaussianBlur3dHasXYZParamsInDeclaredOrder() {
        List<ParamSpec> specs = OpTypeParamRegistry.paramsOf(OpType.GAUSSIAN_BLUR_3D);
        assertEquals(3, specs.size());
        assertEquals("x", specs.get(0).argKey);
        assertEquals("y", specs.get(1).argKey);
        assertEquals("z", specs.get(2).argKey);
    }

    @Test
    public void rankFilterOpsExposeRadiusForSweeps() {
        for (OpType type : Arrays.asList(OpType.MEDIAN, OpType.MEAN,
                OpType.MINIMUM, OpType.MAXIMUM, OpType.VARIANCE)) {
            List<ParamSpec> specs = OpTypeParamRegistry.paramsOf(type);
            assertEquals(type.name(), 1, specs.size());
            assertEquals(type.name(), "radius", specs.get(0).argKey);
        }
    }

    @Test
    public void parseArgsKeyAnchorIsWordBoundaryNotSubstring() {
        // 'sigma' should not match 'asigma=' or be confused with another key
        // that happens to contain the same substring. Anchor on word boundary
        // by feeding a value where 'sigma' appears only embedded, not as a key.
        Map<String, Double> parsed = OpTypeParamRegistry.parseArgs(
                OpType.GAUSSIAN_BLUR, "presigma=9.9");
        // No real 'sigma=' key present → falls back to default 2.0.
        assertEquals(2.0, parsed.get("sigma").doubleValue(), 0.0);
    }
}
