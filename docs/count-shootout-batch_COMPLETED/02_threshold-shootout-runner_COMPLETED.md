# Single-image threshold shootout runner

## Why this stage exists

Users need to know what the current macro would produce as counts, not just what the processed image looks like. This stage builds the reusable runner that applies the current macro to a duplicate, then tests auto threshold methods and fixed numeric thresholds on the macro output.

## Prerequisites

- `01_count-model-native-counter.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/count-shootout-batch/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:375-390` for current preview execution.
- `src/main/java/macro/builder/Macro_Builder.java:421-440` for duplicate run behavior.
- `src/main/java/macro/builder/Macro_Builder.java:540-548` for current duplicate helper.
- `src/main/java/macro/builder/image/FilterExecutor.java:246-296` for macro execution and legacy fallback.
- `src/main/java/macro/builder/image/FilterExecutor.java:883-936` for stack-level operations.

## Scope

- Add `ThresholdShootoutRunner`.
- Run the current macro on a duplicate image.
- Measure macro output min/max before thresholding.
- Apply auto threshold methods.
- Apply fixed numeric thresholds in native processed-image intensity scale.
- Convert thresholded results to binary masks for `ObjectCounter`.
- Return result rows and optional mask previews.

## Out of scope

- Swing controls and table rendering; stage 03 owns that.
- Batch file iteration; stage 04 owns that.
- Saving reusable batch macros; stage 05 owns that.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | NEW | Core single-image macro, threshold, and count workflow. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | Add fields needed by the runner, such as threshold label, threshold value, image range, mask preview, and error. |
| `src/test/java/macro/builder/analysis/ThresholdShootoutRunnerTest.java` | NEW | Tests for fixed threshold behavior and result shaping where possible without Fiji UI. |

## Implementation sketch

Default auto methods:

```java
Default, Otsu, Li, Triangle, Huang, Moments, Yen, MaxEntropy, IsoData, Minimum
```

Runner shape:

```java
public final class ThresholdShootoutRunner {
    public List<ShootoutResult> run(ImagePlus source, String macro, ShootoutSettings settings);
}
```

High-level flow:

```java
ImagePlus processed = duplicate(source);
FilterExecutor.runThreadSafe(processed, macro);
Range range = measureRange(processed);

for (String method : settings.autoMethods) {
    ImagePlus mask = thresholdAuto(processed, method, settings);
    CountSummary count = ObjectCounter.count(mask, settings);
    rows.add(ShootoutResult.success(method, count, range, mask));
}

for (double value : settings.fixedThresholds) {
    ImagePlus mask = thresholdFixed(processed, value, settings);
    CountSummary count = ObjectCounter.count(mask, settings);
    rows.add(ShootoutResult.success("Fixed " + value, count, range, mask));
}
```

Fixed threshold rule:

```java
// Do not convert processed to 8-bit first.
// Apply lower threshold in processed image's native value range.
lower = fixedValue;
upper = observedMaxOrBitDepthMax(processed);
binaryPixel = pixel >= lower && pixel <= upper ? 255 : 0;
```

Prefer native Java mask creation over `IJ.run("Convert to Mask")` so batch runs do not touch global ImageJ state.

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. A fixed threshold on a 16-bit synthetic image treats `2000` as native intensity `2000`, not as an 8-bit value.
3. Runner leaves the source image unchanged.
4. Runner returns failed rows instead of throwing away the whole shootout when one threshold method fails.

## Known risks

- ImageJ auto-threshold behavior can differ by bit depth. Preserve native intensity for fixed thresholds no matter what; if auto thresholds need histogram scaling, report the final threshold value in native units.
- Large stacks can create many masks. Close or flush intermediate images promptly.
