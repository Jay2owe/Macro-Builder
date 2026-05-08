# Stage 05 — Variation chooser dialog

## Why this stage exists

Before any variants run, the user has to tell the plugin: **what to vary**. This stage builds the modal Swing dialog that asks. The user picks a mode (sweep parameter / swap filter), picks which DAG node, picks the alternatives or the numeric range, and sees a live memory estimate. On Generate, the dialog assembles `VariantAxis` lists, hands them to `VariantSampler.ofat` (or `cartesian` if Advanced is on), and invokes `VariantExecutor.runAll` inside a `SwingWorker` with a progress bar. When execution finishes, it hands the result list to stage 06's grid window.

## Prerequisites

- Stage 01 (`VariantPlan`, `VariantSampler`, `VariantAxis`)
- Stage 02 (`OpTypeParamRegistry`, `ParamSpec`)
- Stage 03 (`MemoryEstimator`, `RoiPromptDialog`, `RoiCropper`)
- Stage 04 (`VariantExecutor`, `ProgressCallback`)

## Read first

- `docs/create-macro-variations/00_overview.md`
- All four predecessor stage files (for the API shapes the dialog drives).
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java` (read it end-to-end — you'll be invoked from here, and the dialog should match its visual style)
- `src/main/java/macro/builder/ui/sandbox/StepEditorDialog.java` (existing modal Swing dialog in this codebase — copy its conventions for OK/Cancel button placement, JDialog construction, modal behaviour)
- `src/main/java/macro/builder/ui/sandbox/SandboxModel.java` (the variations dialog will be passed the current DAG via this model)

## Scope

- New `VariationChooserDialog extends JDialog`.
- Constructor takes: parent `Frame`, `SandboxModel` (current DAG + source `ImagePlus`), and a `Consumer<List<VariantResult>>` callback to fire when generation completes.
- Layout (top to bottom):
  - **Mode radio group**: ⦿ Sweep parameter ⦾ Swap filter
  - **Node picker**: a `JComboBox<DagNode>` populated from `model.dag.lines.flatMap(l -> l.ops)`. Show as `"<lineName>: <opType.name()> [id=<id>]"`. Filter to nodes that have non-empty `OpTypeParamRegistry.paramsOf(node.type)` when in sweep mode; show all nodes in swap mode.
  - **Mode-specific panel** (CardLayout, swapped on radio change):
    - **Sweep panel**: shows a `JComboBox<ParamSpec>` of the picked node's params, then four spinners: Min, Max, Steps (default 5, max 9), and a "log/linear" indicator (read-only, derived from `ParamSpec.scale`). Live preview row showing the actual numeric values that will be swept.
    - **Swap panel**: shows a `JList<OpType>` of compatible alternatives (heuristic: same broad category — filters that act on intensity → other filter ops; 3D filters → other 3D filters; categorical thresholds → other thresholds). User checks 1–8 alternatives. Each checked alternative gets `OpTypeParamRegistry.argsForDefaults` as its starting args.
  - **Advanced toggle**: collapsed by default. When expanded, shows: ☐ Use cartesian product (multi-axis), int spinner for max variants (default 9, max 16). v1 only allows multi-axis when Advanced is open and explicitly ticked.
  - **Memory estimate row**: live label showing `humanReadable` from `MemoryEstimator.estimate`. Updates whenever variant count changes. Red text + warning icon when `exceedsBudget`.
  - **Buttons**: `Generate`, `Cancel`. Generate is disabled until at least one alternative is configured.
- On Generate:
  1. Build `List<VariantAxis>` from the dialog state.
  2. Run `VariantSampler.ofat(...)` (or `.cartesian(...)` if Advanced+ticked) with `maxVariants=9` (or user-chosen up to 16).
  3. Re-check memory estimate against the *actual* plan count. If `exceedsBudget`, invoke `RoiPromptDialog.prompt` — user draws an ROI, Confirm. Source for execution becomes `RoiCropper.cropToRoi(model.source, roi)`.
  4. Time-lapse policy (v1): if source is hyperstack with `T > 1`, automatically constrain to the *currently displayed* timepoint by calling `Duplicator().run(cropped, 1, C, 1, Z, t, t)`. This matches the user's "I'm parked on T=60, work on that" expectation.
  5. Open a non-modal progress `JDialog` with a determinate `JProgressBar`. Disable Generate.
  6. `SwingWorker<List<VariantResult>, Integer>`:
     - `doInBackground`: call `VariantExecutor.runAll(executionSource, plans, this::publish)`.
     - `process(chunks)`: update progress bar from latest chunk.
     - `done`: close progress dialog, dispose this dialog, invoke the result callback on EDT.

## Out of scope

- The grid window itself — stage 06.
- Tile actions (Promote etc.) — stage 07.
- Export / provenance — stage 08.
- The button on `SandboxDialog` that opens this dialog — stage 08.
- Multi-node simultaneous sweeps — Tier 2.
- Live preview on parameter spinner change — Tier 3.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/ui/sandbox/variation/VariationChooserDialog.java` | NEW | The modal dialog itself |
| `src/main/java/macro/builder/ui/sandbox/variation/SweepPanel.java` | NEW | Sub-panel for sweep mode |
| `src/main/java/macro/builder/ui/sandbox/variation/SwapPanel.java` | NEW | Sub-panel for swap mode |
| `src/main/java/macro/builder/ui/sandbox/variation/ProgressDialog.java` | NEW | Non-modal progress while executor runs |
| `src/main/java/macro/builder/image/variation/FilterCompatibility.java` | NEW | Heuristic — which OpTypes are reasonable substitutes for which |
| `src/test/java/macro/builder/ui/sandbox/variation/VariationChooserDialogTest.java` | NEW | Headless test of axis-building from dialog state |

## Implementation sketch

```java
// FilterCompatibility.java — small heuristic, hardcoded
public final class FilterCompatibility {
    public static List<OpType> alternativesFor(OpType current) {
        switch (current) {
            case GAUSSIAN_BLUR:
            case MEDIAN:
            case MEAN:
                return Arrays.asList(OpType.GAUSSIAN_BLUR, OpType.MEDIAN, OpType.MEAN, OpType.UNSHARP_MASK);
            case GAUSSIAN_BLUR_3D:
            case MEDIAN_3D:
            case MINIMUM_3D:
                return Arrays.asList(OpType.GAUSSIAN_BLUR_3D, OpType.MEDIAN_3D, OpType.MINIMUM_3D);
            case SUBTRACT_BACKGROUND:
                return Arrays.asList(OpType.SUBTRACT_BACKGROUND);  // no near-equivalents
            // ... other categories
            default:
                return Collections.emptyList();
        }
    }
}
```

```java
// SweepPanel — given a node, build a VariantAxis with the swept values
public VariantAxis buildAxis() {
    ParamSpec spec = (ParamSpec) paramCombo.getSelectedItem();
    double min = (Double) minSpinner.getValue();
    double max = (Double) maxSpinner.getValue();
    int steps = (Integer) stepsSpinner.getValue();
    List<Double> values = (spec.scale == Scale.LOG)
        ? geometricSpacing(min, max, steps)
        : arithmeticSpacing(min, max, steps);
    List<AlternativeValue> alts = new ArrayList<>();
    for (double v : values) {
        Map<String, Double> existing = OpTypeParamRegistry.parseArgs(node.type, node.args);
        existing.put(spec.argKey, v);
        String args = OpTypeParamRegistry.renderArgs(node.type, existing);
        String label = String.format("%s=%.2f", spec.argKey, v);
        alts.add(new AlternativeValue(label, null, args));   // null type = no substitution
    }
    return new VariantAxis(node.id, Kind.PARAM_SWEEP, alts);
}
```

```java
// SwapPanel — given a node and ticked alternatives, build a VariantAxis
public VariantAxis buildAxis() {
    List<AlternativeValue> alts = new ArrayList<>();
    for (OpType alt : tickedAlternatives) {
        if (alt == node.type) continue;  // skip baseline
        String args = OpTypeParamRegistry.argsForDefaults(alt);
        alts.add(new AlternativeValue(alt.name(), alt, args));
    }
    return new VariantAxis(node.id, Kind.FILTER_SWAP, alts);
}
```

```java
// VariationChooserDialog generation flow
private void onGenerate() {
    List<VariantAxis> axes = currentPanel().buildAxes();
    int cap = advancedPanel.maxVariantsValue();   // 9 default, ≤16
    List<VariantPlan> plans = (advancedPanel.cartesianTicked())
        ? VariantSampler.cartesian(model.dag, axes, cap)
        : VariantSampler.ofat(model.dag, axes, cap);

    MemoryEstimate estimate = MemoryEstimator.estimate(model.source, plans.size());
    ImagePlus executionSource = model.source;
    if (estimate.exceedsBudget) {
        Roi roi = RoiPromptDialog.prompt(model.source, estimate.humanReadable);
        if (roi == null) return;  // user cancelled
        executionSource = RoiCropper.cropToRoi(model.source, roi);
    }
    if (executionSource.getNFrames() > 1) {
        executionSource = singleTimepoint(executionSource, model.source.getT());
    }

    ProgressDialog progress = new ProgressDialog(this, plans.size());
    SwingWorker<List<VariantResult>, Integer> worker = new SwingWorker<List<VariantResult>, Integer>() {
        protected List<VariantResult> doInBackground() {
            return VariantExecutor.runAll(finalSource, plans, new ProgressCallback() {
                public void onStart(int total) { /* publish 0 */ }
                public void onVariantComplete(int done, int total, VariantResult r) { publish(done); }
                public void onAllDone(List<VariantResult> results) {}
            });
        }
        protected void process(List<Integer> chunks) {
            progress.setProgress(chunks.get(chunks.size() - 1));
        }
        protected void done() {
            progress.dispose();
            try { resultCallback.accept(get()); }
            catch (Exception e) { IJ.handleException(e); }
            VariationChooserDialog.this.dispose();
        }
    };
    progress.setVisible(true);
    worker.execute();
}
```

## Exit gate

1. `mvn test -Dtest=VariationChooserDialogTest` passes (headless tests of `buildAxis`).
2. Test coverage:
   - Sweep panel with sigma 0.5–4.0, 4 steps, log scale → returns 4 alternatives at sigma values [0.5, ~1.0, ~2.0, 4.0] (geometric spacing).
   - Sweep panel with linear scale → arithmetic spacing.
   - Swap panel with Gaussian + Median + Mean ticked, baseline Gaussian → returns 2 alternatives (Median, Mean), each with default args from registry.
   - Cartesian axis builder rejects when Advanced is not ticked.
   - `FilterCompatibility.alternativesFor(GAUSSIAN_BLUR_3D)` returns only 3D filters (no 2D pollution).
3. Manual smoke (Fiji running):
   - Open Sandbox, build a 1-line DAG with `Subtract Background → Gaussian Blur → Otsu Threshold`.
   - Open `VariationChooserDialog` directly from a test main (no SandboxDialog button yet — that's stage 08).
   - Pick Sweep mode, pick the Gaussian node, pick Sigma, range 0.5–5, steps 5.
   - Memory estimate updates from "5 variants × ..." to "1 variant × ..." as steps spinner changes.
   - Click Generate → progress bar advances 1/5..5/5 → result callback receives 5 `VariantResult` objects with non-null `output`.
4. Manual: with a 5 GiB hyperstack source, the memory estimate label turns red and the ROI prompt fires on Generate.
5. `mvn compile` produces no new warnings.

## Known risks

- Spinner value updates can fire too aggressively, causing many memory-estimate recomputations. Use a 200ms debounce or wire to `stateChanged` only on commit (focus loss / Enter).
- The `Consumer<List<VariantResult>>` callback runs on the EDT (called from `done()`). Stage 06 will create a Swing window — that's correct.
- `ParamSpec.min`/`max` from the registry are *suggestions*, not enforced limits. The spinner should default to those but allow the user to type wider values. If `min < spec.min`, warn but allow.
- Time-lapse single-frame extraction may surprise users who expected variants on every frame. Add a tooltip on the dialog explaining the policy ("variants run on the current timepoint only; promote a winner then run on the full T axis"). v1 doesn't support sweeping T.
- If the user picks Sweep mode but the chosen node has no params (`OpTypeParamRegistry.paramsOf` empty), the Sweep panel is empty and Generate stays disabled. Either filter the node combobox to sweep-capable nodes (current scope), or show a "no parameters available — try Swap mode" message. Current scope is filtering — implement that.
