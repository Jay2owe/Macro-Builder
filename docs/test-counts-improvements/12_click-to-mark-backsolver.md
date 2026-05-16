# Click-to-mark back-solver

## Why this stage exists

Sometimes none of the swept variants look right and the user does not have a `RoiSet.zip` to use as ground truth. They can, however, recognise good objects when they see them. This stage lets the user click on 5–10 real objects in the source image and asks the system "which variant catches the ones I just clicked, while still producing a plausible count?" Effectively a one-minute ground truth.

## Prerequisites

- `02_auto-threshold-grid` complete (the back-solver searches over the grid).
- `05_ground-truth-scoring` complete for the reference-object matching concepts and `GroundTruthScorer` comparison tests.
- `09_macro-roundtrip-and-sidecar` complete for `ShootoutResult.Source` and `TestCountsManifest`; this stage extends the enum with `CLICK_FIT` and records click points in the sidecar.
- `10_live-threshold-slider` complete when executing strictly in numbered order, so `LiveMaskBuilder.build(...)` already exists. If this post-MVP stage is pulled ahead of stage 10, add `LiveMaskBuilder` here and let stage 10 extend it later.
- Depends on stage 01 for `ShootoutRun.context.processed`; disable the button if the current run has no retained processed image.

## Read first

- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` after stage 02 (`gridThresholds(...)` must be package-private, not private)
- `src/main/java/macro/builder/analysis/GroundTruthScorer.java` (NEW in stage 05; read it after stage 05/09 have landed) — the matching rule is similar
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` for the source-image hand-off pattern (currently passes `source` to the runner)

## Scope

- Add a "Click good objects..." button next to "Open mask preview".
- On click: focus the source image, set the active tool to multi-point, and listen for click events via `ImageCanvas`. Show a small floating helper window: "Click 5–10 real objects. Press Done when finished. Press Esc to cancel."
- When Done is pressed, capture the list of (x, y, z) click points and pass them to the back-solver.
- Back-solver:
  - Build a dense grid (e.g., 30 thresholds) across the macro-output range.
  - For each grid point, build the mask (reuse the typed mask builder from stage 01) and check: of the user's clicks, how many fall inside a foreground blob? That is the "catch rate".
  - Pick the threshold with the highest catch rate, subject to a "spread check": the variant's total count must be within ±25% of the median count across all grid points (so the system does not over-fit to a "catch everything" solution).
  - If ties, prefer the one whose count is closest to the median.
- Show the result inline as a new starred row in the table labelled "Click-fit". The reason text reads: "catches 8 of 8 clicked objects with a plausible count of 247."
- Pin the click points into the run so they are not lost: store them in `ShootoutSettings.clickPoints` and include them in the JSON sidecar (stage 09).
- In headless mode, skip all click-capture UI; `BackSolver` remains a pure class tested with synthetic click points.

## Out of scope

- Negative clicks ("this is not a real object") — single-class only for now.
- Auto-detection of "objects near each click" (we use the click point itself; refinement is a future polish stage).
- Brush-style annotation (point clicks only).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/ui/ClickCapture.java` | NEW | Attaches to the source image's `ImageCanvas`, captures clicks until Done or Esc; releases the canvas afterwards. |
| `src/main/java/macro/builder/analysis/BackSolver.java` | NEW | Pure: takes processed image, click points, settings; returns chosen threshold + reason + count. |
| `src/main/java/macro/builder/analysis/LiveMaskBuilder.java` | NEW | Provide a fresh-mask `build(...)` helper; current codebase has no `LiveMaskBuilder`. If stage 10 already added it, extend that file instead of creating a duplicate. |
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | MODIFY | Add `List<int[]> clickPoints` (each point: `[x, y, z]`). |
| `src/main/java/macro/builder/analysis/ShootoutResult.java` | MODIFY | Extend the result source/kind field from stage 09 with `CLICK_FIT` (or add it here if stage 09 did not). |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | "Click good objects..." button; integrate `ClickCapture`; on Done, dispatch a single click-fit run; append result row. |
| `src/main/java/macro/builder/analysis/TestCountsManifest.java` | NEW in 09; MODIFY here | Record click points in the sidecar JSON when present. |
| `src/test/java/macro/builder/analysis/BackSolverTest.java` | NEW | Synthetic image with known blobs; clicks at blob centres; assert chosen threshold catches all clicks with reasonable count. |
| `src/test/java/macro/builder/analysis/TestCountsManifestTest.java` | MODIFY | Assert click-fit sidecar JSON records click points and chosen threshold. |

## Implementation sketch

Click capture:

```java
public void start(ImagePlus source, Consumer<List<int[]>> onDone, Runnable onCancel) {
    ImageCanvas canvas = source.getCanvas();
    MouseAdapter listener = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e) {
            int x = canvas.offScreenX(e.getX());
            int y = canvas.offScreenY(e.getY());
            int z = source.getZ();
            points.add(new int[]{x, y, z});
            redrawOverlay();
        }
    };
    canvas.addMouseListener(listener);
    // Helper window with Done / Cancel buttons; on Done -> onDone.accept(points), detach listener.
}
```

Back-solver search:

```java
public BackSolverResult solve(ImagePlus processed, List<int[]> clicks, ShootoutSettings settings, int gridSteps) {
    List<Double> thresholds = ThresholdShootoutRunner.gridThresholds(ctx, gridSteps);
    int[] catchPerThreshold = new int[thresholds.size()];
    int[] countPerThreshold = new int[thresholds.size()];
    for (int i = 0; i < thresholds.size(); i++) {
        ImagePlus mask = LiveMaskBuilder.build(processed, thresholds.get(i), ctx.rangeMax);
        catchPerThreshold[i] = countCatches(mask, clicks);
        countPerThreshold[i] = ObjectCounter.count(mask, settings).count;
    }
    int medianCount = median(countPerThreshold);
    int best = -1, bestCatch = -1;
    for (int i = 0; i < thresholds.size(); i++) {
        if (catchPerThreshold[i] < bestCatch) continue;
        if (countPerThreshold[i] > medianCount * 1.25 || countPerThreshold[i] < medianCount * 0.75) continue;
        if (catchPerThreshold[i] > bestCatch || closerToMedian(...)) best = i;
    }
    return new BackSolverResult(thresholds.get(best), catchPerThreshold[best], countPerThreshold[best]);
}
```

`LiveMaskBuilder.build(...)` does not exist today, and `gridThresholds(...)` must be visible outside `ThresholdShootoutRunner` for this sketch to compile.
<!-- audit:agent1 corrected BackSolver dependencies on non-existing LiveMaskBuilder.build, private gridThresholds, and prior-stage GroundTruthScorer/TestCountsManifest files -->

Threading model:

- `ClickCapture` attaches/detaches `ImageCanvas` listeners on the EDT and must always remove listeners on Done, Esc, Cancel, or dialog close.
- Back-solving runs in a background worker using `ShootoutRun.context.processed`; it must not block the source image window while thresholds are tested.
- Mask building/counting inside `BackSolver` may reuse stage 01's per-slice worker helper, but no Swing objects are touched there.
- Appending the click-fit row, starring it, and updating button state happen on the EDT.
- True headless tests call `BackSolver.solve(...)` directly and never instantiate `ClickCapture`.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `BackSolverTest` exercises the pure back-solver on a synthetic image with 12 well-separated bright spots; 6 click points must be caught and the chosen count must be between 9 and 15.
3. Cancelling with Esc cleanly detaches from the source canvas and leaves the canvas with the same mouse-listener count it had before capture started.
4. The click-fit row appears in the table with the `CLICK_FIT` source badge in the Variant column and is the only newly starred row.
5. The sidecar JSON written after a click-fit run contains all click points and the chosen threshold under a `clickFit` block; this is covered by an updated `TestCountsManifestTest`.
6. In headless mode the click button is absent or disabled, while `BackSolverTest` still passes without constructing Swing components.

## Manual smoke check

1. Open Test Counts on a stack with retained processed context, click "Click good objects...", mark 5-10 objects, press Done, and confirm a starred click-fit row appears.
2. Start click capture and press Esc; confirm the helper window closes and later image clicks behave normally.
3. Repeat click capture on a 3D stack after changing slices; confirm stored points include the active z slice.
4. Write the sidecar JSON after a click-fit run and confirm the `clickFit` block includes click coordinates and threshold.
5. Close the Test Counts dialog during capture and confirm listeners are detached and no helper window remains.

## Known risks

- Multi-canvas Fiji sessions can attach to a different canvas than the one the user expects. Mitigation: document that clicks must be made on the original source window and show the source image title in the helper window.
- Stack clicks need the active z slice because different blobs live on different slices. Mitigation: store `[x, y, z]` for every click and add a stack-click case to `BackSolverTest`.
- A user with shaky hands might double-click and register a single point twice. Mitigation: debounce clicks within 200 ms and ignore points within a 3 px radius of the previous accepted point.
- Virtual stacks can make the 30-threshold search slow because every threshold may fetch slices from disk. Mitigation: run in a background worker, reuse the active processed context, and show progress/cancel if solving takes longer than 2 seconds.
- Very large images can make dense-grid mask building exceed memory. Mitigation: build and close one mask at a time, estimate mask size before solving, and refuse click-fit with a clear message if the 256 MiB cap would be exceeded.
- Float processors can contain `NaN` or infinite values that make threshold ordering unstable. Mitigation: use the finite range from `ShootoutContext`, treat non-finite pixels as background, and test this in `BackSolverTest`.
- Multi-monitor and HiDPI source windows can change screen-to-image coordinate conversion. Mitigation: always use `ImageCanvas.offScreenX/Y` and smoke-test capture on a scaled display.
- Over-fit guard: the +/-25% median count window is a heuristic. Mitigation: keep it as a named constant, tune against real data, and consider exposing it as an advanced setting only after validation.
- The spread check can reject every grid threshold if the count varies wildly across thresholds. Mitigation: fall back to the highest-catch threshold and warn in the reason with `count varies a lot; manual verification recommended`.
