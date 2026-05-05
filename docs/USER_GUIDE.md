# User Guide

## Launch

After installation, restart Fiji and choose:

```text
Plugins > Macro Builder > Macro Builder
```

Macro Builder opens as a small dialog. It can use the currently active Fiji image, or it can open one image, folder, image stack, or microscope container from disk.

## Select An Image

- `Use current Fiji image` selects the active image window in Fiji.
- `Open image/container...` opens one image, folder, stack, or microscope container from disk.

Supported direct image formats include TIFF, PNG, JPEG, GIF, BMP, ICS, and IDS. For microscope containers such as CZI, LIF, ND2, OIB, OIF, LSM, ZVI, and OME files, Fiji's Bio-Formats plugin must be installed.

When you select a container file or folder-style dataset, Macro Builder opens Fiji's Bio-Formats chooser. Use that dialog to select the series/image inside the container. If Bio-Formats opens more than one image, Macro Builder asks which imported image it should use as the selected source.

## Build A Macro

Use one of the two authoring modes:

- `Build step-by-step`: opens the visual filter builder.
- `Record in Fiji`: opens a recorder workflow for capturing ImageJ macro commands.

The generated macro appears in the `Last built macro` panel.

## Test A Macro

- `Preview macro` runs the current macro against a duplicate preview image.
- `Run macro on selected image` runs the current macro on a duplicate of the selected source image.

The source image is not modified by these test actions.

## Save A Macro

Click `Save macro...` to export an ImageJ macro file (`.ijm`).

When a macro was created with the visual builder, Macro Builder also writes a `.dag.json` sidecar next to the macro. The sidecar stores the visual graph so the macro can be reloaded and edited more accurately later.

## Local State

Macro Builder stores its current working state in:

```text
~/.macro-builder
```

This state is only used by Macro Builder and can be deleted if you want a fresh session.
