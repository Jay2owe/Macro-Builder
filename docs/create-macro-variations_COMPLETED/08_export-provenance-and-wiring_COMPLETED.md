# Stage 08 — Export, provenance, and SandboxDialog wiring

## Why this stage exists

The previous seven stages built the moving parts in isolation. This stage connects them into a feature the user can actually invoke from the Sandbox dialog, and adds the three things that turn a tuned variant into something a microscopist can use tomorrow: a labelled montage PNG for the lab notebook, an `.ijm` snippet of the chosen variant for the methods section, and a session provenance log of every variant ever generated so the user can answer "what did we test?" without relying on memory.

## Why this stage is last

It depends on working `VariationChooserDialog` (stage 05) and a working `VariantGridFrame` with action listeners (stages 06–07). All earlier stages are pure infrastructure — none are reachable from the UI until this one wires them up.

## Prerequisites

- Stages 05 (chooser dialog) and 07 (tile actions) completed.

## Read first

- `docs/create-macro-variations/00_overview.md`
- `docs/create-macro-variations/05_variation-chooser-dialog.md`
- `docs/create-macro-variations/07_tile-actions-and-compare-mode.md`
- `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java` end-to-end. Find the existing button cluster (search `JButton` near lines 76–82). Variations button goes adjacent.
- `src/main/java/macro/builder/ui/sandbox/SandboxModel.java` — exposes the current `DagIR` and the source `ImagePlus`.
- `src/main/java/macro/builder/image/dag/DagToIjmEmitter.java` end-to-end — the `.ijm` export.
- `ij.plugin.MontageMaker` — `makeMontage2(imp, cols, rows, scale, first, last, inc, border, labels)` returns an `ImagePlus`.
- `ij.io.FileSaver` for the PNG write, or `ij.IJ.saveAs(imp, "PNG", path)`.
- `ij.plugin.frame.Recorder` for macro recordability.

## Scope

### A. SandboxDialog button

- Add a new `JButton variations = new JButton("Create Variations")` adjacent to the existing button cluster (~lines 76–82). Match style of neighbours.
- Action: open `VariationChooserDialog(parentFrame, sandboxModel, this::onVariationResults)`. The dialog handles everything from there.
- `onVariationResults(List<VariantResult>)`: build a `VariantGridFrame`, attach a `TileActionListener`, show the frame.

### B. TileActionListener wiring

- The listener attached to `VariantGridFrame` (and propagated to `CompareFrame`) needs three concrete behaviours:

#### B1. `onPromote(VariantPlan plan)`

- Mutate `SandboxModel`'s current DAG to `plan.dag`. Reuse whatever `SandboxModel` setter the existing UI uses (e.g. `setDag(DagIR)` or equivalent — locate via grep `setDag\|currentDag\|dagChanged`).
- After mutation, force the `DagCanvasPanel` to repaint so the user sees the new pipeline.
- Emit recordable `.ijm` lines describing the promoted DAG via `DagToIjmEmitter`. Wrap with: `Recorder.recordString("// macro-builder variation: promoted " + plan.label + "\n");` followed by the emitted macro lines.
- Append a "Promoted: <label> at <timestamp>" entry to the session provenance log.
- Show a non-modal confirmation toast (use a `JDialog` that auto-closes after 2s, or just a small status banner in `SandboxDialog`'s status bar).

#### B2. `onSavePreset(VariantPlan plan)`

- Open a small `JDialog` asking for a preset name, defaulted to `<sourceTitle>_<plan.label>_<HHmm>` (e.g. `nuclei_sigma=2.0_1432`).
- On confirm, write the preset to the existing preset folder. **Locate the preset directory and serialiser** — search for `Preset` / `presets` in the codebase; reuse `DagIRSerializer` (already in `src/main/java/macro/builder/image/dag/DagIRSerializer.java`) for the on-disk format. Do not invent a new format.
- Append "Saved preset: <name>" to provenance log; emit `Recorder.recordString` line.

#### B3. Export buttons (added to `VariantGridFrame`'s north toolbar by *this* stage)

- **Save labelled montage (PNG)**: produce a flat montage of all currently-visible tiles using `MontageMaker.makeMontage2`. Steps:
  1. Build a synthetic `ImageStack` where each slice is the *current* slice of one tile (so the exported montage shows the slice the user is currently viewing). Apply `CaptionBaker` first so captions are baked.
  2. `MontageMaker.makeMontage2(synth, cols, rows, 1.0, 1, n, 1, 4, true)`.
  3. `IJ.saveAs(montage, "PNG", chosenPath)` via a `JFileChooser` defaulting to `<sourceTitle>_variations.png` next to the source file.
- **Copy .ijm to clipboard**: emit lines for *every* visible variant via `DagToIjmEmitter.emit(plan.dag)`, prefixed with comments labelling each variant. Concatenate into one string. Push to system clipboard via `Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null)`.
- **Save preset**: same as B2 but applied to the *currently focused* tile (clicking a tile gives it focus). Already covered by per-tile button — this toolbar button is a convenience for the focused tile.

### C. Provenance log

- New `VariationSessionLog` singleton (or per-Sandbox-session). In-memory `List<LogEntry>`.
- Each entry: `Instant timestamp`, `String action` (one of GENERATE / ELIMINATE / PROMOTE / SAVE_PRESET), `String summary`, `Map<String, String> detail`.
- A "Show variation log" button in `SandboxDialog` (next to Variations) opens a non-modal `JDialog` showing the log as a `JTable`. Each row clickable: clicking a GENERATE row should restore that variant set if its source is still in memory (Tier 2 if expensive — for v1, just display).
- Log persists for the lifetime of the Sandbox session (cleared when SandboxDialog closes, or when the user clicks "Clear log").

### D. Macro recordability — final pass

- Audit every action emitted by stages 05–07 to confirm it produces at least a comment line in the recorder. The recorder must show a coherent transcript of: open chooser → generate → eliminate → eliminate → promote.
- Promote *also* emits actual macro code via `DagToIjmEmitter` so re-running the macro reproduces the chosen pipeline.

## Out of scope

- Restoring a past variant set from the log entry — Tier 2.
- Per-channel preset save (whole-DAG only) — Tier 2.
- PDF export — Tier 2 (PNG covers the lab-notebook case).
- OMERO upload — out of scope entirely.
- Cloud sync — out of scope entirely.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/ui/sandbox/SandboxDialog.java` | MODIFY | Add Create Variations button + onVariationResults handler + Show variation log button |
| `src/main/java/macro/builder/ui/sandbox/variation/VariationActionsBinder.java` | NEW | Implements `TileActionListener`; mutates SandboxModel, writes presets, emits Recorder lines |
| `src/main/java/macro/builder/ui/sandbox/variation/MontageExporter.java` | NEW | `MontageMaker`-based export of current grid state to PNG |
| `src/main/java/macro/builder/ui/sandbox/variation/IjmClipboardExporter.java` | NEW | Concatenate `DagToIjmEmitter` outputs for visible variants → clipboard |
| `src/main/java/macro/builder/ui/sandbox/variation/VariationSessionLog.java` | NEW | In-memory provenance log + viewer dialog |
| `src/main/java/macro/builder/ui/sandbox/variation/VariantGridFrame.java` | MODIFY | Wire export buttons into the north toolbar |
| `src/main/java/macro/builder/ui/sandbox/SandboxModel.java` | MODIFY (maybe) | Confirm or add a public DAG-replacement method (`setDag` or equivalent) for Promote |
| `src/test/java/macro/builder/ui/sandbox/variation/VariationActionsBinderTest.java` | NEW | Promote mutates the model; recorder lines emitted |
| `src/test/java/macro/builder/ui/sandbox/variation/MontageExporterTest.java` | NEW | Montage shape + caption pixels present |
| `src/test/java/macro/builder/ui/sandbox/variation/IjmClipboardExporterTest.java` | NEW | Output contains a comment header per variant + valid `.ijm` body |

## Implementation sketch

```java
// SandboxDialog.java — add to button cluster
private final JButton variations = new JButton("Create Variations");
// in initLayout:
buttonRow.add(variations);
variations.addActionListener(e -> openVariationsDialog());

private void openVariationsDialog() {
    VariationChooserDialog dialog = new VariationChooserDialog(
        SwingUtilities.getWindowAncestor(this), model, this::onVariationResults);
    dialog.setVisible(true);
}

private void onVariationResults(List<VariantResult> results) {
    VariationActionsBinder binder = new VariationActionsBinder(model, sessionLog, this);
    VariantGridFrame frame = new VariantGridFrame(
        "Variations: " + model.source.getTitle(), model.source, results);
    frame.setActionListener(binder);
    frame.attachExporters(new MontageExporter(frame), new IjmClipboardExporter(frame));
    frame.setVisible(true);
    sessionLog.record(LogEntry.generate(results));
}
```

```java
// VariationActionsBinder.java
public final class VariationActionsBinder implements TileActionListener {
    public void onPromote(VariantPlan plan) {
        DagIR oldDag = model.getDag();
        model.setDag(plan.dag);   // assumes setDag exists; if not, locate existing API
        sessionLog.record(LogEntry.promote(plan, oldDag));

        if (Recorder.record) {
            Recorder.recordString("// macro-builder variation: promoted " + plan.label + "\n");
            String macro = DagToIjmEmitter.emit(plan.dag);
            Recorder.recordString(macro);
        }
        showToast("Promoted: " + plan.label);
        sandboxDialog.repaintDagCanvas();
    }

    public void onSavePreset(VariantPlan plan) {
        String defaultName = model.source.getTitle().replaceAll("[^a-zA-Z0-9]+", "_")
            + "_" + plan.label.replaceAll("[^a-zA-Z0-9.=]+", "_")
            + "_" + new SimpleDateFormat("HHmm").format(new Date());
        String name = JOptionPane.showInputDialog(sandboxDialog, "Preset name:", defaultName);
        if (name == null || name.isEmpty()) return;
        File presetFile = new File(presetDir(), name + ".dag.json");
        DagIRSerializer.writeTo(plan.dag, presetFile);
        sessionLog.record(LogEntry.savePreset(plan, name));
        if (Recorder.record) Recorder.recordString("// macro-builder variation: saved preset " + name + "\n");
        showToast("Saved preset: " + name);
    }
}
```

```java
// MontageExporter.java
public final class MontageExporter {
    public void exportTo(File path) {
        List<TilePanel> tiles = grid.visibleTilesInDisplayOrder();
        ImageStack synth = new ImageStack(tiles.get(0).slice().getWidth(), tiles.get(0).slice().getHeight());
        for (TilePanel t : tiles) {
            synth.addSlice(t.label(), t.currentSliceProcessor());   // already caption-baked
        }
        ImagePlus stackImp = new ImagePlus("variations", synth);
        int n = synth.getSize();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil(n / (double) cols);
        ImagePlus montage = new MontageMaker().makeMontage2(stackImp, cols, rows, 1.0, 1, n, 1, 4, true);
        IJ.saveAs(montage, "PNG", path.getAbsolutePath());
    }
}
```

## Exit gate

1. `mvn test -Dtest=VariationActionsBinderTest,MontageExporterTest,IjmClipboardExporterTest` passes.
2. Test coverage:
   - `VariationActionsBinder`: `onPromote(plan)` calls `model.setDag(plan.dag)`; with Recorder mocked-on, captures both the comment line and the macro body. `onSavePreset` writes a file at the expected path.
   - `MontageExporter`: building from 4 tiles produces a 2×2 montage with each tile's caption visible at expected coordinates (use a known input where caption pixels are predictable).
   - `IjmClipboardExporter`: output is parseable: `DagToIjmEmitter` round-trip works on each segment between `// VARIANT: ` markers.
3. Manual smoke (full integration, Fiji running):
   - Open SandboxDialog with a non-trivial DAG and a real Fiji image.
   - Click Create Variations → chooser opens.
   - Sweep Gaussian sigma 0.5–4, 4 steps → Generate → progress bar → grid window opens with raw + 4 tiles.
   - Click X on two tiles → grid reflows. Click X again → CompareFrame opens.
   - Press Space → flicker. Press Space → stop.
   - Click Promote on the surviving variant → SandboxDialog's DAG canvas updates to show the promoted pipeline. With Recorder open, see the recorded `.ijm`.
   - Re-open Variations dialog → sweep again → Generate.
   - Click Save labelled montage → file dialog → save → open the PNG → captions visible, all variants present.
   - Click Copy .ijm → paste into a text editor → contains commented header per variant + valid macro code.
   - Open Show variation log → table shows: Generate (5 variants), Eliminate × 2, Promote, Generate (5 variants).
4. Macro recordability end-to-end: `Plugins > Macros > Record` open from before clicking Variations → record a full Generate→Eliminate→Promote sequence → save the recording → run it on the same source image → produces the same final DAG state. (Recording reproduces the *promoted DAG*; it does not re-run variant generation, since generation is a UI-driven exploration step.)
5. `mvn compile` produces no new warnings.
6. **End-to-end with the workflows from the brainstorm**: replicate Scenario 1 (Gaussian sigma sweep) from the brainstorm — reproduce the "After" workflow timing and outcomes. Specifically: total clicks ≤ 8 (Variations button → pick node → set range → Generate → eliminate × N → Promote → Save montage → Save), total time ≤ 60s on a 1 GiB hyperstack.

## Known risks

- `SandboxModel`'s DAG-mutation API may not exist as a clean `setDag(DagIR)` — `Read first` calls this out as a "maybe MODIFY". If the existing model uses a more granular API (per-line edits) or fires events on every change, Promote needs to call the right path so observers (DagCanvasPanel etc.) repaint. Locate the existing path before adding a new one.
- `MontageMaker.makeMontage2` doesn't preserve color LUTs — for multichannel composite tiles, the montage will be grayscale unless we flatten each tile to RGB first via `imp.flatten()`. Test with a multichannel input; if needed, flatten before adding to synth stack.
- Preset directory location: must reuse existing convention. Search for "preset" in the codebase to find it. Don't pick a new directory.
- The "show variation log" button opens a non-modal dialog. If the user closes SandboxDialog with the log dialog still open, the log dialog should also close (parent it correctly).
- Recorder calls: only emit when `Recorder.record` is true, otherwise Recorder accumulates strings the user never sees. `Recorder.recordString` already checks internally — verify in the local IJ version, but as defensive practice wrap explicit checks anyway.
- The clipboard exporter must include `setBatchMode(true)/(false)` envelope around the emitted macro chunks if `DagToIjmEmitter` doesn't already, otherwise pasting and running the macro will flicker windows.
