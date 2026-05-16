# Histogram and count-vs-threshold charts

## Why this stage exists

The user has no way to tell at a glance whether the macro output is easy or hard to threshold. A small histogram with the tested threshold values marked on it answers "did the methods land in the valley?" instantly. A count-vs-threshold curve makes the plateau (from stage 02) visible to the eye rather than just to the algorithm.

## Prerequisites

- `02_auto-threshold-grid` complete (the curve needs a grid).
- Depends on stage 01 for `ShootoutRun` / `ShootoutContext` exposing the cached histogram and range to the UI.
- Depends on stage 02 for `ShootoutResult.recommended` and `recommendationReason`.

## Read first

- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:150-200` for the dialog layout
- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` for where the histogram is now cached on `ShootoutContext`
- Fiji's bundled `ij.gui.Plot` documentation: <https://imagej.net/ij/developer/api/ij/ij/gui/Plot.html>

## Scope

- Add a `ChartPanel` above the results table that holds two stacked charts: histogram (top, ~80px tall) and count curve (bottom, ~80px tall).
- After every successful run, render both charts from the cached `ShootoutContext` carried by `ShootoutRun` and the result list, then update the panel.
- Charts are rendered with the bundled `ij.gui.Plot`, exported as `BufferedImage`, and shown via `JLabel(new ImageIcon(...))`. No new dependency.
- Threshold values from successful variants are drawn as vertical lines on the histogram. The recommended one (if any) is drawn in a different colour and labelled.
- A simple legend underneath: "vertical lines = tested thresholds; gold line = recommended".
- If chart rendering fails or the environment cannot render `Plot` headlessly, hide the chart panel for that run and leave the table/export path working.

## Out of scope

- Interactive charts (no zoom, pan, click-to-pick — stage 10's live slider covers that need).
- 3D-aware plotting; histogram is computed across the whole stack as it already is.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Add a `ChartPanel` between the settings panel and the results table; hook into `onShootoutDone`. |
| `src/main/java/macro/builder/ui/ChartPanel.java` | NEW | Holds two `JLabel`s for histogram and curve; `update(ShootoutContext, List<ShootoutResult>)` method. |
| `src/main/java/macro/builder/ui/ChartRenderer.java` | NEW | Pure functions: `renderHistogram(...)` and `renderCurve(...)` returning `BufferedImage`. |
| `src/test/java/macro/builder/ui/ChartRendererTest.java` | NEW | Asserts the renderers return non-null images of the expected size; runs headless. |

## Implementation sketch

Histogram render:

```java
Plot plot = new Plot("", "Macro output value", "Pixel count");
double[] binCentres = binCentres(ctx);
double[] freq = toDouble(ctx.histogram);
plot.add("line", binCentres, freq);
for (ShootoutResult r : results) {
    if (r.thresholdValue == null || !r.isSuccess()) continue;
    plot.setColor(r.recommended ? RECOMMENDED : TESTED);
    plot.drawLine(r.thresholdValue, 0, r.thresholdValue, maxFreq);
}
return plot.getImagePlus().getBufferedImage();
```

Count curve render: same shape, x = threshold value, y = count, points joined with a thin line; vertical line at the recommended threshold.

EDT hand-off:

```java
SwingUtilities.invokeLater(() -> chartPanel.setImages(histo, curve));
```

The render itself happens on the worker thread to keep the EDT responsive on big stacks.

Threading model:

- `ThresholdShootoutDialog` calls the context-aware runner from the existing `SwingWorker`.
- `ChartRenderer.renderHistogram(...)` and `renderCurve(...)` run in the worker after the result rows are available. They must not create or show ImageJ windows.
- `ChartPanel.setImages(...)`, resize handling, and repaint happen on the EDT only.
- `ChartRendererTest` runs through the pure renderer path; it must not instantiate `ChartPanel` or any top-level Swing window in headless mode.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `ChartRendererTest` exercises both new renderer methods headlessly and asserts each image is non-null, 640x80 pixels by default, and contains at least two non-background colours.
3. After a successful run on a clearly bimodal image, the histogram shows two peaks and at least 80% of successful threshold vertical lines fall between the peak centres.
4. After a successful run with `AUTO_GRID`, the count curve has 10 plotted points and the recommended threshold marker is drawn at the same x-value as the starred result row.
5. Resizing the dialog from 800 px to 1400 px wide changes chart width while keeping each chart between 70 and 120 px tall.
6. Running on a headless test environment does not throw; renderers return images without constructing any top-level Swing window.

## Manual smoke check

1. Open Test Counts on a 16-bit stack, run with "Auto grid (recommended)", and confirm the histogram and count curve appear above the table.
2. Resize the Test Counts dialog narrow and wide; confirm chart labels remain readable and the plots do not overlap the table.
3. Select a different result row; confirm the table selection still works and the charts do not steal focus.
4. Run on a mostly blank image; confirm the chart panel hides or draws a flat chart without blocking CSV export.

## Known risks

- `Plot.getImagePlus()` allocates a backing `ImagePlus`. Mitigation: flush it in a `finally` block after grabbing the `BufferedImage`, and assert in `ChartRendererTest` that repeated renders do not retain extra `ImagePlus` windows.
- 16-bit images have 65536 possible values but only 256 bins in the cached histogram. Mitigation: label the x-axis `value (binned to 256)` and keep the chart visual-only, not a source for numeric decisions.
- On very narrow ranges, the curve may have all points at the same x. Mitigation: draw a flat line with padded x-axis bounds and add a renderer test with a zero-span range.
- Multi-monitor and HiDPI displays can make Swing chart pixel sizes wrong. Mitigation: size rendered images from the panel's device-independent bounds, test at 1x and 2x scaling where possible, and keep minimum chart dimensions fixed.
- Very large images can make chart generation slow if the renderer rebuilds histograms. Mitigation: render only from `ShootoutContext.histogram` and result rows; never rescan pixels in `ChartRenderer`.
