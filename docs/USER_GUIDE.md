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

When you select a container file or folder-style dataset, Macro Builder opens Fiji's Bio-Formats chooser. Use that dialog to select the series/image inside the container. If Bio-Formats opens more than one image, Macro Builder asks which imported image it should use as the selected source.

## Build A Macro

Use one of the two `Workflows` authoring tiles:

- `Build Macro`: opens the visual filter builder.
- `Macro Recorder`: opens a recorder workflow for capturing ImageJ macro commands.

The generated macro appears in the `Loaded Macro` panel.

Choose a macro from `Load Saved Macro` to load its macro text. Macro Builder also loads the matching `.dag.json` sidecar when one exists.

## Test A Macro

The `Loaded Macro` column is enabled after a macro has been built, recorded, or loaded.

- `Run as batch...` currently shows a message directing you to `Test Counts...` > `Run batch...`.
- `Save as batch macro...` exports a batch count wrapper for the current macro and latest count settings.
- `Edit Macro...` opens the visual builder using the current macro.
- `Create Macro Variations...` is a placeholder and is not implemented yet.
- `Test Counts...` opens a count-testing dialog for the current macro and selected source image.

The source image is not modified by count testing.

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

In the `Test Counts` dialog, click `Run batch...` to run the same macro and count settings on selected image files or a selected folder. The batch run writes a CSV file with one row per input file and threshold variant. The CSV includes file metadata, count settings, threshold value, count, mean size, coverage, macro output range, status, and any error message.

Batch count testing supports ordinary image files such as TIFF, PNG, JPEG, GIF, BMP, ICS, and IDS. Bio-Formats containers are skipped in batch mode; open those files individually first if you need the Bio-Formats series chooser.

## Save A Batch Macro

Click `Save as batch macro...` to save a batch count wrapper for the current macro and latest count settings. Macro Builder writes three files:

- A wrapper `.ijm` macro that asks for an input folder and output folder, then runs `Macro Builder Batch Count`.
- A `_Filter.ijm` macro containing the filter steps.
- A `.settings.json` file containing the count mode, threshold mode, fixed thresholds, size filters, and output CSV name.

If you have not opened `Test Counts...` in the current session, `Save as batch macro...` uses default count settings: `2D particles`, automatic threshold methods, minimum size `0`, maximum size `Infinity`, and bright objects on a dark background.

Some recorded macros contain commands that may not be safe in batch mode, such as commands that open a fixed file path or depend on the active window. Macro Builder warns before saving those macros, but you should still test the saved wrapper on a small folder before using it for real data.

## Local State

Macro Builder stores its current working state in:

```text
~/.macro-builder
```

This state is only used by Macro Builder and can be deleted if you want a fresh session.
