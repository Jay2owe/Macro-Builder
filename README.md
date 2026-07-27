# Macro Builder

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.21633369.svg)](https://doi.org/10.5281/zenodo.21633369)

Macro Builder is a standalone Fiji/ImageJ plugin for building ImageJ macro (`.ijm`) filter steps from one image, stack, or multichannel hyperstack. It opens from `Plugins > Macro Builder > Macro Builder` and provides a small desktop UI for selecting an open Fiji image or opening a single image, folder, stack, or microscope container from disk. For multichannel hyperstacks, choose the primary channel to process, then optionally use parallel branches from other channels to subtract, mask, or combine signals. After building or loading a filter macro, users can run that macro across selected files or Bio-Formats container series and save TIFF outputs with a CSV summary. Separately, users can test object counts with threshold shootouts, export count results to CSV, and save a batch count macro for ordinary image folders.

The plugin is intentionally standalone. It does not include a bin-analysis importer, channel setup workflow, batch import workflow, or any project-specific analysis setup.

## Features

- Visual macro builder for single-channel images, stacks, and multichannel hyperstacks.
- Channel-aware branches and merge operations for subtracting, masking, and combining signals.
- Built-in filter presets shipped as ImageJ macro resources inside the plugin jar.
- Macro recorder integration for ImageJ commands that are not yet represented as native builder steps.
- Batch macro-output runner for ordinary image files and selected Bio-Formats container series.
- Count testing with threshold shootouts, CSV export, and reusable batch count macros.

## Install From Fiji

The public update site is:

```text
https://sites.imagej.net/Macro-Builder/
```

In Fiji:

1. Open `Help > Update...`.
2. Click `Manage update sites`.
3. Click `Add Unlisted Site`.
4. Set the name to `Macro-Builder` and the URL to `https://sites.imagej.net/Macro-Builder/`.
5. Apply the update and restart Fiji.

After restart, launch the plugin from `Plugins > Macro Builder > Macro Builder`.

## Basic Use

1. Use `Use current Fiji image`, `Open Image/Container`, or `Open Last Image/Container` to select the source image.
2. Use `Build Macro` to build a visual filter pipeline, or `Macro Recorder` to record ImageJ macro actions.
3. Use `Load Saved Macro` to load a previously saved macro into the `Loaded Macro` panel.
4. Use `Run as batch...` to apply the loaded macro to selected ordinary image files or selected series inside one Bio-Formats microscope container, saving TIFF outputs and `Macro_Builder_Batch_Run.csv`.
5. Use `Test Counts...` to compare threshold methods and count `2D particles` or `3D stack objects` from the processed macro output.
6. Use `Save as batch macro...` to export a self-contained macro for batch count runs, either in Fiji's macros folder or another location.

In `Build Macro`, steps are grouped by command type. Use the row `+` buttons to add commands, double-click or right-click sandbox steps to edit their parameters, preview selected or full results in the embedded preview pane, and Ctrl-click or Shift-click branches before `Merge selected branches`.

For microscope container formats such as `.czi`, `.lif`, `.nd2`, `.oib`, `.oif`, `.lsm`, or `.zvi`, Fiji's Bio-Formats plugin must be available. Standard Fiji installations normally include it. Container files and folder-style datasets show a Bio-Formats series list first, so you can select the specific series/image before Macro Builder loads it.

## Output

`Run as batch...` saves processed TIFF images with `_MacroBuilder.tif` appended to the source name and writes `Macro_Builder_Batch_Run.csv` in the selected output folder. Recursive folder runs preserve matching subfolder paths. Bio-Formats container rows are named from the container, one-based series number, optional series name, and `_MacroBuilder.tif`.

Count testing and saved batch count macros write CSV result tables. Those rows include the input file or container series, selected channel, threshold method or fixed threshold, count mode, count, size and coverage summaries, status, and any error message.

## Macro And Java API Automation

Run saved filter macros across ordinary image files from an ImageJ macro without opening the Macro Builder UI:

```ijm
run("Macro Builder",
    "macro=[C:/analysis/filter.ijm] " +
    "input=[C:/analysis/images] " +
    "output=[C:/analysis/output]");
```

Options are whitespace-separated. Paths with spaces must be bracketed.

| Option | Meaning | Default |
| --- | --- | --- |
| `macro=[path]` | Saved `.ijm` filter macro to run. | Required |
| `input=[path]` | Input image file, image folder, or Bio-Formats container file. | Required |
| `output=[folder]` | Folder for processed TIFFs and CSV output. | Required |
| `regex=[pattern]` | Folder filename regex. Must match the whole filename. | Image files |
| `recursive=true|false` | Recurse through input subfolders. | `true` |
| `series=<n>` | One-based Bio-Formats series for container-file input. | `1` |
| `csv=[name-or-path]` | CSV summary path; use `csv=[none]` to skip. | `Macro_Builder_Batch_Run.csv` |

The existing batch-count command remains macro-callable through exported settings:

```ijm
run("Macro Builder Batch Count",
    "settings=[C:/analysis/count.settings.json] " +
    "input=[C:/analysis/images] " +
    "output=[C:/analysis/counts]");
```

Other plugins can call the public Java API:

```java
MacroBuilderResult result = MacroBuilder.runBatch(
    MacroBuilderParameters.builder()
        .addInput(BatchMacroInput.file(imageFile, imageFile.getName()))
        .macro(filterMacroText)
        .outputDirectory(outputFolder)
        .build());
```

The public API lives under `macro.builder.api`. It returns result objects and does not open Macro Builder dialogs.

Reusable API facades are available for the main non-UI engines:

| Class | Purpose |
| --- | --- |
| `MacroBuilder` | Batch macro-output runs and batch count runs. |
| `MacroBuilderCounting` | Single-image Test Counts, one threshold variant, binary object counting, and object detection. |
| `MacroBuilderMacros` | Apply a chosen count result back into `.ijm` macro text or a Macro Builder DAG. |
| `MacroBuilderFilters` | Load bundled presets, parse/edit filter macros, inspect filter parameters, and list compatible filter swaps. |
| `MacroBuilderInputs` | Scan image folders and list/open Bio-Formats container series. |
| `MacroBuilderBatchExport` | Build or save self-contained batch-count wrapper macros and settings JSON. |

For example, another plugin can run a single-image threshold shootout and promote the selected result into macro text:

```java
List<ShootoutResult> rows = MacroBuilderCounting.runShootout(
    imp, filterMacroText, ShootoutSettings.defaults());
String updatedMacro = MacroBuilderMacros.applyThresholdToIjm(
    filterMacroText, rows.get(0), ShootoutSettings.defaults());
MacroBuilderCounting.closeMaskPreviews(rows);
```

The lower-level facades also support reuse of Macro Builder's presets and input discovery:

```java
String preset = MacroBuilderFilters.loadPreset("Default");
List<BatchMacroInput> inputs = MacroBuilderInputs.scanFolder(
    imageFolder, "(?i).*\\.tif", true);
MacroBuilderBatchExport.exportWrapperMacro(
    wrapperFile, preset, ShootoutSettings.defaults(), 1);
```

APIs that return `ImagePlus` objects, such as variation results, mask previews, retained shootout contexts, or opened Bio-Formats series, transfer image ownership to the caller. Close or flush those images when finished.

Variation workflows can also be used from Java when you already have a Macro Builder DAG or saved macro:

```java
DagIR baseline = MacroBuilderVariations.loadDag(filterMacroText);
VariantAxis sweep = MacroBuilderVariations.paramSweep(
    "node_1", "sigma=1 stack", "sigma=2 stack", "sigma=4 stack");

MacroBuilderVariationResult variants = MacroBuilderVariations.run(
    MacroBuilderVariationParameters.builder()
        .sourceImage(imp)
        .baseline(baseline)
        .addAxis(sweep)
        .maxVariants(4)
        .build());
```

Variant result images are returned as `ImagePlus` objects. Call `closeOutputs()` when you are done with them and do not plan to show or save them.

## How It Works

Macro Builder stores visual builder pipelines as a small directed graph and emits ordinary ImageJ macro text for execution. Native builder steps can run channel-aware branches directly. Recorded or unsupported ImageJ commands are preserved as macro steps and run through ImageJ's macro interpreter.

Preview and count-testing workflows run on duplicate images so the selected Fiji source image is not modified. Multichannel hyperstacks are handled by extracting the selected primary channel or branch channel before applying filters, then merging branch outputs when requested.

Batch runs open each selected input independently, run the loaded macro, save the requested output, append a CSV row, and close temporary images before moving to the next input.

## Build From Source

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

The uploadable plugin jar is written to:

```text
target/Macro_Builder-0.2.2.jar
```

## Local Fiji Smoke Test

On Windows, build and copy the jar into a local Fiji installation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-fiji.ps1 -FijiPluginsDir "C:\path\to\Fiji.app\plugins"
```

Restart Fiji after copying the jar.

## Documentation

- [User guide](docs/USER_GUIDE.md)
- [Developer guide](docs/DEVELOPER.md)
- [Update-site upload checklist](docs/UPDATE_SITE_UPLOAD.md)
- [GitHub Actions release automation](docs/GITHUB_ACTIONS_RELEASE.md)
- [Changelog](CHANGELOG.md)

## Citing Macro Builder

If you use Macro Builder in published work, please cite it. Citation metadata is in [`CITATION.cff`](CITATION.cff) (use GitHub's "Cite this repository" button). A Zenodo DOI will be added here once a tagged release is archived.

Macro Builder is built on Fiji/ImageJ and Bio-Formats. When describing analyses that depend on those platforms, cite the relevant upstream tools as well, for example ImageJ (Schneider et al., 2012), Fiji (Schindelin et al., 2012), ImageJ2/SciJava (Rueden et al., 2017), and Bio-Formats (Linkert et al., 2010).

## License

BSD 3-Clause License. See [`LICENSE`](LICENSE) for the full text.

## Acknowledgements

Developed by Jamie Malcolm in the [Brancaccio Lab](https://www.ukdri.ac.uk/labs/brancaccio-lab) at the [UK Dementia Research Institute](https://ukdri.ac.uk/centres/imperial), Imperial College London.

This work was supported by the UK Dementia Research Institute, which receives its core funding from the UK Medical Research Council, the Alzheimer's Society, and Alzheimer's Research UK.

Built on the [Fiji](https://fiji.sc/) / [ImageJ](https://imagej.net/) ecosystem; we thank the SciJava community for the platform.
