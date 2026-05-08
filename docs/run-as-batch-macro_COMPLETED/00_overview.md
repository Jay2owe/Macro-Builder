# Run as batch for loaded Macro Builder macros

## End goal

The right-side `Run as batch...` button becomes a real workflow for applying the currently loaded macro to many images. Users can either choose images inside a microscope container and tick which series/images to process, or scan a normal folder with a full-filename regular expression and tick matching files. The batch run saves TIFF outputs and writes a CSV summary of successes and failures.

## Why we're doing this

The button currently only says the feature is not implemented, which blocks the obvious next step after building or loading a useful macro. The existing `Test Counts... > Run batch...` path is for count validation and intentionally skips Bio-Formats containers; this new workflow is for running the macro itself and saving processed image outputs.

## Architecture overview

`Macro_Builder.SessionDialog` should hand the loaded macro to a new batch dialog. Folder scans and container series listing should be separated from execution. A batch runner opens one selected input at a time, runs `FilterExecutor.runThreadSafe(...)`, saves a TIFF result, records a CSV row, and closes all temporary images before moving on.

```text
loaded macro
  -> BatchMacroDialog
      -> BatchMacroScanner or BioFormatsSeriesProvider
      -> selected BatchMacroInput rows
      -> BatchMacroRunner
          -> open input -> run FilterExecutor -> save TIFF -> CSV row -> close images
```

## Stage map

| NN | name | one-line goal | rough size | depends on |
|---|---|---|---|---|
| 01 | batch-model-and-folder-scan | Add batch input/result models plus CPC-style folder regex scanning and tests. | medium | none |
| 02 | macro-batch-runner | Run the loaded macro over ordinary image files, save TIFF outputs, and write CSV results. | medium-large | 01 |
| 03 | batch-dialog-folder-mode | Wire `Run as batch...` to a dialog with folder regex preview, tickable rows, progress, cancel, and output selection. | large | 01, 02 |
| 04 | bioformats-container-selection | Add Bio-Formats container series listing/opening and tickable container-series batch input. | large | 01, 02, 03 |
| 05 | docs-and-smoke-checks | Update docs and record manual Fiji checks for folder and container batch runs. | small-medium | 03, 04 |

## House rules

- Follow `AGENTS.md`: be concise, use plain language, and explain niche terms on first use.
- Deploy means local Fiji/plugin-folder jar replacement only. Do not publish to GitHub Actions or the Fiji update site unless the user explicitly asks.
- Keep Macro Builder standalone. Do not add project-specific importers, channel setup, lab folder conventions, or bin-analysis workflows.
- Keep Bio-Formats optional unless a stage proves compile-time Bio-Formats APIs are truly needed. Normal Fiji installations provide Bio-Formats at runtime.
- Do not turn this into count testing. The existing count batch workflow stays under `Test Counts...`.
- Batch runs must not mutate the selected source image. Open or duplicate each batch item independently and close per-file temporary images.
- Preserve unrelated local changes in the worktree. At plan creation time, unrelated local changes existed in `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java`, `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java`, and `src/main/java/macro/builder/ui/sandbox/ArgsEditorModel.java`.
- Use `apply_patch` for manual edits.
- Run `.\mvnw.cmd test "-Denforcer.skip=true"` for automated verification before handoff when code changes are made.

## Known open questions

- Container series enumeration may need reflection or an optional Maven dependency. The executing stage should choose the least invasive approach that keeps the plugin buildable.
- Output format is planned as TIFF-only first. Any other output format should be deferred unless an existing local pattern makes it trivial.
- Exact container-series metadata available from Bio-Formats may vary by runtime version, so the table should degrade gracefully when a field is missing.

## How to run a stage

Run `/do-step docs/run-as-batch-macro/` to execute the first incomplete numbered stage.
