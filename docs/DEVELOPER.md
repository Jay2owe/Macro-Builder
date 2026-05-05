# Developer Guide

## Scope

Macro Builder is a standalone Fiji/ImageJ plugin. Keep it focused on one user-selected image or image stack.

Do not add project-specific importers, channel naming setup, bin analysis setup, or batch import workflows to this repository. Those workflows belong outside the public standalone plugin.

## Source Layout

```text
src/main/java/macro/builder/Macro_Builder.java       Launcher and session UI
src/main/java/macro/builder/ui/                      Swing dialogs and UI helpers
src/main/java/macro/builder/ui/sandbox/              Visual builder UI
src/main/java/macro/builder/image/                   Macro loading, parsing, and execution
src/main/java/macro/builder/image/dag/               Visual graph model and serialization
src/main/resources/plugins.config                    Fiji Plugins menu registration
src/main/resources/named-filters/                    Built-in filter macro presets
src/test/java/                                       Parser and graph regression tests
scripts/smoke-fiji.ps1                               Windows Fiji install helper
```

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
target/Macro_Builder-0.1.1.jar
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

The plugin first opens files with `IJ.openImage`. If that fails, it tries Fiji's `Bio-Formats Importer` command by name.

Keep Bio-Formats optional unless a future change truly needs compile-time Bio-Formats APIs. A normal Fiji installation already provides Bio-Formats at runtime.

## Regression Tests

Current automated tests cover:

- ImageJ macro parser behavior.
- Visual graph serialization and round-tripping.

Run them before every release:

```powershell
.\mvnw.cmd clean test "-Denforcer.skip=true"
```

Manual Fiji UI testing is still required before update-site upload.
