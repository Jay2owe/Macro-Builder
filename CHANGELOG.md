# Changelog

## 0.1.2 - 2026-05-06

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
