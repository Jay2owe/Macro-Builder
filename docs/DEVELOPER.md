# Developer Guide

## Scope

Macro Builder is a standalone Fiji/ImageJ plugin. Keep it focused on one user-selected image or image stack.

Do not add project-specific importers, channel naming setup, bin analysis setup, or batch import workflows to this repository. Those workflows belong outside the public standalone plugin.

## Source Layout

```text
src/main/java/macro/builder/Macro_Builder.java       Launcher and session UI
src/main/java/macro/builder/Macro_Builder_Batch_Count.java
                                                        Fiji command used by exported batch count macros
src/main/java/macro/builder/analysis/                Count shootout models, thresholding, object counting, macro-output batch runs, batch CSV, and batch macro export
src/main/java/macro/builder/ui/                      Swing dialogs and UI helpers
src/main/java/macro/builder/ui/sandbox/              Visual builder UI
src/main/java/macro/builder/image/                   Macro loading, parsing, and execution
src/main/java/macro/builder/image/dag/               Visual graph model and serialization
src/main/resources/plugins.config                    Fiji Plugins menu registration
src/main/resources/named-filters/                    Built-in filter macro presets
src/test/java/                                       Parser and graph regression tests
scripts/smoke-fiji.ps1                               Windows Fiji install helper
```

The sandbox builder split is:

- `SandboxDialog` owns the builder dialog layout, embedded source/output previews, footer actions, preset loading, save/cancel handling, and preview execution.
- `FilterCatalog` owns grouped command discovery, search, category assignment, and row `+` add requests.
- `DagCanvasPanel` renders branches, step cards, merge cards, branch selection, branch multi-select, context menus, and drag reorder.
- `SandboxModel` owns the editable branch/merge state and converts it to and from `DagIR`.
- `StepEditorDialog`, `MergeEditorDialog`, `ArgsEditorModel`, and `RecorderParameterProbe` handle inline parameter editing, merge editing, and Fiji command option capture.

## Visual DAG Channel Metadata

Visual builder DAGs store `primaryChannel` on `DagIR` and `sourceChannel` on each `DagLine`. Channel numbers are 1-based ImageJ channel indexes. Old DAGs without these fields must load as channel 1.

Native DAG execution extracts each branch source channel into a one-channel stack before running branch steps. Combiners operate on those one-channel branch outputs and produce a single processed output image for preview, threshold shootout, and batch count workflows.

The first sandbox branch follows the primary channel. Additional branches may use any available numeric source channel, such as `C2`, but the plugin does not currently store or display human-readable channel names.

## Build

On Windows:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean -DskipTests package
```

On macOS or Linux:

```sh
./mvnw clean test
./mvnw clean -DskipTests package
```

The main plugin jar is:

```text
target/Macro_Builder-0.2.2.jar
```

Do not upload the generated `*-sources.jar` or `*-tests.jar` files to the ImageJ update site.

The ImageJ API dependency is marked as `provided` because Fiji already supplies it. Do not change it back to compile/runtime scope unless the plugin is no longer distributed through Fiji.

## Fiji Menu Registration

The plugin is registered by:

```text
src/main/resources/plugins.config
```

Current menu path:

```text
Plugins > Macro Builder > Macro Builder
```

The jar name intentionally contains an underscore (`Macro_Builder-...jar`) because ImageJ update-site guidance expects plugin jars that add commands to the `Plugins` menu to include an underscore.

## Bio-Formats

The plugin opens ordinary image files with `IJ.openImage`. Known microscope container files and selected directories go straight to Fiji's `Bio-Formats Importer` command by name, with only the `open=[path]` option, so Bio-Formats can show its native series/image chooser. If Bio-Formats opens more than one image window, Macro Builder asks which imported image should become the selected source and closes the other imported images.

`Run as batch...` container mode uses `BioFormatsSeriesProvider` to list and open selected series without adding a compile-time Bio-Formats dependency. The provider checks for `loci.formats.ImageReader`, `loci.plugins.BF`, and `loci.plugins.in.ImporterOptions` by reflection. If those classes are missing, users get a plain Bio-Formats availability message instead of a leaked `ClassNotFoundException`.

Keep Bio-Formats optional unless a future change truly needs compile-time Bio-Formats APIs. A normal Fiji installation already provides Bio-Formats at runtime. If a future provided-scope Bio-Formats dependency is added, confirm that the built plugin jar still does not bundle Bio-Formats classes.

## Macro Output Batch Runs

Direct macro-output batch runs are started from `Loaded Macro` > `Run as batch...` and live in these classes:

- `BatchMacroScanner` scans ordinary image folders with a full-filename regular expression and returns `BatchMacroInput.file(...)` rows.
- `BatchMacroInput` stores either an ordinary file row or a selected Bio-Formats container-series row.
- `BioFormatsSeriesProvider` lists container series metadata and opens one selected series at a time.
- `BatchMacroRunner` opens each selected input independently, runs the loaded macro through `FilterExecutor.runThreadSafe(...)`, saves TIFF output, appends status data for the CSV, and closes temporary images.
- `BatchMacroDialog` is the Swing dialog for folder preview, container-series preview, row ticking, output folder selection, progress, and cancel-after-current-input behavior.

Folder regex matching uses `Matcher.matches()`, so the regex must match the whole filename. Keep this behavior in sync with the user guide. Recursive folder runs preserve relative subfolder paths in the output folder and append `_MacroBuilder.tif` to output names. Container outputs use the source container basename plus a one-based `_sNNN` series suffix and sanitized series name when present. The CSV columns are:

```text
source,kind,series_index,series_name,width,height,channels,slices,frames,output,status,error
```

The stored `series_index` is the zero-based Bio-Formats index used to reopen the series. User-facing labels may display one-based series numbers.

Cancellation is cooperative. `BatchMacroDialog` asks the runner to stop before the next input; it should not interrupt a macro already running inside ImageJ.

## Public Java API

Public API facades live in `src/main/java/macro/builder/api/`. They should stay small, stable, and non-UI:

- `MacroBuilder` runs macro-output batches and batch count workflows.
- `MacroBuilderCounting` wraps single-image threshold shootouts, one-variant count runs, binary object counting, and object detection.
- `MacroBuilderMacros` applies a selected threshold/count result back into `.ijm` text or a Macro Builder DAG.
- `MacroBuilderFilters` exposes bundled presets, macro parsing/editing, batch-compatibility warnings, filter parameter metadata, and compatible filter swaps.
- `MacroBuilderInputs` exposes folder scanning and Bio-Formats series listing/opening.
- `MacroBuilderBatchExport` builds and saves self-contained batch-count wrapper macros.

Do not make Swing dialogs part of the API contract. If an API returns `ImagePlus` objects, document caller ownership and provide a cleanup helper where practical.

## Count Testing

Count testing lives in `src/main/java/macro/builder/analysis/`:

- `ShootoutSettings` stores the count mode, threshold mode, automatic methods, fixed thresholds, size filters, and foreground polarity.
- `ThresholdShootoutRunner` duplicates the source image, runs the current macro through `FilterExecutor`, measures the processed output range, thresholds each variant, and builds binary masks for counting.
- `ObjectCounter` counts connected foreground components as either `2D particles` or `3D stack objects`.
- `BatchShootoutRunner` applies the same runner and settings to ordinary image files or folders, then builds batch CSV rows.
- `BatchMacroExporter` writes a self-contained wrapper macro that embeds the filter macro and count settings. At runtime that wrapper creates temporary support files for `Macro_Builder_Batch_Count`, runs the batch, then removes the temporary files.

Fixed numeric thresholds must use the processed macro output's native intensity scale. On a 16-bit processed image, `2000` means intensity `2000`; do not remap fixed threshold values to `0-255` before applying them. Automatic threshold methods may use a histogram projection internally, but result rows should report values back in the processed image's native scale.

Run count analysis on duplicates only. `ThresholdShootoutRunner`, preview actions, selected-row mask previews, and batch runs must not mutate the selected source image.

Batch count mode intentionally supports ordinary image files first. Bio-Formats containers are listed during selection but skipped by `BatchShootoutRunner` with a CSV error row. Keep this count-testing batch path separate from `Run as batch...`, which can process selected Bio-Formats container series and saves processed TIFF images.

## Regression Tests

Current automated tests cover:

- ImageJ macro parser behavior.
- Visual graph serialization and round-tripping.
- Sandbox catalog grouping, inline edit helpers, branch naming, branch multi-selection, selected-branch merge ordering, and merge input reordering.
- Native `2D particles` and `3D stack objects` counting.
- Single-image threshold shootouts, including native-scale fixed thresholds.
- Batch count CSV behavior and window cleanup.
- Macro-output batch folder scanning, TIFF saving, CSV output, duplicate source preservation, dialog table selection, validation helpers, container output naming, and missing Bio-Formats runtime messages.
- Batch macro export and `Macro Builder Batch Count` settings round-tripping.

Run them before every release:

```powershell
.\mvnw.cmd clean test
```

Manual Fiji UI testing is still required before update-site upload.
