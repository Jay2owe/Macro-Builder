# User Guide

## Launch

After installation, restart Fiji and choose:

```text
Plugins > Macro Builder > Macro Builder
```

Macro Builder opens as a small dialog. It can use the currently active Fiji image, or it can open one image or image stack from disk.

## Select An Image

- `Use current Fiji image` selects the active image window in Fiji.
- `Open image/stack...` opens one image or stack from disk.

Supported direct image formats include TIFF, PNG, JPEG, GIF, BMP, ICS, and IDS. For microscope containers such as CZI, LIF, ND2, OIB, OIF, LSM, ZVI, and OME files, Fiji's Bio-Formats plugin must be installed.

## Build A Macro

Use one of the two authoring modes:

- `Build step-by-step`: opens the visual filter builder.
- `Record in Fiji`: opens a recorder workflow for capturing ImageJ macro commands.

The generated macro appears in the `Last built macro` panel.

## Test A Macro

- `Preview macro` runs the current macro against a duplicate preview image.
- `Run macro on current image` runs the current macro on a duplicate of the selected source image.

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
