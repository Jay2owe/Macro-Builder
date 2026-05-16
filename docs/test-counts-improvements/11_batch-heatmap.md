# Batch heatmap window

## Why this stage exists

After a long batch run, the user is left with a CSV that may have hundreds or thousands of rows. Today they open it in Excel, pivot, conditionally format, and eyeball patterns. A purpose-built heatmap window opens at the end of the batch with files on one axis and methods on the other, coloured by count (or F1 if ground truth was used). Outliers — both bad files and unstable methods — pop out without spreadsheet work.

## Prerequisites

- `04_batch-bioformats-and-channels` complete (the CSV schema with file/series/channel/variant columns is what the heatmap consumes).
- `09_macro-roundtrip-and-sidecar` complete if following the numbered programme; score columns from stages 05, 07, and 08 are optional inputs and must be detected by header name.
- Depends on stage 10 for the single-variant helper when executing strictly in numbered order; if stage 11 is pulled ahead as post-MVP work, add the helper here instead of creating a second implementation later.

## Read first

- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java` after stage 04
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:540-700` for the batch flow
- ImageJ `Plot` for axis-rendering reference (we'll roll our own heatmap, not use `Plot` — but the look-and-feel should match)

## Scope

- After a successful batch run, automatically open a `BatchHeatmapWindow` (a modeless `JFrame`) only when `!GraphicsEnvironment.isHeadless()`. The window is also openable later from a new "Open batch heatmap..." button that takes a CSV path.
- Layout:
  - Heatmap cell grid: one row per (file, series, channel) tuple, one column per threshold variant.
  - Colour scale: viridis (perceptually uniform). Vendor the standard 256-colour table from Bokeh's `palettes.py` (`https://github.com/bokeh/bokeh/blob/branch-3.4/src/bokeh/palettes.py`), which carries the Matplotlib viridis table under CC0/public domain dedication. Render a small vertical scale bar on the right.
  - Toggle: "Colour by [Count | F1 | Agreement | Fragility]". Only enabled options appear (skip those with no data).
  - Toggle: "Normalise per row" (so file-to-file count differences do not drown method-to-method differences).
  - Click any cell → opens the corresponding `(file, series, channel)`, re-runs the single variant on it, shows the mask in a small popup. This is a "drill-in" path, not a re-sweep.
  - File list on the left axis is clickable: clicking a row label highlights it; clicking a column label highlights the column.
- Render the heatmap as a `BufferedImage` painted onto a `JPanel`; do not use a JTable (too slow for thousands of cells).
- Window remembers its size between runs (`Preferences.userNodeForPackage`).
- Parse CSV columns by the locked headers in `00_overview.md` "CSV column order (cumulative)"; unknown future columns are ignored.
- If the matrix/render estimate exceeds `00_overview.md` "Memory budget", open with row grouping/downsampling instead of allocating a huge image.

## Out of scope

- Editing the CSV from the heatmap (read-only view).
- Statistical tests (mean, median per row/column) — display only.
- Export of the heatmap as PNG (nice-to-have; leave a TODO).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/ui/batch/BatchHeatmapWindow.java` | NEW | The window: layout, toggles, drill-in. |
| `src/main/java/macro/builder/ui/batch/HeatmapRenderer.java` | NEW | Pure: takes a 2D `double[][]` plus optional normalisation, returns a `BufferedImage`. |
| `src/main/java/macro/builder/ui/batch/ViridisPalette.java` | NEW | 256-entry RGB lookup for the colour scale. |
| `src/main/java/macro/builder/analysis/BatchHeatmapModel.java` | NEW | Parses the batch CSV (or accepts the in-memory `List<BatchShootoutResult>`) and shapes it into a `double[][]`. |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | Add or reuse a package-private single-variant helper for drill-in; no public single-variant runner exists today. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Auto-open after batch; "Open batch heatmap..." button. |
| `src/test/java/macro/builder/ui/batch/HeatmapRendererTest.java` | NEW | Asserts the rendered image is the expected size and that two given values map to different colours. |
| `src/test/java/macro/builder/analysis/BatchHeatmapModelTest.java` | NEW | Synthetic batch results; assert correct shaping of the matrix and correct handling of missing cells (NaN). |

## Implementation sketch

Model shape:

```java
public final class BatchHeatmapModel {
    private final List<String> rowLabels;     // "file | series | channel"
    private final List<String> columnLabels;  // variant names
    private final double[][] values;          // [row][col], NaN = missing

    public double[][] matrix(MetricKind kind, boolean normalisePerRow) { ... }
}
```

Renderer:

```java
public static BufferedImage render(double[][] matrix, ViridisPalette palette, int cellW, int cellH) {
    int rows = matrix.length, cols = matrix[0].length;
    BufferedImage img = new BufferedImage(cols * cellW, rows * cellH, BufferedImage.TYPE_INT_RGB);
    double min = nanMin(matrix), max = nanMax(matrix);
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            double v = matrix[r][c];
            int rgb = Double.isNaN(v) ? MISSING : palette.colour((v - min) / (max - min));
            fillRect(img, c * cellW, r * cellH, cellW, cellH, rgb);
        }
    }
    return img;
}
```

Drill-in: take the row label, parse `(file, series, channel)`, open the image via `BioFormatsSeriesProvider` (reused in stage 04), run a single variant on it, show the mask in a small popup. Add the single-variant runner here if stage 10 has not already added it.
<!-- audit:agent1 corrected BioFormatsSeriesReader reference and marked the single-variant runner helper as new/reused work -->

Threading model:

- Batch execution and CSV writing remain in the batch `SwingWorker`.
- Auto-open is scheduled from `done()` on the EDT only when not headless; model parsing for large CSVs runs in a heatmap worker before the window is shown.
- `HeatmapRenderer` is pure and can run in headless tests without constructing `BatchHeatmapWindow`.
- Drill-in file opening, Bio-Formats access, and single-variant execution run off the EDT; only the popup window creation and mask display run on the EDT.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `BatchHeatmapModelTest` exercises header-name CSV parsing with shuffled optional score columns and asserts the matrix has the expected row/column labels and `NaN` missing cells.
3. `HeatmapRendererTest` exercises the new renderer headlessly and asserts image dimensions, missing-cell colour, and that two different numeric values map to different viridis colours.
4. A batch run of 20 files x 8 variants opens the heatmap automatically with no vertical scroll on a 1080p screen.
5. Toggling "Normalise per row" changes at least one rendered cell colour; toggling colour metric updates the scale bar label and colours within one click.
6. Clicking a cell opens the underlying image's mask within 2 seconds for a local 1024x1024 TIFF.
7. Heatmap window position and size are restored across two Fiji launches.
8. In `GraphicsEnvironment.isHeadless()`, batch completion does not try to create a `JFrame`; parsing/rendering tests still pass through the pure model/renderer.

## Manual smoke check

1. Run a small batch of 20 files x 8 variants and confirm the heatmap opens automatically after the CSV is written.
2. Toggle Count/F1/Agreement/Fragility where available and confirm unavailable metrics are hidden, not disabled clutter.
3. Toggle "Normalise per row" and confirm the colour scale and cell colours update.
4. Click a cell and confirm the matching source image/series/channel mask opens; then close it and click another cell.
5. Resize the heatmap window and move it to a second monitor; confirm labels and cells stay aligned.

## Known risks

- Very large batches (1000+ files) need scroll bars and a downsampling rule. Mitigation: render each cell at >= 2 px high; below that, group adjacent rows and show the grouping count in the row label.
- Very large rendered heatmaps can exceed the 64 MiB UI cap. Mitigation: estimate `8 * rows * columns + 4 * renderedPixels` before rendering and switch to grouped/downsampled rendering when the estimate exceeds the cap.
- Drill-in needs the original file path to still be valid. Mitigation: if the file was moved, show `file not found: <path>` and leave the heatmap open.
- ImageJ 1.x and ImageJ2 hybrid classloaders can make Bio-Formats drill-in unavailable even if batch CSV parsing works. Mitigation: route drill-in through `BioFormatsSeriesProvider`, catch classloader/linkage failures, and show a per-cell error instead of crashing.
- Virtual stacks can make drill-in preview slow or uncached. Mitigation: run drill-in off the EDT, open one source/series/channel at a time, and skip the mask preview with a useful message if the 256 MiB mask estimate is exceeded.
- Locale-dependent CSV parsing can misread comma decimals or system-localised numbers. Mitigation: parse the batch CSV with `Locale.ROOT`, accept dot-decimal numeric cells only, and treat unparseable metric cells as `NaN` with a logged warning.
- Multi-monitor and HiDPI displays can desynchronise heatmap cell pixels and axis labels. Mitigation: compute cell bounds from Swing component size in device-independent pixels and include a manual smoke check on a scaled display.
- Viridis palette must be the standard one so colours are comparable with other tools. Mitigation: vendor Bokeh's single-file `Viridis256` table, keep the source URL/credit in the Java file header, and assert first/middle/last RGB values in `HeatmapRendererTest`.
<!-- audit:agent2 corrected viridis source and license claim -->
