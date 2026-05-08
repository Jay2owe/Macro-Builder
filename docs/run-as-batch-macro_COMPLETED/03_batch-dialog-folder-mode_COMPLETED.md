# Batch dialog folder mode

## Why this stage exists

Users need a visible workflow behind the `Run as batch...` button. Folder regex mode can ship before container mode because the scanner and runner are already available after stages 01 and 02, and it provides useful batch macro execution for ordinary image folders.

## Prerequisites

- `01_batch-model-and-folder-scan.md` completed and renamed with `_COMPLETED`.
- `02_macro-batch-runner.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/run-as-batch-macro/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:250-283` for the right-side `Loaded Macro` action column.
- `src/main/java/macro/builder/Macro_Builder.java:654-670` for the current `Run as batch...` placeholder and macro-loaded guard.
- `src/main/java/macro/builder/Macro_Builder.java:845-890` for batch compatibility warning UI used by `Save as batch macro...`.
- `src/main/java/macro/builder/analysis/MacroBatchCompatibility.java:1-100` for unsafe macro warning detection.
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:533-603` for existing batch SwingWorker structure.
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:605-678` for file chooser, CSV chooser, cancel, and completion status patterns.
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:780-803` for busy-state button enablement.
- `src/main/java/macro/builder/ui/PipelineDialog.java:21-130` for the local dialog helper, if reusing it is practical.
- `src/main/java/macro/builder/ui/PipelineDialog.java:325-410` and `src/main/java/macro/builder/ui/PipelineDialog.java:503-636` for dialog field, component, footer, and show behavior.
- `../CPC/src/main/java/cpc/CPCBatch.java:50-102` for CPC-style regex preview interaction.

## Scope

- Replace `runAsBatchPlaceholder()` with a real action.
- Add a `BatchMacroDialog` under `src/main/java/macro/builder/ui/`.
- Implement folder regex mode with:
  - folder chooser,
  - filename regex field,
  - recursive toggle,
  - preview button,
  - tickable table of matching files,
  - output folder chooser,
  - run button,
  - cancel-after-current-file button,
  - status/progress display.
- Warn about unsafe batch macro patterns before starting a run.
- Run `BatchMacroRunner` on a background `SwingWorker`.
- Write TIFF outputs and `Macro_Builder_Batch_Run.csv` to the selected output folder.
- Keep container mode out of the UI or show it as disabled until stage 04.

## Out of scope

- Bio-Formats container series selection. Stage 04 owns that.
- Count settings or threshold result tables.
- Saving reusable wrapper macros. Existing `Save as batch macro...` owns count wrapper export.
- Reworking the main launcher layout beyond replacing the button action.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Wire `Run as batch...` to the new dialog instead of the placeholder. |
| `src/main/java/macro/builder/ui/BatchMacroDialog.java` | NEW | Folder regex batch-run UI. |
| `src/main/java/macro/builder/analysis/BatchMacroRunner.java` | MODIFY | Add any small progress or CSV helpers needed by the dialog. |
| `src/test/java/macro/builder/ui/BatchMacroDialogTest.java` | NEW | Add model-level tests if the dialog exposes a table model or validation helpers. |

## Implementation sketch

Main button change:

```java
private void runAsBatch() {
    if (!ensureMacroLoaded()) return;
    List<String> warnings = MacroBatchCompatibility.warnings(lastMacro);
    if (!warnings.isEmpty() && !confirmBatchWarnings(warnings, "Run the batch anyway?")) {
        return;
    }
    BatchMacroDialog.show(dialog, lastMacro);
}
```

If `confirmBatchWarnings(...)` is currently tied to "Save the batch macro anyway?", either add a message parameter or add a second confirmation helper. Keep wording plain:

```text
This macro may not be safe for batch use:

- waitForUser pauses for manual input and can stall a batch run.

Run the batch anyway?
```

Dialog shape:

```java
public final class BatchMacroDialog {
    public static void show(Window owner, String macro) {
        new BatchMacroDialog(owner, macro).show();
    }

    private final JTable table = new JTable(new InputTableModel());
    private final JTextField folderField = new JTextField(24);
    private final JTextField regexField = new JTextField("(?i).*\\.(tif|tiff|png|jpg|jpeg|gif|bmp|ics|ids)", 24);
    private final JCheckBox recursive = new JCheckBox("Include subfolders", true);
    private final JTextField outputField = new JTextField(24);
    private final JButton preview = new JButton("Preview");
    private final JButton run = new JButton("Run");
    private final JButton cancel = new JButton("Cancel batch");
}
```

Table columns:

```text
Run | File | Folder | Type | Size
```

For ordinary file inputs, `Type` can be the extension and `Size` can be blank. Stage 04 can add dimensions for container series.

Preview action:

```java
try {
    List<BatchMacroInput> inputs = new BatchMacroScanner().scanFolder(
            new File(folderField.getText().trim()),
            regexField.getText().trim(),
            recursive.isSelected());
    tableModel.setInputs(inputs, true);
    statusLabel.setText(inputs.size() + " matching file(s).");
} catch (PatternSyntaxException ex) {
    IJ.showMessage("Run as Batch", "Invalid filename regex:\n" + ex.getMessage());
}
```

Run action:

```java
List<BatchMacroInput> selected = tableModel.selectedInputs();
File outputDir = new File(outputField.getText().trim());
File csv = new File(outputDir, "Macro_Builder_Batch_Run.csv");
batchWorker = new SwingWorker<BatchRunResult, Void>() {
    @Override protected BatchRunResult doInBackground() throws Exception {
        List<BatchMacroResult> rows = new BatchMacroRunner().run(
                selected, macro, outputDir, progressAdapter);
        Files.write(csv.toPath(),
                BatchMacroRunner.buildCsv(rows).getBytes(StandardCharsets.UTF_8));
        return new BatchRunResult(rows, csv, cancelRequested);
    }
};
```

The cancel button should set a volatile flag and the runner should stop before the next item. Do not attempt to interrupt a currently running ImageJ macro.

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. Clicking `Run as batch...` no longer shows the placeholder message.
3. Folder preview lists matching ordinary image files and lets the user untick rows.
4. Invalid regex shows a clear message and does not start a run.
5. A manual run on at least two ordinary TIFF or PNG files saves TIFF outputs plus `Macro_Builder_Batch_Run.csv`.
6. Cancel changes status and stops before starting the next file.
7. The selected source image in the main Macro Builder window is unchanged after a folder batch run.

## Known risks

- Swing table tests may be brittle in headless builds. Prefer testing table model and validation helpers directly.
- Do not block the Swing event thread while running macros. Use `SwingWorker` like `ThresholdShootoutDialog`.
- If the output folder is inside the input folder, the scanner may pick up outputs on a later preview. The UI should not auto-preview after running, and docs can recommend a separate output folder.
