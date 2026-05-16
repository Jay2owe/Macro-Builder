# Live threshold slider with pin button

## Why this stage exists

Even with auto grid and the plateau pick, the user sometimes wants to *feel* the parameter space — to drag a slider and watch the mask paint and depaint until they find the moment where it looks right. A live slider also makes it natural to add a custom threshold value to the run by clicking "Pin" at any position.

## Prerequisites

- `01_foundation-perf-refactor` complete (the typed-array mask builder makes this fast enough to be interactive).
- `09_macro-roundtrip-and-sidecar` complete, so pinned rows flow through the sidecar/result-source model. Use `ShootoutResult.Source.FIXED` plus a `Pinned <value>` variant label unless this stage deliberately adds a `PINNED` enum value.
- Depends on stage 01 for `ShootoutRun.context.processed`; the Scrub button is disabled until a successful run has cached range data and a retained post-macro image. The dialog closes that processed image when the run is replaced or the dialog closes.

## Read first

- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` after stage 01 for the typed mask builder
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` for the dialog layout and `activeMaskPreview` lifecycle

## Scope

- Add a "Scrub threshold" pane that opens from a new button in the dialog ("Scrub..." next to "Open mask preview"). The pane is a small undecorated `JDialog` owned by the main Test Counts dialog.
- Pane contents:
  - One horizontal slider spanning the cached macro-output range, with 1000 discrete ticks.
  - A second small slider for the active Z-slice if the source is a stack (default to the middle slice).
  - A live mask preview window the pane manages (opens on first drag, closes when the pane closes).
  - A live readout: current threshold value, current count, current coverage %.
  - Tickmarks on the threshold slider showing where each successful auto method landed.
  - A "Pin this value" button: adds the current slider value to the fixed-thresholds list and runs only that new variant (does not redo the whole sweep).
- The slider is debounced at 30 ms. Each tick rebuilds the mask using the typed-array path from stage 01 and updates the preview by replacing the ImageProcessor in the existing `ImagePlus` (no flicker).
- Counting on each tick uses the existing `ObjectCounter` on the active slice only when the source is a stack (full-stack counting on every slider tick would block).
- If the preview estimate exceeds the live-preview cap in `00_overview.md` "Memory budget", build a downsampled preview and make the readout say it is preview-only; Pin still runs the full single-variant path.

## Out of scope

- 3D-stack live count on every tick (only the active slice).
- A "compare two thresholds" mode (out of scope; future stage if requested).
- Painting a threshold curve below the slider (the histogram from stage 03 already shows the post-macro histogram; no need to duplicate).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/ui/ScrubPane.java` | NEW | Owns the slider pane, debounced repaint, pin behaviour. |
| `src/main/java/macro/builder/analysis/LiveMaskBuilder.java` | NEW | Mutates an existing `ByteProcessor` in place for a given threshold; reuses typed-array path from stage 01. |
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | Add NEW package-private `runOneVariant(...)` helper or equivalent so Pin does not re-run the whole sweep. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | "Scrub..." button; lifecycle (close scrub pane when main dialog closes); handle "Pin" event by adding to fixed thresholds and running a single-variant sweep. |
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | MODIFY | Helper `withAdditionalFixed(double v)` to keep the rest of settings unchanged. |
| `src/test/java/macro/builder/analysis/LiveMaskBuilderTest.java` | NEW | Asserts in-place rebuild matches a fresh rebuild byte-for-byte. |

## Implementation sketch

Debounce:

```java
private final javax.swing.Timer debounce = new javax.swing.Timer(30, e -> repaintNow());
{
    debounce.setRepeats(false);
    thresholdSlider.addChangeListener(ev -> debounce.restart());
}
```

In-place mask update (per active slice):

```java
public static void rebuildInPlace(ByteProcessor mask, ImageProcessor src, double lower, double upper) {
    byte[] out = (byte[]) mask.getPixels();
    if (src instanceof ShortProcessor) {
        short[] in = (short[]) src.getPixels();
        int lo = (int) Math.ceil(lower), hi = (int) Math.floor(upper);
        for (int i = 0; i < in.length; i++) {
            int v = in[i] & 0xffff;
            out[i] = (byte) ((v >= lo && v <= hi) ? 255 : 0);
        }
    } else { /* byte, float fallbacks */ }
}
```

Preview swap (no flicker):

```java
livePreview.setProcessor(maskTitle, mask);  // replaces pixels in the existing window
livePreview.updateAndDraw();
```

Pin behaviour:

```java
ShootoutSettings updated = settings.withAdditionalFixed(currentSliderValue);
runner.runOneVariant(processed, updated, currentSliderValue, progress, result -> {
    SwingUtilities.invokeLater(() -> tableModel.appendRow(result));
});
```

`runOneVariant(...)` does not exist today; add it in this stage as a package-private helper, or replace the sketch with a call to a new single-variant service.
<!-- audit:agent1 marked runOneVariant as new because ThresholdShootoutRunner currently only exposes run(...) -->

Threading model:

- Slider events and the 30 ms debounce timer fire on the EDT.
- The actual mask rebuild/count runs on one cancellable background worker or single-thread executor; stale jobs are dropped when a newer slider value arrives.
- The EDT only swaps the finished `ByteProcessor` into the existing preview, updates labels, and handles Pin.
- Pin uses the package-private single-variant runner off the EDT, then appends the row on the EDT.
- In headless mode the Scrub button is never shown; `LiveMaskBuilderTest` covers the pure mask builder.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `LiveMaskBuilderTest` exercises the new in-place rebuild path for byte, short, and float processors and asserts the rebuilt mask matches a fresh mask byte-for-byte.
3. Dragging the slider on a 2048x2048 8-bit image keeps the preview repainting at >= 30 frames per second for 5 seconds on a developer laptop.
4. Closing the scrub pane closes its live preview window within 1 second; closing the main Test Counts dialog also closes both.
5. Pinning a value appends exactly one new row to the existing table, does not re-run the full sweep, and marks the new row with a small `pinned` badge in the Variant column.
6. With a 3D stack open, the active-slice scrubber changes the preview slice and the threshold slider changes the mask threshold; each action refreshes the preview within 100 ms on a 1024x1024 stack.

## Manual smoke check

1. Run Test Counts on a 2048x2048 8-bit image, open "Scrub...", drag the threshold slider, and confirm the preview repaints continuously.
2. On a 3D stack, move the slice scrubber and confirm the preview changes slice without changing the table selection.
3. Click "Pin this value" and confirm one new row appears without the existing rows being recomputed.
4. Close the scrub pane and then the main dialog; confirm no live preview window remains open.
5. Repeat on a large image that triggers downsampling and confirm the readout says preview-only while Pin still runs the full variant.

## Known risks

- 16-bit short-range images can have 1000 slider ticks across a range such as 800-820, which is finer than one bin. Mitigation: quantise the slider value to one integer step when the bit depth allows it.
- Float-processor stacks have no obvious slider granularity and may contain `NaN` or infinite pixels. Mitigation: default to 1000 ticks across the finite range and treat non-finite pixels as background in `LiveMaskBuilderTest`.
- Virtual stacks can be too slow for live per-slice fetches. Mitigation: scrub only the active slice, cache only that slice's processed pixels while the pane is open, and show a "preview unavailable for virtual stack" message if 5 consecutive rebuilds exceed 100 ms.
- Very large images can exceed live-preview memory. Mitigation: downsample to stay under the 64 MiB preview cap, never build a full-stack live preview, and keep Pin using the full off-EDT single-variant path.
- Multi-monitor and HiDPI displays can make the floating scrub pane or preview open at the wrong size. Mitigation: position the pane relative to the owning dialog's graphics configuration and smoke-test moving it between monitors.
- EDT vs. worker mistakes can freeze the UI. Mitigation: only the final processor swap runs on the EDT; rebuild/count work runs in the scrub worker, and stale jobs are cancelled before updating labels.
- `JSlider.setValue` from code can fire `ChangeListener` recursion. Mitigation: use `slider.getModel().setValue` inside an `adjusting` flag and cover snap-to-tick behaviour in a unit test.
