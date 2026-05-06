# Macro count testing, threshold shootout, and batch validation

## End goal

Macro Builder will let users test what their current filter macro means for object counting. They can compare threshold methods on one image or stack, count 2D particles or 3D stack objects, then run the same test across a batch of images. They can export results and save a batch-applicable macro.

## Why we're doing this

Macro Builder can preview a filtered image, but it does not show the analysis consequence: how many objects the macro would produce. This work adds count testing so users can judge whether a macro is stable, too permissive, or too strict before committing to an analysis workflow.

## Architecture overview

`Macro_Builder.java` remains the launcher and session UI. New analysis classes run the existing `FilterExecutor` on duplicated images, then threshold and count the duplicate output. UI classes display shootout results and export CSV. Batch support reuses the same single-image runner so single-image, batch, and saved batch behavior stay consistent.

```text
selected image -> duplicate -> current macro -> threshold variants -> 2D/3D counter -> table/CSV
                                      batch runner reuses the same settings and runner
```

## Stage map

| NN | name | one-line goal | rough size | depends on |
|---|---|---|---|---|
| 01 | count-model-native-counter | Add result models and native 2D/3D connected-object counting with tests. | medium | none |
| 02 | threshold-shootout-runner | Run the current macro plus auto and fixed threshold variants on one image/stack. | medium | 01 |
| 03 | shootout-dialog-ui | Add `Test counts...` UI, results table, previews, and CSV export. | medium-large | 02 |
| 04 | batch-shootout-runner | Run the same macro/count shootout across selected image files or a folder. | medium-large | 02 |
| 05 | batch-macro-export | Add `Save batch macro...` and batch-compatibility warnings. | medium | 02, 04 |
| 06 | docs-and-manual-validation | Update user/developer docs and add manual Fiji validation steps. | small | 03, 04, 05 |

## House rules

- Keep this standalone: no project-specific bin-analysis importer or channel setup workflow.
- Batch means user-selected files or folders only, not a lab-specific import pipeline.
- Run analysis on duplicates. Never mutate the selected source image.
- Fixed numeric thresholds use the processed macro output's native intensity scale. Do not convert fixed-threshold inputs to 8-bit before applying the threshold.
- After any threshold is applied, convert the thresholded result to a binary mask for counting.
- The UI must show the processed image range before fixed thresholds are run, for example `Macro output range: 182-12480`.
- Support `2D particles` and `3D stack objects` from the same settings object so single-image testing, batch testing, and saved batch macros agree.
- Keep Bio-Formats optional unless a stage explicitly needs runtime chooser behavior.
- Follow `AGENTS.md`: deploy means copying the built jar only to the local Fiji plugin folders.
- Run `.\mvnw.cmd clean test "-Denforcer.skip=true"` for automated verification before handoff when code changes are made.

## Known open questions

- The default 3D size filter should probably start at `min=100 voxels, max=Infinity`; confirm against real lab data during manual testing.
- Batch Bio-Formats containers should be deferred unless ordinary image and TIFF stack batch testing is already stable.
- Saved batch runs should default to CSV results only; mask saving can be optional to avoid large output folders.

## How to run a stage

Run `/do-step docs/count-shootout-batch/` to execute the first incomplete numbered stage.
