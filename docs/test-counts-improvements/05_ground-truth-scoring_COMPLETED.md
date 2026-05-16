# Ground-truth scoring with colour-coded mask preview

## Why this stage exists

Today a user has no way to ask "of all the variants, which one matched my hand-counted reference best?" They eyeball counts and trust the closest number. This stage lets the user point Test Counts at a ROI file (or use ROIs already in the ROI Manager) and score every variant against it: precision, recall, F1. The mask preview shows where each method got it right and where it failed.

## Prerequisites

- `01_foundation-perf-refactor` complete.
- Depends on stage 02 for `ShootoutResult.recommended` and `recommendationReason`, because this stage can replace the plateau pick with the reference winner.

## Read first

- `src/main/java/macro/builder/analysis/ObjectCounter.java`
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:502-523` for the existing mask preview path
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:781-805` for selected-row and preview-button state
- ImageJ ROI Manager headless pattern: <https://forum.image.sc/t/roi-manager-headless/11045>
- BioVoxxel Threshold Check UX for reference (not for copying code): <https://imagej.net/plugins/biovoxxel-toolbox>

## Scope

- Add a "Reference" panel above the existing settings. It has one button "Load reference..." with a tooltip listing accepted formats, and a label showing "47 objects loaded" or "no reference" with a small clear button.
- Accepted reference formats:
  - `RoiSet.zip` from the Fiji ROI Manager (any mix of point ROIs and area ROIs).
  - Cell Counter `.xml` from the Cell Counter plugin.
  - 2-column CSV of x,y centroids (header row optional).
  - Label-image TIFF (one unique non-zero value per object) — opened via `IJ.openImage`.
- Auto-detect ROIs already in the ROI Manager and offer "Use 47 ROIs in the ROI Manager?" on dialog open.
- Add three new columns to `ShootoutResult`: `precision`, `recall`, `f1`. They are populated only when a reference is loaded.
- CSV export always includes `precision`, `recall`, and `f1` after the base columns once this stage lands; rows without a reference write blanks. Batch CSV gets the same columns but usually blanks because per-image batch references are out of scope. This keeps the schema additive and matches `00_overview.md` "CSV column order (cumulative)".
- Matching rule per result row:
  - Point ROI references match if the centroid falls inside a detected object.
  - Area ROI references match a detected object when IoU ≥ 0.5.
  - Label-image references match a detected object when IoU ≥ 0.5 against the same label id.
- When a reference is loaded, the mask preview switches to a three-colour overlay: green = true positive, cyan = missed reference, red = extra detection. Add a small legend below the preview.
- Allow sorting the table by F1 (it is `Comparable` already because the values are numeric — just register a row sorter comparator).
- If a row is starred by stage 02's plateau detector AND a reference is loaded, prefer the F1 winner instead. Update the recommendation reason to "highest agreement with your reference".

## Out of scope

- In-dialog ROI painting (out of scope for the MVP — import only).
- Bland–Altman plots (the sidecar in stage 09 can record the data; visualisation is later).
- Reference per-image in batch mode (single-image only in this stage).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/GroundTruthReference.java` | NEW | Immutable: source format, list of reference objects (point or polygon), per-object label id. |
| `src/main/java/macro/builder/analysis/GroundTruthLoader.java` | NEW | Loaders for the four formats; format detection by extension and content sniff. |
| `src/main/java/macro/builder/analysis/GroundTruthScorer.java` | NEW | Given a mask, `ShootoutSettings`, and a `GroundTruthReference`, returns `{tp, fp, fn, precision, recall, f1, perObjectStatus}`. |
| `src/main/java/macro/builder/analysis/DetectedObject.java` | NEW | Connected component geometry extracted from a mask: id, slice/stack bounds, area, centroid, and pixels or ROI shape needed for IoU. |
| `src/main/java/macro/builder/analysis/ObjectCounter.java` | MODIFY | Add NEW `detect(ImagePlus mask, ShootoutSettings settings)` and keep existing `count(mask, settings)` as the summary entry point. |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | Add `precision`, `recall`, `f1`, and `perObjectStatus` (the array used to colour the overlay). |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | After each variant's mask is built, if a reference is loaded, call the scorer. |
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | MODIFY | Hold an optional `GroundTruthReference`. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Reference panel, new columns in the table, auto-detect ROI Manager contents on open, three-colour overlay rendering in `openMaskPreview`. |
| `src/main/java/macro/builder/ui/MaskPreviewRenderer.java` | NEW | Pure renderer: given source, mask, perObjectStatus, returns an `ImagePlus` with the green/cyan/red overlay. |
| `src/test/java/macro/builder/analysis/GroundTruthLoaderTest.java` | NEW | One test per format using small fixture files in `src/test/resources/groundtruth/`. |
| `src/test/java/macro/builder/analysis/GroundTruthScorerTest.java` | NEW | Synthetic mask + known references; assert precision, recall, F1 to within 1e-6. |

## Implementation sketch

Scorer:

```java
public final class ScoreSummary {
    public final int tp, fp, fn;
    public final double precision, recall, f1;
    public final int[] perObjectStatus;  // one entry per detected object: TP=1, FP=2
    // FN list is implicit in (reference - matched).
}

public ScoreSummary score(ImagePlus mask, GroundTruthReference ref, ShootoutSettings settings) {
    // ObjectCounter.detect(...) is NEW in this stage; today only count(mask, settings) exists.
    List<DetectedObject> detected = ObjectCounter.detect(mask, settings);
    // Greedy matching by IoU descending, no double-claiming.
    // Compute precision = tp / (tp+fp), recall = tp / (tp+fn), f1 = harmonic mean.
}
```
<!-- audit:agent1 corrected preview line range, GroundTruthScorer signature, and marked ObjectCounter.detect plus DetectedObject as new stage 05 work -->

Auto-detect an already-open ROI Manager on dialog open:

```java
RoiManager rm = RoiManager.getInstance2();  // does not create the ROI Manager window
if (rm != null && rm.getCount() > 0) {
    int count = rm.getCount();
    int answer = JOptionPane.showConfirmDialog(dialog,
        "Use the " + count + " ROIs in the ROI Manager as a reference?",
        "Reference detected", JOptionPane.YES_NO_OPTION);
    if (answer == JOptionPane.YES_OPTION) loadReference(rm);
}
```

Colour-coded overlay: render the mask as grey, then draw outlines: green for TP detected objects, red for FP detected objects, cyan for FN reference objects.

Threading model:

- File chooser, ROI Manager prompt, table column visibility, and preview-window actions run on the EDT.
- Reference file parsing for `RoiSet.zip`, XML, CSV, and label TIFF runs in a `SwingWorker` or other background task; large label images must not be opened and parsed on the EDT.
- Scoring runs inside the shootout worker immediately after each variant count, using `ObjectCounter.detect(...)` and `GroundTruthScorer`.
- `MaskPreviewRenderer` is pure image construction and runs off the EDT; only `preview.show()` and window selection run on the EDT.
- In true headless mode, skip ROI Manager detection and use only direct `GroundTruthReference` fixtures or direct file parsing tests.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `GroundTruthLoaderTest` exercises each new loader format on a small fixture and asserts the expected reference-object count.
3. `GroundTruthScorerTest` exercises the new scoring path on a synthetic mask plus known references and asserts precision, recall, and F1 within `1e-6`.
4. Loading a 12-point `RoiSet.zip` on a synthetic image with 12 known objects produces F1 >= 0.99 on the correct variant.
5. The mask preview contains green, cyan, and red overlay pixels; the legend lists those colours in plain English.
6. With no reference loaded, no score columns are visible in the table, CSV score cells are blank, and the plain mask preview path still opens.
7. CSV export includes the new `precision`, `recall`, and `f1` columns in the order locked in `00_overview.md`; values are formatted with `Locale.ROOT` and blank when no reference is loaded.

## Manual smoke check

1. Open Test Counts with ROIs already in the ROI Manager and confirm the prompt offers to use the existing ROI count without creating a new manager.
2. Load a `RoiSet.zip`, run Test Counts, sort by F1, and confirm the top row is starred with the reference-winner reason.
3. Select a scored row and open the mask preview; confirm green true positives, cyan missed references, red extra detections, and the legend are visible.
4. Clear the reference, rerun, and confirm the table and preview return to the no-reference behaviour.

## Known risks

- Greedy IoU matching can under-count when many detected objects overlap one reference object. Mitigation: spell out the matching rule in a tooltip on the F1 column header and cover a one-reference/many-detections case in `GroundTruthScorerTest`.
- Point ROIs near a detected object boundary may flip TP/FP unpredictably. Mitigation: document that point-in-mask is the rule and test boundary pixels explicitly.
- Label TIFFs from Cellpose use 16-bit labels. Mitigation: read label IDs as unsigned 16-bit/32-bit values and include a 16-bit label fixture in `GroundTruthLoaderTest`.
- Large label images or overlays can blow memory on 4K x 4K images. Mitigation: parse label images slice-by-slice, estimate overlay size before rendering, and degrade to numeric scores without a coloured preview when the cap would be exceeded.
- Multi-monitor and HiDPI displays can scale overlay previews oddly. Mitigation: render overlay pixels in image coordinates, not screen coordinates, and smoke-test preview placement on a scaled display.
- Locale-dependent CSV parsing/writing can misread centroid CSVs with decimal commas. Mitigation: accept only dot-decimal numeric CSV input with a clear error, and write score CSV values with `Locale.ROOT`.
- `new RoiManager(false)` constructs a hidden manager in normal AWT runs, but it still creates a `Frame` and throws `HeadlessException` when `java.awt.headless=true`. Mitigation: true headless tests avoid `RoiManager` entirely by building `GroundTruthReference` directly or parsing `.roi`/`RoiSet.zip` entries with `ij.io.RoiDecoder`; UI code inspects an existing manager with `RoiManager.getInstance2()` only.
<!-- audit:agent2 corrected RoiManager headless pattern -->
