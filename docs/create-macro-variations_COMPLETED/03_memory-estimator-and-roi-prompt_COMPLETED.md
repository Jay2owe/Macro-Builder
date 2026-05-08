# Stage 03 — Memory estimator and ROI-mode prompt

## Why this stage exists

Microscopy hyperstacks routinely run 5–20 GiB. Running 9 variants of a pipeline on a 5 GiB hyperstack tries to allocate ~60 GiB of working memory and crashes Fiji. The Auto Threshold "Try all" plugin solves this by warning the user when stacks exceed 25 slices and offering to skip the montage render. We do the same thing better: estimate the memory cost, and if it exceeds 25% of the JVM heap, force ROI mode — the user draws a small rectangle, variants run on the crop only, and the full hyperstack run is deferred until after the user has picked a winner.

## Prerequisites

None.

## Read first

- `docs/create-macro-variations/00_overview.md`
- `src/main/java/macro/builder/image/FilterExecutor.java` lines 480–512 (`runDagThreadSafe` — the variant executor will call this; estimator must reason about its memory footprint)
- `src/main/java/macro/builder/image/FilterExecutor.java` — search for `cloneChannelStack` to see the per-line clone cost
- ImageJ `Roi` and `ImagePlus.crop()` API — relevant for cropping to ROI before variant execution
- The Auto Threshold "Try all" precedent: https://imagej.net/plugins/auto-threshold (it prompts when `nSlices > 25`)

## Scope

- New `MemoryEstimator` class with one method:
  - `Estimate estimate(ImagePlus source, int variantCount)` returns a `MemoryEstimate` value type.
- New `MemoryEstimate` value type: `long sourceBytes`, `long projectedBytes`, `long maxHeap`, `double headroomFraction` (= `projectedBytes / maxHeap`), `boolean exceedsBudget`, `String humanReadable`.
- The projection formula: `projectedBytes = sourceBytes * variantCount * 1.3`. The 1.3 multiplier is overhead for intermediate per-slice work in `runDagThreadSafe` (each line clones the source channel into a working stack).
- `exceedsBudget` is true when `headroomFraction > 0.25` (i.e. projected use exceeds 25% of `IJ.maxMemory()`).
- New `RoiPromptDialog` Swing utility: shows a non-modal toolbar telling the user to draw an ROI on the source image, with Confirm and Cancel buttons. Returns the drawn `Roi`, or `null` if the user cancels.
- New helper `ImagePlus cropToRoi(ImagePlus source, Roi roi)` that produces a thread-safe-clonable cropped `ImagePlus` preserving calibration, LUT, channel dimensions, Z, T. Use `ImagePlus.crop("stack")` or equivalent.
- The estimator's `humanReadable` returns a one-line summary like: `"9 variants × 1.2 GiB = 14.0 GiB (43% of 32.0 GiB heap) — ROI mode required"`.

## Out of scope

- The dialog that *invokes* the estimator — that's stage 05.
- Any decision logic about *what* to do when budget exceeded — stage 05 wires the estimator's verdict to either auto-engage the ROI prompt or proceed.
- Cropping for time-lapse (single representative timepoint) — stage 05 owns that policy decision; this stage exposes `cropToRoi` for spatial cropping only.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/image/variation/MemoryEstimator.java` | NEW | Pure-logic memory projection |
| `src/main/java/macro/builder/image/variation/MemoryEstimate.java` | NEW | Value type for estimator result |
| `src/main/java/macro/builder/ui/sandbox/variation/RoiPromptDialog.java` | NEW | Non-modal Swing prompt asking the user to draw an ROI |
| `src/main/java/macro/builder/image/variation/RoiCropper.java` | NEW | `cropToRoi(source, roi)` helper preserving calibration |
| `src/test/java/macro/builder/image/variation/MemoryEstimatorTest.java` | NEW | Estimator math + threshold |

## Implementation sketch

```java
// MemoryEstimator.java
public final class MemoryEstimator {
    private static final double OVERHEAD_FACTOR = 1.3;
    private static final double BUDGET_FRACTION = 0.25;

    public static MemoryEstimate estimate(ImagePlus source, int variantCount) {
        long sourceBytes = computeSourceBytes(source);
        long projected = (long) (sourceBytes * variantCount * OVERHEAD_FACTOR);
        long maxHeap = IJ.maxMemory();
        double headroom = (double) projected / maxHeap;
        boolean exceeds = headroom > BUDGET_FRACTION;
        return new MemoryEstimate(sourceBytes, projected, maxHeap, headroom, exceeds, format(...));
    }

    private static long computeSourceBytes(ImagePlus imp) {
        int w = imp.getWidth();
        int h = imp.getHeight();
        int slices = imp.getStackSize();   // C × Z × T total
        int bytesPerPixel = imp.getBitDepth() / 8;
        if (imp.getBitDepth() == 24) bytesPerPixel = 4; // RGB stored as 4 bytes
        return (long) w * h * slices * bytesPerPixel;
    }
}
```

```java
// RoiPromptDialog.java
public final class RoiPromptDialog {
    /**
     * Non-modal: shows a small floating panel with "Draw an ROI on <imageTitle>, then click Confirm".
     * Returns null if user cancels. Blocks the calling thread until Confirm or Cancel.
     */
    public static Roi prompt(ImagePlus source, String reasonMessage) {
        // Display reasonMessage prominently (e.g. "9 variants × 1.2 GiB = 14.0 GiB — ROI mode required").
        // Use a JDialog with Confirm / Cancel; on Confirm, read source.getRoi().
        // If source.getRoi() is null on Confirm, beep and ask again.
    }
}
```

```java
// RoiCropper.java
public final class RoiCropper {
    public static ImagePlus cropToRoi(ImagePlus source, Roi roi) {
        // 1. Save current source ROI (restore at end).
        // 2. source.setRoi(roi).
        // 3. ImagePlus cropped = source.crop("stack");  // covers all C, Z, T at the ROI bounds
        // 4. Copy calibration: cropped.setCalibration(source.getCalibration().copy()).
        // 5. Copy LUT / display range per channel.
        // 6. Restore original ROI on source.
        // 7. Return cropped.
    }
}
```

## Exit gate

1. `mvn test -Dtest=MemoryEstimatorTest` passes.
2. Test coverage:
   - 1024×1024×100-slice 16-bit image × 5 variants → projected ≈ `1024*1024*100*2*5*1.3 = 1.36 GiB`. Assert `projectedBytes` matches within 1%.
   - With `IJ.maxMemory()` mocked to 4 GiB, `exceedsBudget == true` for that case.
   - With `IJ.maxMemory()` mocked to 32 GiB, `exceedsBudget == false`.
   - RGB image (24-bit) bytes-per-pixel correctly counted as 4.
   - `humanReadable` includes variantCount, total GiB, percentage, and verdict text.
3. Manual smoke: invoke `RoiPromptDialog.prompt` from a small test main, draw an ROI on a Fiji-loaded image, confirm — returned Roi has the expected bounds. Cancel returns null.
4. Manual smoke: `RoiCropper.cropToRoi` on a 3-channel hyperstack → result has same channel count, same Z, calibration preserved, dimensions match the ROI bounds.
5. `mvn compile` produces no new warnings.

## Known risks

- `IJ.maxMemory()` reports the JVM `-Xmx` ceiling, not currently-available memory. Some users run other things in parallel and the actual headroom is less. The 25% budget is conservative on purpose — leaves room for the rest of Fiji's working set.
- Image bit depth detection for 24-bit RGB and 32-bit float must be correct or estimates are off by 2× or 4×. Test both.
- `ImagePlus.crop("stack")` doesn't preserve hyperstack layout (C/Z/T) on all ImageJ versions — verify with a 4-channel × 10 Z × 5 T input that the cropped result has the same hyperstack shape. If not, use `ij.plugin.Duplicator().run(source, c1, c2, z1, z2, t1, t2)` and intersect with ROI bounds manually.
- The non-modal RoiPromptDialog must not block the EDT — it shows, the user interacts with the canvas (which is on the EDT), then clicks Confirm. Use a `CountDownLatch` and a worker pattern, or make the prompt blocking with a separate dialog thread.
