# Changelog

## 0.2.2 - 2026-05-21

- Added ImageJ macro option automation for running Macro Builder batch-output workflows without opening the UI.
- Added public Java API facades under `macro.builder.api` for batch runs, counting, filters, inputs, macro updates, batch export, and variation workflows.
- Added API and macro-option parser regression tests.
- Documented macro-callable batch runs and Java API usage.

## 0.2.1 - 2026-05-21

- Added preview display controls, histogram/range UI helpers, sandbox undo history, and macro save helpers.
- Improved batch macro export and ImageJ macro emission behavior.
- Added public release metadata, citation files, GitHub Release automation, and update-site upload documentation.
- Removed completed private planning folders from the public working tree.

## 0.2.0 - 2026-05-07

- Reworked the main launcher UI with compact workflow tiles, loaded-macro controls, saved macro loading, and a shorter scrollable macro view.
- Added automatic image/container prompting for workflows that require a selected image or stack.
- Added multichannel hyperstack support with primary-channel selection, per-branch source channels, channel-aware preview/count execution, and saved batch settings.
- Polished launcher icons and image/container reopening behavior.

## 0.1.2 - 2026-05-06

- Added count testing with threshold shootouts, `2D particles` and `3D stack objects` modes, CSV export, batch count CSV runs, and saved batch count macros.
- Added folder/container opening through Bio-Formats so users can choose the specific series/image inside LIF and other microscope containers before using Macro Builder.

## 0.1.1 - 2026-05-05

- Marked Fiji's ImageJ API as provided so the update-site uploader does not treat core Fiji jars as plugin dependencies.
- Removed embedded Maven POM metadata from the plugin jar to avoid unnecessary dependency inference during upload.

## 0.1.0 - 2026-05-05

- Initial standalone Macro Builder plugin.
- Added a small launcher UI for selecting the current Fiji image or opening one image/stack from disk.
- Added the visual builder, Fiji recorder workflow, macro preview, duplicate-image run action, and macro export.
- Added optional Bio-Formats opening through Fiji for common microscope container formats.
- Removed project-specific importer and batch setup workflows from the standalone plugin.
