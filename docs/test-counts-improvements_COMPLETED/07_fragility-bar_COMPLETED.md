# Fragility bar: how much the count moves under small wiggles

## Why this stage exists

Two variants with nearly identical counts can have very different reliability. One sits on a plateau and barely moves if the staining brightens by 5%; the other balances on a knife-edge and halves its count under the same wiggle. Today the user cannot tell them apart. This stage adds a small horizontal bar per row that visualises the range of counts under small threshold and intensity perturbations, plus a single sortable "Fragility" number.

## Prerequisites

- `02_auto-threshold-grid` complete (the cheapest version reuses neighbouring grid points).
- `06_quality-score-columns` complete, so this stage appends its CSV fields after `separation` and `distinctness`.
- Depends on stage 01 for the typed mask builder and `ShootoutContext` range.

## Read first

- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` after stage 02
- `src/main/java/macro/builder/analysis/ShootoutResult.java`

## Scope

- Add two new fields to `ShootoutResult`: `fragilityScore` (0..1, lower = steadier) and `fragilityCountRange` (a small `int[]` of count samples used to draw the bar).
- For each variant, compute fragility by sampling the count at:
  - threshold ± 5% of the macro output range,
  - threshold ± 10% of the macro output range,
  - one 1-pixel-shift of the macro output (use `ImageProcessor.duplicate()` then `translate(1.0, 0.0)` before re-thresholding; ImageJ fills exposed pixels with 0),
  - one 1% intensity multiplicative jitter (multiply pixels by 1.01 in a duplicate; no source mutation).
- For `AUTO_GRID` variants, the threshold-wiggle samples are *free* — reuse the neighbouring grid rows' counts. For auto-method and fixed variants, run the four extra mask builds.
- Add a "Fragility" column. The cell renderer draws a small horizontal bar inside the cell (width fixed at column width, height ~14px). The bar shows min..max count as a span centred on the variant's own count. Narrow = steady; wide = fragile. Numeric value also shown on the right of the bar.
- Add a "Run fragility checks" checkbox in the settings panel, on by default. When off, the column is empty and the runner skips the extra work.
- Tooltip on the Fragility header: "How much the count changes if the threshold or image brightness moves slightly. Lower is steadier."

## Out of scope

- Per-channel or per-slice fragility (use the whole stack as the basic image).
- A "fragility heatmap" across many wiggle sizes (sample only the four wiggles above for now).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/FragilityProbe.java` | NEW | Pure: `probe(ImagePlus processed, ShootoutSettings, double centreThreshold)` returns counts under the four wiggles. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | `fragilityScore`, `fragilityCountRange`. |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | For each variant, after counting, call `FragilityProbe` (or reuse neighbour grid rows). |
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | MODIFY | `boolean runFragilityChecks`. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Checkbox; new column; bar renderer in `ResultTableModel`. |
| `src/main/java/macro/builder/ui/FragilityBarRenderer.java` | NEW | `TableCellRenderer` that draws the bar plus number. |
| `src/test/java/macro/builder/analysis/FragilityProbeTest.java` | NEW | Synthetic stack on a plateau (low fragility), synthetic stack on a steep slope (high fragility). |

## Implementation sketch

Fragility score:

```java
public static double scoreFrom(int[] counts, int centre) {
    int min = centre, max = centre;
    for (int c : counts) { min = Math.min(min, c); max = Math.max(max, c); }
    if (centre <= 0) return max == min ? 0.0 : 1.0;
    return Math.min(1.0, (max - min) / (double) centre);
}
```

Reuse grid neighbours:

```java
// When variant is part of an AUTO_GRID sweep with neighbours at indices i-1 and i+1,
// use those counts as the "threshold ± step" samples instead of running new mask builds.
```

Renderer (simplified):

```java
public Component getTableCellRendererComponent(...) {
    ShootoutResult r = model.resultAt(row);
    int[] samples = r.fragilityCountRange;
    int centre = r.countSummary == null ? 0 : r.countSummary.count;
    // Paint a horizontal bar from min..max relative to centre.
    // Print number on the right.
}
```

CSV export adds three columns after stage 06's quality columns: `fragility_score`, `fragility_range_min`, `fragility_range_max`. See `00_overview.md` "CSV column order (cumulative)".

Threading model:

- `FragilityProbe` runs inside the shootout worker. It may reuse stage 01's per-slice worker pattern, but it must not touch Swing.
- Image wiggles are built from duplicates only; the source image and the main processed image are never mutated.
- The checkbox state is read when settings are built on the EDT; the runner treats `runFragilityChecks=false` as a feature gate and leaves the fields blank/NaN.
- `FragilityBarRenderer` runs on the EDT and only reads immutable `fragilityScore` / `fragilityCountRange` values.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `FragilityProbeTest` exercises the new probe on a plateau synthetic stack and a steep-slope synthetic stack; the steep-slope score is at least 0.3 higher.
3. With `runFragilityChecks` on, an `AUTO_GRID` sweep does no extra mask builds for threshold-neighbour samples; a pure-auto-methods sweep does at most 4 extra mask builds per method.
4. The bar renderer maps the plateau sample range to <= 25% of the cell width and the knife-edge sample range to >= 60% of the cell width in a renderer unit test or screenshot check.
5. With the checkbox off, no fragility probe method is called, the column cells are blank, and the run time is within 5% of the same stage 06 sweep on a developer laptop.
6. CSV export includes the three fragility columns in the order locked in `00_overview.md`; values are blank when `runFragilityChecks` is off or the row failed, and numeric values use `Locale.ROOT`.

## Known risks

- The 1-pixel shift drops touching object pairs, which is legitimate sensitivity rather than a bug. Mitigation: document this in the tooltip and keep the shift as only one sample among the full probe set.
- On very small images (< 64 px wide), the zero-filled edge from `translate(...)` can dominate the score. Mitigation: if the image is smaller than 64 px wide, use a manual shift helper that fills vacated edge pixels with the image mean; ImageJ's `translate` API has no mean-fill option.
<!-- audit:agent2 corrected ImageProcessor.translate signature and edge-fill behaviour -->
- Virtual stacks can make the extra wiggle samples slow because each duplicate may fetch slices from disk. Mitigation: reuse grid-neighbour counts when available, process one duplicate at a time, and skip fragility with a clear message if a virtual stack exceeds the time/memory budget.
- Very large images can multiply memory use during intensity-jitter duplicates. Mitigation: estimate duplicate memory before each probe, keep only one probe image alive, and skip fragility rather than retaining multiple 4K x 4K masks.
- Float jitter can turn `NaN`/infinite pixels into unstable values. Mitigation: preserve non-finite pixels as background and assert finite fragility scores in `FragilityProbeTest`.
- Users may read the bar as a confidence interval. Mitigation: the tooltip must say `how much the count changes if the image wiggled`, not `confidence in the count`.
