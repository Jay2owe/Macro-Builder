# Developer Guide

## Scope

Macro Builder is a standalone Fiji/ImageJ plugin. Keep it focused on one user-selected image or image stack.

Do not add project-specific importers, channel naming setup, bin analysis setup, or batch import workflows to this repository. Those workflows belong outside the public standalone plugin.

## Source Layout

```text
src/main/java/macro/builder/Macro_Builder.java       Launcher and session UI
src/main/java/macro/builder/Macro_Builder_Batch_Count.java
                                                        Fiji command used by exported batch count macros
src/main/java/macro/builder/analysis/                Count shootout models, thresholding, object counting, batch CSV, and batch macro export
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
.\mvnw.cmd clean test "-Denforcer.skip=true"
.\mvnw.cmd clean -DskipTests "-Denforcer.skip=true" package
```

On macOS or Linux:

```sh
./mvnw clean test -Denforcer.skip=true
./mvnw clean -DskipTests -Denforcer.skip=true package
```

The main plugin jar is:

```text
target/Macro_Builder-0.2.0.jar
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

Keep Bio-Formats optional unless a future change truly needs compile-time Bio-Formats APIs. A normal Fiji installation already provides Bio-Formats at runtime.

## Count Testing

Count testing lives in `src/main/java/macro/builder/analysis/`:

- `ShootoutSettings` stores the count mode, threshold mode, automatic methods, fixed thresholds, size filters, and foreground polarity.
- `ThresholdShootoutRunner` duplicates the source image, runs the current macro through `FilterExecutor`, measures the processed output range, thresholds each variant, and builds binary masks for counting.
- `ObjectCounter` counts connected foreground components as either `2D particles` or `3D stack objects`.
- `BatchShootoutRunner` applies the same runner and settings to ordinary image files or folders, then builds batch CSV rows.
- `BatchMacroExporter` writes the wrapper macro, filter macro, and `.settings.json` sidecar used by `Macro_Builder_Batch_Count`.

Fixed numeric thresholds must use the processed macro output's native intensity scale. On a 16-bit processed image, `2000` means intensity `2000`; do not remap fixed threshold values to `0-255` before applying them. Automatic threshold methods may use a histogram projection internally, but result rows should report values back in the processed image's native scale.

Run count analysis on duplicates only. `ThresholdShootoutRunner`, preview actions, selected-row mask previews, and batch runs must not mutate the selected source image.

Batch count mode intentionally supports ordinary image files first. Bio-Formats containers are listed during selection but skipped by `BatchShootoutRunner` with a CSV error row, because container series selection is interactive and belongs in single-image opening.

## Regression Tests

Current automated tests cover:

- ImageJ macro parser behavior.
- Visual graph serialization and round-tripping.
- Sandbox catalog grouping, inline edit helpers, branch naming, branch multi-selection, selected-branch merge ordering, and merge input reordering.
- Native `2D particles` and `3D stack objects` counting.
- Single-image threshold shootouts, including native-scale fixed thresholds.
- Batch count CSV behavior and window cleanup.
- Batch macro export and `Macro Builder Batch Count` settings round-tripping.

Run them before every release:

```powershell
.\mvnw.cmd clean test "-Denforcer.skip=true"
```

Manual Fiji UI testing is still required before update-site upload.
