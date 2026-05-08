# Macro Builder

Macro Builder is a standalone Fiji/ImageJ plugin for building ImageJ macro (`.ijm`) filter steps from one image, stack, or multichannel hyperstack. It opens from `Plugins > Macro Builder > Macro Builder` and provides a small desktop UI for selecting an open Fiji image or opening a single image, folder, stack, or microscope container from disk. For multichannel hyperstacks, choose the primary channel to process, then optionally use parallel branches from other channels to subtract, mask, or combine signals. After building a filter macro, users can test object counts with threshold shootouts, export count results to CSV, and save a batch count macro for ordinary image folders.

The plugin is intentionally standalone. It does not include a bin-analysis importer, channel setup workflow, batch import workflow, or any project-specific analysis setup.

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
4. Use `Test Counts...` to compare threshold methods and count `2D particles` or `3D stack objects` from the processed macro output.
5. Use `Save as batch macro...` to export a wrapper macro and settings for batch count runs.

In `Build Macro`, steps are grouped by command type. Use the row `+` buttons to add commands, double-click or right-click sandbox steps to edit their parameters, preview selected or full results in the embedded preview pane, and Ctrl-click or Shift-click branches before `Merge selected branches`.

For microscope container formats such as `.czi`, `.lif`, `.nd2`, `.oib`, `.oif`, `.lsm`, or `.zvi`, Fiji's Bio-Formats plugin must be available. Standard Fiji installations normally include it. Container files and folder-style datasets show a Bio-Formats series list first, so you can select the specific series/image before Macro Builder loads it.

## Build From Source

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

The uploadable plugin jar is written to:

```text
target/Macro_Builder-0.2.0.jar
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

## License

This project is released under CC0 1.0 Universal. See [LICENSE](LICENSE).
