# Bio-Formats container selection

## Why this stage exists

The user specifically wants the batch button to run on selected images inside a microscope container. Folder mode does not solve `.lif`, `.czi`, `.nd2`, and similar multi-series files, so this stage adds container series listing and opening while keeping Bio-Formats optional for normal builds.

## Prerequisites

- `01_batch-model-and-folder-scan.md` completed and renamed with `_COMPLETED`.
- `02_macro-batch-runner.md` completed and renamed with `_COMPLETED`.
- `03_batch-dialog-folder-mode.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/run-as-batch-macro/00_overview.md`
- `AGENTS.md`
- `docs/DEVELOPER.md:68-88` for current Bio-Formats policy and the existing count-batch container limitation.
- `pom.xml:58-76` for current dependencies and provided-scope ImageJ dependency style.
- `src/main/java/macro/builder/Macro_Builder.java:111-114` for current container extensions.
- `src/main/java/macro/builder/Macro_Builder.java:374-383` for the image/container chooser filters.
- `src/main/java/macro/builder/Macro_Builder.java:396-482` for current Bio-Formats importer fallback behavior.
- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java:99-114` for existing container extension detection.
- `src/main/java/macro/builder/ui/BatchMacroDialog.java` from stage 03 for the folder-mode UI to extend.
- `src/main/java/macro/builder/analysis/BatchMacroRunner.java` from stage 02 for ordinary file execution to extend.

## Scope

- Add a Bio-Formats series provider that can:
  - detect whether Bio-Formats runtime classes are available,
  - list series/images inside one selected container,
  - return `BatchMacroInput.containerSeries(...)` rows,
  - open one selected series as an `ImagePlus`.
- Add a container mode to `BatchMacroDialog`:
  - choose container file,
  - list series/images,
  - show tick boxes,
  - run selected series through the same output workflow.
- Extend `BatchMacroRunner` to process `BatchMacroInput.Kind.CONTAINER_SERIES`.
- Save container outputs as TIFF and record series index/name in CSV.
- Add graceful error messages when Bio-Formats is missing or cannot list a container.

## Out of scope

- Automating Bio-Formats importer options beyond selecting one series.
- Processing every file in a folder as a container batch. This stage is one selected container with ticked series/images.
- Count testing for containers.
- Bundling Bio-Formats jars into the Macro Builder jar.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/image/BioFormatsSeriesProvider.java` | NEW | Lists and opens selected series from microscope containers. |
| `src/main/java/macro/builder/analysis/BatchMacroInput.java` | MODIFY | Store any extra metadata discovered from Bio-Formats. |
| `src/main/java/macro/builder/analysis/BatchMacroRunner.java` | MODIFY | Open and run `CONTAINER_SERIES` inputs. |
| `src/main/java/macro/builder/ui/BatchMacroDialog.java` | MODIFY | Add container-selection mode and tickable series table. |
| `src/test/java/macro/builder/image/BioFormatsSeriesProviderTest.java` | NEW | Test missing-runtime behavior and pure helper methods where possible. |
| `pom.xml` | MODIFY MAYBE | Only if reflection is not practical and a provided-scope Bio-Formats dependency is required. |

## Implementation sketch

Prefer reflection first so Macro Builder stays buildable with only the existing provided ImageJ dependency. The exact reflection calls must be verified in the local Fiji runtime during this stage.

Provider shape:

```java
package macro.builder.image;

import ij.ImagePlus;
import macro.builder.analysis.BatchMacroInput;

import java.io.File;
import java.util.List;

public final class BioFormatsSeriesProvider {
    public boolean isAvailable() { ... }

    public List<BatchMacroInput> listSeries(File container) {
        // Reflect Bio-Formats reader classes if available.
        // Return one BatchMacroInput.containerSeries(...) per series.
    }

    public ImagePlus openSeries(BatchMacroInput input) {
        // Open only input.seriesIndex from input.file.
    }
}
```

Expected Bio-Formats runtime classes to investigate:

```text
loci.formats.ImageReader
loci.formats.MetadataTools
loci.plugins.BF
loci.plugins.in.ImporterOptions
```

If reflection proves too fragile, add a provided-scope dependency only after confirming Maven resolves it from the SciJava repository and the built jar does not bundle Bio-Formats:

```xml
<dependency>
    <groupId>ome</groupId>
    <artifactId>formats-gpl</artifactId>
    <scope>provided</scope>
</dependency>
```

That dependency coordinate is a candidate to verify, not a blind requirement. If the local SciJava BOM exposes a different Bio-Formats artifact, use the artifact already managed by the parent POM.

Opening a specific series should avoid the interactive chooser:

```java
ImporterOptions options = new ImporterOptions();
options.setId(container.getAbsolutePath());
options.setOpenAllSeries(false);
options.setSeriesOn(seriesIndex, true);
ImagePlus[] images = BF.openImagePlus(options);
```

When using reflection, wrap failures in a plain message:

```text
Bio-Formats could not list images in this container. A normal Fiji installation includes Bio-Formats, but this Fiji instance may not.
```

Output naming:

```text
container: sample.lif
series: 3, "DAPI"
output: sample_s003_DAPI_MacroBuilder.tif
```

Update CSV rows to include:

```text
kind=CONTAINER_SERIES
series_index=3
series_name=DAPI
```

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. When Bio-Formats classes are absent from the test runtime, provider tests produce a clear "not available" path rather than `ClassNotFoundException` leaking to users.
3. Manual in Fiji: choose a real `.lif`, `.czi`, or `.nd2` container and see a list of series/images with tick boxes.
4. Manual in Fiji: untick at least one listed series and confirm it is not processed.
5. Manual in Fiji: run at least one selected container series and confirm TIFF output plus CSV row with series index/name.
6. Manual in Fiji: no extra Bio-Formats or temporary image windows remain open after the run.

## Known risks

- Bio-Formats APIs are not part of the current compile-time dependencies. Reflection keeps the build light but needs careful runtime testing inside Fiji.
- Some containers report incomplete metadata. Show blanks or `0` for unavailable dimensions rather than failing the list.
- Series indexes are usually zero-based in Bio-Formats APIs. User-facing display can be one-based, but stored `seriesIndex` should match the API index used for opening.
- Do not silently process all series. The point of this stage is that users tick the series/images they want.
