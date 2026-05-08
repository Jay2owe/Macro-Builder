# User Guide

## Launch

After installation, restart Fiji and choose:

```text
Plugins > Macro Builder > Macro Builder
```

Macro Builder opens as a launcher with `Selected image` controls above `Workflows` on the left, the loaded macro text in the center, and `Loaded Macro` actions on the right. It can use the currently active Fiji image, or it can open one image, folder, image stack, or microscope container from disk.

## Select An Image

- `Use current Fiji image` selects the active image window in Fiji.
- `Open Image/Container` opens one image, folder, stack, or microscope container from disk.
- `Open Last Image/Container` reopens the last image or container path Macro Builder remembers, when that path still exists.

If you start a workflow that needs an image or stack before selecting one, Macro Builder opens the image/container chooser automatically.

Supported direct image formats include TIFF, PNG, JPEG, GIF, BMP, ICS, and IDS. For microscope containers such as CZI, LIF, ND2, OIB, OIF, LSM, ZVI, and OME files, Fiji's Bio-Formats plugin must be installed.

When you select a container file or folder-style dataset, Macro Builder reads the Bio-Formats series list and asks which series to open before loading the image.

## Build A Macro

Use one of the two `Workflows` authoring tiles:

- `Build Macro`: opens the visual filter builder.
- `Macro Recorder`: opens a recorder workflow for capturing ImageJ macro commands.

The visual builder has source and output previews on the left, the sandbox in the middle, and grouped `Available steps` on the right. Step groups include filters, 3D commands, binary commands, image type conversions, plugins, and other Fiji commands.

Use a row `+` button in `Available steps`, or double-click a command row, to add that command to the selected branch. You can also select a command row, then click `+ Add step` on a branch. New steps open their parameter editor automatically. When the source image has calibrated micron metadata, spatial parameters such as `sigma`, `radius`, `rolling`, `x`, `y`, and `z` are shown in microns in both the editor and `Your filter`. Defaults are interpreted in the visible unit, then converted to pixels when saved. If calibration is missing, or X/Y pixel sizes differ for a single-radius filter, the editor shows pixels. Saved filters store pixel values so later metadata loss during macro execution does not change the filtering. Double-click or right-click a step in the sandbox to edit its parameters later. Right-click a step to preview to that point or delete it.

Use `+ Add parallel branch` to add another branch. Double-click a branch title or empty branch area to rename it; the saved name replaces labels such as `Branch 1` and is shown in merge labels. Ctrl-click branches to toggle them, or Shift-click to select a range, then click `Merge selected branches`. Double-click or right-click a merge card to change its operation, inputs, and input order.

`Preview to selected point` runs only up to the selected step or merge card. `Preview full filter` runs the whole builder chain. Both preview buttons update the embedded output preview and leave the selected source image unchanged. `Large view` opens the source and output previews in a larger side-by-side window. The source and output preview Z sliders stay synced in both the embedded view and the large view, so moving to a slice in either preview shows the matching slice in the other preview.

### Multichannel Hyperstacks

When the selected source image has more than one channel, the visual builder shows a `Primary channel` control with numeric channel choices such as `C1`, `C2`, and `C3`. Branch 1 starts from the primary channel. Additional parallel branches can start from any available channel.

Example: choose `C1` as the primary object channel, add a second branch from `C2`, filter the `C2` branch, then merge the two branches with `Subtract` so the output is `C1` minus the processed `C2` signal. Merge input order is editable from the merge card; ordered operations such as `Subtract`, and ImageJ fallback `AND` operations with several inputs, use the listed order.

The generated macro appears in the `Loaded Macro` panel.

Choose a macro from `Load Saved Macro` to load its macro text. Macro Builder also loads the matching `.dag.json` sidecar when one exists.

## Test A Macro

The `Loaded Macro` column is enabled after a macro has been built, recorded, or loaded.

- `Run as batch...` runs the loaded macro on selected batch inputs and saves processed TIFF images plus a CSV summary.
- `Save as batch macro...` exports a batch count wrapper for the current macro and latest count settings.
- `Edit Macro...` opens the visual builder using the current macro.
- `Create Macro Variations...` is a placeholder and is not implemented yet.
- `Test Counts...` opens a count-testing dialog for the current macro and selected source image.

The source image is not modified by count testing.

## Run Macro Outputs In Batch

Use `Run as batch...` when you want to apply the loaded macro itself to many inputs and save processed images. This is separate from `Test Counts...` > `Run batch...`, which runs count validation and writes count rows instead of saving processed macro-output images.

The batch dialog has two input modes:

- Folder mode scans an ordinary image folder. Choose an input folder, enter a filename regular expression, choose whether to include subfolders, then click `Preview`. The regular expression must match the whole filename. For example, `(?i).*_DAPI\.tif` matches `Sample1_DAPI.tif`, and `(?i).*\.(tif|tiff|png)` matches common TIFF and PNG names.
- Container mode lists images inside one Bio-Formats microscope container. Choose a `.lif`, `.czi`, `.nd2`, or similar container file, click `List series`, then tick only the series/images you want to process. This requires Fiji's Bio-Formats plugin at runtime.

Previewed rows are ticked by default. Untick rows you do not want, choose an output folder, then click `Run`. `Cancel batch` stops before the next input; it does not interrupt the macro currently running on an image.

Successful file rows are saved as TIFF files with `_MacroBuilder.tif` appended to the source name. Recursive folder runs preserve matching subfolder paths under the output folder. Container rows are saved with the container name, one-based series number, optional series name, and `_MacroBuilder.tif`. The dialog also writes `Macro_Builder_Batch_Run.csv` with one row per attempted input, including the source, input kind, container series index/name when relevant, output path, status, and error message.

Macro Builder opens each batch input independently and closes its temporary image after saving or failure. The selected source image in the main Macro Builder window is not used as a batch input unless you also select that file in the batch dialog.

## Test Counts

Use `Test Counts...` after building, recording, or loading a macro. Macro Builder duplicates the selected source image, runs the current macro on the duplicate, thresholds the processed result, converts the thresholded result to a binary mask, and counts the mask. The selected source image remains unchanged.

Counting modes:

- `2D particles` counts connected foreground objects on each slice independently.
- `3D stack objects` counts connected foreground objects through the stack as volumes.

Threshold modes:

- `Auto threshold shootout` runs the listed automatic threshold methods and adds one result row per method.
- `Fixed numeric threshold` runs one or more comma-separated fixed thresholds, for example `2000,5000`.
- `Auto methods + fixed thresholds` runs both automatic methods and fixed values.

Fixed numeric thresholds use the processed macro output's native intensity scale. On a 16-bit processed image, `2000` means intensity `2000`. Macro Builder does not remap that value to `0-255` before thresholding.

The dialog shows `Macro output range` after a run so you can choose fixed thresholds that match the processed image. Each result row includes the threshold variant, count mode, threshold value, object count, mean object size, foreground coverage, output range, and status. Select a successful row and click `Open mask preview` to inspect the counted binary mask.

Click `Export CSV...` to save the current single-image count table.

## Batch Count Testing

In the `Test Counts` dialog, click `Run batch...` to run the same macro and count settings on selected image files or a selected folder. This is a count-validation workflow, not the macro-output TIFF workflow above. The batch run writes a CSV file with one row per input file and threshold variant. The CSV includes file metadata, count settings, threshold value, count, mean size, coverage, macro output range, status, and any error message.

Batch count testing supports ordinary image files such as TIFF, PNG, JPEG, GIF, BMP, ICS, and IDS. Bio-Formats containers are skipped in batch mode; open those files individually first if you need the Bio-Formats series chooser.

## Save A Batch Macro

Click `Save as batch macro...` to save a batch count wrapper for the current macro and latest count settings. Macro Builder writes three files:

- A wrapper `.ijm` macro that asks for an input folder and output folder, then runs `Macro Builder Batch Count`.
- A `_Filter.ijm` macro containing the filter steps.
- A `.settings.json` file containing the primary channel, count mode, threshold mode, fixed thresholds, size filters, and output CSV name.

If you have not opened `Test Counts...` in the current session, `Save as batch macro...` uses default count settings: `2D particles`, automatic threshold methods, minimum size `0`, maximum size `Infinity`, and bright objects on a dark background.

Some recorded macros contain commands that may not be safe in batch mode, such as commands that open a fixed file path or depend on the active window. Macro Builder warns before saving those macros, but you should still test the saved wrapper on a small folder before using it for real data.

## Local State

Macro Builder stores its current working state in:

```text
~/.macro-builder
```

This state is only used by Macro Builder and can be deleted if you want a fresh session.
