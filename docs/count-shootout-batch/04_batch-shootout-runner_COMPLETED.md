# Batch shootout runner

## Why this stage exists

Single-image results can be misleading if the macro only works on one example. This stage reuses the same settings and runner across a user-selected batch so users can see how the current macro behaves across their real dataset.

## Prerequisites

- `01_count-model-native-counter.md` completed and renamed with `_COMPLETED`.
- `02_threshold-shootout-runner.md` completed and renamed with `_COMPLETED`.

## Read first

- `docs/count-shootout-batch/00_overview.md`
- `AGENTS.md`
- `src/main/java/macro/builder/Macro_Builder.java:178-214` for supported direct image/container opening.
- `src/main/java/macro/builder/Macro_Builder.java:214-273` for Bio-Formats chooser behavior.
- `src/main/java/macro/builder/Macro_Builder.java:421-440` for duplicate run behavior.
- `src/main/java/macro/builder/image/FilterExecutor.java:246-296` for macro execution.

## Scope

- Add `BatchShootoutRunner`.
- Add file/folder selection from the count test workflow.
- For ordinary image files and TIFF stacks, open each image, run the same `ThresholdShootoutRunner`, and collect rows.
- Write one CSV with one row per file plus threshold variant.
- Keep batch progress visible and cancellable enough for normal desktop use.
- Close batch images and masks after each file.

## Out of scope

- Project-specific importers or lab folder conventions.
- Multi-series Bio-Formats batch selection automation unless it is trivial and safe.
- Saved batch macro export; stage 05 owns that.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/BatchShootoutRunner.java` | NEW | Batch application of the single-image shootout. |
| `src/main/java/macro/builder/analysis/BatchShootoutResult.java` | NEW | Adds file path/title context around `ShootoutResult`. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Add batch run action, progress/status, and batch CSV export. |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Expose or share safe image-opening helpers if needed. |

## Implementation sketch

Runner shape:

```java
public final class BatchShootoutRunner {
    public List<BatchShootoutResult> run(
            List<File> files,
            String macro,
            ShootoutSettings settings,
            Progress progress);
}
```

CSV columns:

```text
file,title,width,height,channels,slices,frames,counting_mode,variant,threshold_value,count,mean_size,coverage,range_min,range_max,status,error
```

Start with direct image formats already listed in `Macro_Builder.java`:

```text
tif,tiff,png,jpg,jpeg,gif,bmp,ics,ids
```

Bio-Formats containers can be skipped with a row-level error unless the user chose them one at a time through the existing chooser.

## Exit gate

1. `.\mvnw.cmd test "-Denforcer.skip=true"` passes.
2. Manual: run a batch of at least two ordinary TIFF or PNG files and produce a CSV.
3. Manual: a failed image produces an error row without stopping the remaining batch.
4. Manual: after the batch, Fiji does not have a trail of unclosed batch images or mask windows.

## Known risks

- Running legacy macros in a batch may be slow because `FilterExecutor` must lock ImageJ window state. Preserve correctness over parallel speed.
- Bio-Formats containers can contain multiple series. Do not silently choose a series for a whole batch unless the UI clearly asks for that rule.
