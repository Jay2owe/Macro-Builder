# Count models and native object counter

## Why this stage exists

The whole feature depends on a stable way to count objects after thresholding. This stage creates the shared settings/result model and a native 2D/3D connected-object counter so later stages do not depend on Fiji's Results table or optional 3D plugins.

## Prerequisites

None.

## Read first

- `docs/count-shootout-batch/00_overview.md`
- `AGENTS.md`
- `docs/DEVELOPER.md`
- `src/main/java/macro/builder/image/FilterExecutor.java:536-545` for stack copying patterns.
- `src/main/java/macro/builder/image/FilterExecutor.java:658-675` for stack iteration style.
- `src/test/java/macro/builder/image/FilterMacroParserTest.java:12-38` for current JUnit style.

## Scope

- Add `macro.builder.analysis.ShootoutSettings`.
- Add `macro.builder.analysis.ShootoutResult`.
- Add `macro.builder.analysis.ObjectCounter`.
- Add unit tests for 2D and 3D connected components.
- Count binary foreground objects on an already-thresholded mask.
- Support min/max size filters.

## Out of scope

- Running macros or thresholds; stage 02 owns that.
- Swing UI; stage 03 owns that.
- Batch file opening; stage 04 owns that.
- Saved batch macros; stage 05 owns that.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | NEW | Shared settings for count mode, threshold mode, methods, fixed values, and size filters. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | NEW | One result row for a threshold/count variant. |
| `src/main/java/macro/builder/analysis/ObjectCounter.java` | NEW | Native 2D/3D connected-component counting. |
| `src/test/java/macro/builder/analysis/ObjectCounterTest.java` | NEW | Regression tests for 2D and 3D counting behavior. |

## Implementation sketch

Use explicit enums so UI, runner, batch, and export cannot drift:

```java
public final class ShootoutSettings {
    public enum CountingMode { PARTICLES_2D, OBJECTS_3D }
    public enum ThresholdMode { AUTO_METHODS, FIXED_VALUES, AUTO_AND_FIXED }

    public final CountingMode countingMode;
    public final ThresholdMode thresholdMode;
    public final List<String> autoMethods;
    public final List<Double> fixedThresholds;
    public final double minSize;
    public final double maxSize;
    public final boolean darkBackground;
}
```

`ObjectCounter` should accept a binary `ImagePlus` and count foreground voxels. Use 8-connected neighbors in 2D and 26-connected neighbors in 3D unless tests show Fiji parity needs stricter connectivity.

```java
public final class ObjectCounter {
    public static CountSummary count(ImagePlus mask, ShootoutSettings settings);

    public static final class CountSummary {
        public final int count;
        public final double meanSize;
        public final double totalForeground;
        public final double coverage;
    }
}
```

Foreground should be white (`255`) by default. If `darkBackground` later proves confusing, keep the field but document the actual mask convention in stage 03.

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. Tests cover two separated 2D objects, one filtered-out small object, one 3D object spanning multiple slices, and two separated 3D objects.
3. The counter does not read or write `ResultsTable`, `RoiManager`, or `WindowManager`.

## Known risks

- 26-connected 3D counting can merge diagonally touching structures that some users would count separately. If this is a problem, add a connectivity setting later, not in this stage.
- Calibration-aware size units may be requested later. Store raw pixel/voxel counts first; calibrated reporting can be layered on in the UI.
