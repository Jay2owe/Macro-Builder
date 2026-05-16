# Auto threshold grid with plateau pick

## Why this stage exists

Users do not know what numbers to type in the "Fixed thresholds" field. The macro output's intensity scale is whatever the macro produced and varies image to image. This stage replaces typing with an auto-spaced grid over the actual macro output range, then stars the row where the count stops moving (the plateau) so the user does not have to eyeball it.

## Prerequisites

- `01_foundation-perf-refactor` complete.

## Read first

- `src/main/java/macro/builder/analysis/ShootoutSettings.java`
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:60-170` for the settings UI
- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` after stage 01

## Scope

- Add a fourth threshold mode `AUTO_GRID` to `ShootoutSettings.ThresholdMode`. Label in the UI: "Auto grid (recommended)".
- Add a "Grid steps" spinner next to the threshold mode dropdown (default 10, range 4–40).
- In the runner, when the mode is `AUTO_GRID`, generate evenly spaced thresholds across the cached range and run them like fixed thresholds.
- Vendor a small Kneedle-style plateau detector under `src/main/java/macro/builder/analysis/PlateauFinder.java`. Given count-vs-threshold, return the index of the plateau midpoint, or -1 if no plateau is found. Use the single-file `etam4260/kneedle` implementation as the algorithm reference (`https://github.com/etam4260/kneedle/blob/main/R/kneedle.R`, MIT License), or write an equivalent clean-room Java version with that URL and license named in the file header.
- After the sweep, if a plateau is found, set a "recommended" flag on that `ShootoutResult`. Render the recommended row with a star in the first column and a tooltip explaining why ("count barely changed across this region").
- Add a "Copy recommended value" button next to the existing Export CSV button.

## Out of scope

- Charts (stage 03 owns those).
- Anything that needs ground truth.
- Touching the fragility or agreement columns.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | MODIFY | Add `AUTO_GRID` mode and `gridSteps` field. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | Add `boolean recommended` and `String recommendationReason`. |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | Generate grid thresholds; call `PlateauFinder` after the sweep. |
| `src/main/java/macro/builder/analysis/PlateauFinder.java` | NEW | Vendored single-file plateau detector. MIT attribution in the file header. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | New dropdown entry, grid-steps spinner, star renderer in the table, "Copy recommended value" button. |
| `src/test/java/macro/builder/analysis/PlateauFinderTest.java` | NEW | Tests on hand-crafted curves: clean plateau, monotonic decay, two plateaus, noisy curve. |
| `src/test/java/macro/builder/analysis/ThresholdShootoutRunnerGridTest.java` | NEW | End-to-end test: synthetic stack, AUTO_GRID with 10 steps, asserts one row is flagged recommended. |

## Implementation sketch

Grid generation:

```java
static List<Double> gridThresholds(ShootoutContext ctx, int steps) {
    List<Double> out = new ArrayList<>(steps);
    double span = ctx.rangeMax - ctx.rangeMin;
    if (span <= 0 || steps < 2) return Collections.singletonList(ctx.rangeMin);
    for (int i = 0; i < steps; i++) {
        out.add(ctx.rangeMin + span * i / (double)(steps - 1));
    }
    return out;
}
```

Keep `gridThresholds(...)` package-private so later analysis helpers such as `BackSolver` can reuse the same spacing rule.
<!-- audit:agent1 corrected gridThresholds visibility because later stages call it from another class -->

Plateau detector (sketch — full algorithm in `PlateauFinder.java`):

```java
// 1. Build counts[] aligned with thresholds[].
// 2. Compute |dCount/dThreshold| between neighbours.
// 3. Find the longest run of indices where |slope| < epsilon * maxCount.
// 4. Return midpoint of that run, plus a reason string with the count variance.
// 5. If the longest run is shorter than 3 steps, return -1.
```
<!-- audit:agent2 replaced unverified Apache-2 Kneedle attribution with etam4260/kneedle MIT source -->

Star rendering uses a custom `TableCellRenderer` on the Variant column; tooltip comes from `recommendationReason`.

Threading model:

- Grid generation, variant execution, and plateau detection run inside the existing shootout `SwingWorker`.
- The star renderer, tooltip text, copy button state, and clipboard write run on the EDT. Clipboard failures should show a message and leave the results table intact.
- No ForkJoinPool work is introduced here beyond the mask-building helper added in stage 01.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `PlateauFinderTest` covers clean plateau, monotonic decay, two plateaus, and noisy curve cases; each expected index is asserted exactly.
3. `ThresholdShootoutRunnerGridTest` exercises the new `AUTO_GRID` runner path on a synthetic image with a clear bimodal histogram, requests 10 steps, and asserts exactly 10 rows with exactly one recommended row.
4. On a monotonic-decay curve, no row is starred and the status bar says exactly `no stable plateau found`.
5. "Copy recommended value" puts the threshold value on the system clipboard formatted with `Locale.ROOT` and no trailing zeros; a value of `12.5` copies as `12.5`, not `12,5`.
6. Lowering grid steps below 6 shows the warning within one click of changing the spinner and still lets the user run the sweep.

## Known risks

- The plateau detector's epsilon needs a default that works across 8-bit, 16-bit, and float outputs. Mitigation: express it as a fraction of `maxCount`, not an absolute count, and cover low/high dynamic range curves in `PlateauFinderTest`.
- Fewer than 6 grid points makes the plateau call unreliable. Mitigation: warn in the UI when the spinner is below 6 and require `ThresholdShootoutRunnerGridTest` to prove no recommendation is produced from fewer than 3 stable points.
- When the user mixes `AUTO_GRID` with `AUTO_METHODS`, the grid feeds the fixed bucket. Mitigation: recommendation logic filters to `GRID` source rows only, with a test where auto-method rows are present but not eligible for the star.
- Locale-dependent decimal formatting can turn copied thresholds or user-typed fixed values into comma decimals on some machines. Mitigation: parse and format all threshold values with `Locale.ROOT`, and include a unit test for `12.5` under a comma-decimal default locale.
