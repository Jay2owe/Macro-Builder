# Macro batch runner

## Why this stage exists

After the scanner can describe selected inputs, Macro Builder needs a non-UI execution engine that can run the loaded macro repeatedly and save outputs. Keeping this separate from Swing makes it testable and lets folder mode and container mode share the same runner.

## Prerequisites

- `01_batch-model-and-folder-scan.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/run-as-batch-macro/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/image/FilterExecutor.java:60-72` for the existing progress callback contract.
- `src/main/java/macro/builder/image/FilterExecutor.java:107-180` for legacy macro sandbox cleanup.
- `src/main/java/macro/builder/image/FilterExecutor.java:250-310` for `runThreadSafe(...)` behavior and fallback to locked legacy macro execution.
- `src/main/java/macro/builder/Macro_Builder.java:718-740` for the single-image duplicate run flow.
- `src/main/java/macro/builder/Macro_Builder.java:1231-1252` for duplicate and close helpers.
- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java:35-62` for batch progress callback structure.
- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java:153-195` for per-file failure isolation and cleanup.
- `src/test/java/macro/builder/analysis/BatchShootoutRunnerTest.java:29-88` for testing success rows and failure rows.
- `src/test/java/macro/builder/analysis/BatchShootoutRunnerTest.java:160-168` for ImageJ TIFF saving and open-window counting helpers.

## Scope

- Add `BatchMacroRunner`.
- Run ordinary file inputs from `BatchMacroInput.Kind.FILE`.
- Open each file with `IJ.openImage(...)`.
- Run the loaded macro with `FilterExecutor.runThreadSafe(...)`.
- Save each processed result as TIFF.
- Write a CSV summary helper.
- Keep progress visible through a runner callback.
- Support cancellation between files.
- Continue after per-file failures and record failed result rows.
- Close source/result images after each file.

## Out of scope

- Any Swing dialog. Stage 03 owns UI.
- Bio-Formats container opening. Stage 04 owns `CONTAINER_SERIES`.
- Parallel execution. Legacy ImageJ macros can touch global window state, so this stage should run sequentially.
- Count CSV output. This stage writes macro-run output rows only.
- Output formats other than TIFF.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/BatchMacroRunner.java` | NEW | Executes loaded macros over batch inputs and saves image outputs. |
| `src/main/java/macro/builder/analysis/BatchMacroResult.java` | MODIFY | Add fields or factories needed by runner output rows. |
| `src/main/java/macro/builder/analysis/BatchMacroInput.java` | MODIFY | Add display-name or output-name helpers if useful. |
| `src/test/java/macro/builder/analysis/BatchMacroRunnerTest.java` | NEW | Verifies ordinary file execution, output saving, failure isolation, CSV, and window cleanup. |

## Implementation sketch

Runner shape:

```java
public final class BatchMacroRunner {
    public interface Progress {
        void onStarted(int totalItems);
        void onItemStarted(BatchMacroInput input, int index, int totalItems);
        void onItemFinished(BatchMacroInput input, int index, int totalItems, BatchMacroResult result);
        boolean isCancelled();
    }

    public List<BatchMacroResult> run(
            List<BatchMacroInput> inputs,
            String macro,
            File outputDirectory,
            Progress progress) {
        // Validate macro and output directory.
        // Iterate in order.
        // Stop before starting the next item when progress.isCancelled().
    }

    public static String buildCsv(List<BatchMacroResult> results) { ... }
}
```

CSV columns should be macro-output specific, not count-specific:

```text
source,kind,series_index,series_name,width,height,channels,slices,frames,output,status,error
```

Per-file execution skeleton:

```java
ImagePlus image = null;
try {
    image = IJ.openImage(input.file.getAbsolutePath());
    if (image == null || image.getStack() == null) {
        return BatchMacroResult.failure(input, "Fiji could not open this image file.");
    }

    FilterExecutor.runThreadSafe(image, macro, progressAdapter);

    File out = outputFileFor(input, outputDirectory);
    saveTiff(image, out);
    return BatchMacroResult.success(input, out, image);
} catch (RuntimeException ex) {
    return BatchMacroResult.failure(input, cleanMessage(ex));
} finally {
    closeImageQuietly(image);
}
```

TIFF saving should preserve stacks:

```java
private static void saveTiff(ImagePlus image, File out) {
    File parent = out.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
        throw new IllegalStateException("Could not create output folder: " + parent.getAbsolutePath());
    }
    FileSaver saver = new FileSaver(image);
    boolean ok = image.getStackSize() > 1
            ? saver.saveAsTiffStack(out.getAbsolutePath())
            : saver.saveAsTiff(out.getAbsolutePath());
    if (!ok) throw new IllegalStateException("Could not save TIFF: " + out.getAbsolutePath());
}
```

Output naming:

```text
input:  folder/sub/image.tif
output: output/folder/sub/image_MacroBuilder.tif
```

Use a sanitizing helper for filenames:

```java
private static String safeBaseName(String name) {
    // Strip extension, replace characters outside [A-Za-z0-9._-] with "_".
}
```

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. A unit test saves at least one processed TIFF output for an ordinary TIFF input.
3. A unit test verifies a broken image creates one failed row and does not stop a later valid image.
4. A unit test verifies CSV output contains source path, output path, status, and error columns.
5. A unit test verifies the open ImageJ window count is unchanged after the batch.

## Known risks

- Some user macros may save or close their own windows. `FilterExecutor` already has a legacy macro sandbox; use it through `runThreadSafe(...)` and keep runner cleanup defensive.
- Avoid duplicate images unless necessary. `IJ.openImage(...)` already creates a per-file image that can be modified and saved without touching the selected source image.
- FileSaver can return `false` without throwing. Check the boolean and turn it into a failed row or exception.
