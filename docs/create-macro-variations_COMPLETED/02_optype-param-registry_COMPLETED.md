# Stage 02 — OpType parameter registry

## Why this stage exists

When a user picks "sweep parameter" on a Gaussian node, the dialog needs to know: which knobs does Gaussian have, what's a reasonable default, what range makes sense, and is the parameter naturally swept on a linear or log scale? When the user picks "swap filter" and substitutes Gaussian with Bilateral, the dialog needs sensible Bilateral defaults so the user doesn't have to Google `bilateral filter sigma` mid-workflow. This stage builds the static knowledge base that drives both flows.

## Prerequisites

None.

## Read first

- `docs/create-macro-variations/00_overview.md`
- `docs/create-macro-variations/01_dag-mutations-and-sampler.md` (for the `VariantAxis.AlternativeValue` shape this stage will populate)
- `src/main/java/macro/builder/image/FilterMacroParser.java` lines 37–95 (`OpType` enum + the args parsing for each op — read these to copy real argument names, e.g. `sigma=`, `radius=`, `rolling=`)
- `src/main/java/macro/builder/image/FilterExecutor.java` — search for each `OpType` to see how its args are actually consumed at runtime. Use these as ground truth, not docs.

## Scope

- New `OpTypeParamRegistry` class with two public methods:
  - `List<ParamSpec> paramsOf(OpType type)` — what parameters does this op take?
  - `String argsForDefaults(OpType type)` — produce a canonical args string with default values, suitable for a fresh substitution.
- New `ParamSpec` value type: `String name`, `String argKey` (the literal key in the args string e.g. `"sigma"`), `double defaultValue`, `double min`, `double max`, `Scale scale` (`LINEAR` or `LOG`), `String unit`, `boolean isInteger`.
- New helper `String renderArgs(OpType type, Map<String, Double> values)` that takes a partial values map and produces an args string, falling back to defaults for unspecified keys.
- New helper `Map<String, Double> parseArgs(OpType type, String args)` that extracts current values from an op's args string.
- Coverage required for the **most common bioimage filters in the codebase**: `GAUSSIAN_BLUR`, `MEDIAN`, `MEAN`, `SUBTRACT_BACKGROUND`, `UNSHARP_MASK`, `GAUSSIAN_BLUR_3D`, `MEDIAN_3D`, `MINIMUM_3D`. Other ops can return an empty `paramsOf` list — they simply won't be sweepable in v1 (acceptable).
- All op specs hardcoded as immutable `Map<OpType, List<ParamSpec>>` initialised in a static block.

## Out of scope

- The UI that consumes the registry — that's stage 05.
- Adding new `OpType` values to the enum — keep what's already there.
- Defaults for "compatible filter substitutions" (i.e. which filters can replace which) — handled in stage 05 by just listing alternatives within the same broad category.
- Argument parsing edge cases for legacy-tier ops with `commandName` — those don't go through this registry.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/image/variation/OpTypeParamRegistry.java` | NEW | Static registry, hardcoded specs |
| `src/main/java/macro/builder/image/variation/ParamSpec.java` | NEW | Value type for one parameter |
| `src/test/java/macro/builder/image/variation/OpTypeParamRegistryTest.java` | NEW | Round-trip args parsing + render, default coverage |

## Implementation sketch

```java
// ParamSpec.java
public final class ParamSpec {
    public enum Scale { LINEAR, LOG }
    public final String name;       // human-readable: "Sigma"
    public final String argKey;     // literal key in args string: "sigma"
    public final double defaultValue;
    public final double min;
    public final double max;
    public final Scale scale;
    public final String unit;       // "px", "" if dimensionless
    public final boolean isInteger;
    // ctor + equals + hashCode
}
```

```java
// OpTypeParamRegistry.java
public final class OpTypeParamRegistry {
    private static final Map<OpType, List<ParamSpec>> SPECS;
    static {
        Map<OpType, List<ParamSpec>> m = new EnumMap<>(OpType.class);
        m.put(OpType.GAUSSIAN_BLUR, Arrays.asList(
            new ParamSpec("Sigma", "sigma", 2.0, 0.5, 10.0, Scale.LOG, "px", false)));
        m.put(OpType.MEDIAN, Arrays.asList(
            new ParamSpec("Radius", "radius", 2.0, 1.0, 10.0, Scale.LINEAR, "px", false)));
        m.put(OpType.SUBTRACT_BACKGROUND, Arrays.asList(
            new ParamSpec("Rolling-ball radius", "rolling", 50.0, 10.0, 500.0, Scale.LOG, "px", true)));
        m.put(OpType.UNSHARP_MASK, Arrays.asList(
            new ParamSpec("Radius", "radius", 1.0, 0.5, 10.0, Scale.LOG, "px", false),
            new ParamSpec("Mask weight", "mask", 0.6, 0.1, 0.9, Scale.LINEAR, "", false)));
        // ... others
        SPECS = Collections.unmodifiableMap(m);
    }

    public static List<ParamSpec> paramsOf(OpType type) {
        return SPECS.getOrDefault(type, Collections.emptyList());
    }

    public static String argsForDefaults(OpType type) {
        StringBuilder sb = new StringBuilder();
        for (ParamSpec p : paramsOf(type)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(p.argKey).append('=');
            if (p.isInteger) sb.append((int) p.defaultValue);
            else sb.append(p.defaultValue);
        }
        return sb.toString();
    }

    public static Map<String, Double> parseArgs(OpType type, String args) {
        // simple " key=value " parser. ImageJ args are space-separated key=value pairs.
        // Tolerate quoted values, missing keys (return defaults).
    }

    public static String renderArgs(OpType type, Map<String, Double> values) {
        // Build args string from values, falling back to defaults for unspecified keys.
        // Preserve key order from paramsOf(type).
    }
}
```

**Where to find ground-truth defaults.** Read `FilterExecutor.executeOpOnSlice` (and `executeOpOnStack`) — the existing code already has documented behaviour for each op. The `ParamSpec.defaultValue` should match what users actually use as a starting point in practice; `min`/`max` should be wide enough to cover plausible real values (e.g. Gaussian sigma 0.5–10 covers everything from edge-preserving denoise to heavy smoothing) and tight enough that 5 evenly-spaced steps are interesting.

**Log vs linear scale.** Sigma, rolling-ball radius, and any param where doubling is a meaningful step should be `LOG`. Threshold values, percentages, and counts should be `LINEAR`. The dialog (stage 05) uses this to pick step values — `LOG` produces geometric spacing, `LINEAR` produces arithmetic.

## Exit gate

1. `mvn test -Dtest=OpTypeParamRegistryTest` passes.
2. Test coverage:
   - `paramsOf(GAUSSIAN_BLUR)` returns exactly one `ParamSpec` named "Sigma" with `argKey="sigma"`.
   - `argsForDefaults(GAUSSIAN_BLUR)` returns `"sigma=2.0"` (or whatever default you choose — assert it's parseable back).
   - `parseArgs(GAUSSIAN_BLUR, "sigma=3.5")` returns a map containing `"sigma" -> 3.5`.
   - `renderArgs(GAUSSIAN_BLUR, Map.of("sigma", 4.0))` returns `"sigma=4.0"`.
   - Round-trip: `parseArgs(t, argsForDefaults(t))` produces every defaultValue for every covered op type.
   - `paramsOf(UNKNOWN)` returns empty list (no exception).
3. All 8 listed op types have non-empty `paramsOf` results.
4. `mvn compile` produces no new warnings.

## Known risks

- ImageJ args strings have quirks: some keys take quoted strings (`"create"`, `"none"`), some are boolean flags (just the keyword present means true), some are nested. The registry only needs to handle numeric `key=value` for v1 — non-numeric keys can be ignored. Document this limitation in a class-level Javadoc comment.
- Defaults must match user expectations to avoid surprise. When in doubt, pick the value that the existing `FilterExecutor` code uses when no value is supplied. If `FilterExecutor` doesn't have a fallback, pick the value that appears in existing test fixtures or sample DAGs.
- Substituting a 2D filter (`MEDIAN`) for a 3D filter (`MEDIAN_3D`) is technically possible but probably not what the user wants. Stage 05 will restrict the substitution alternatives offered. This stage just provides defaults — it doesn't restrict.
