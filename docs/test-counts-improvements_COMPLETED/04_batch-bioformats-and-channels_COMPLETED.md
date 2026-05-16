# Batch mode handles Bio-Formats series and sweeps channels

## Why this stage exists

Batch mode today rejects Bio-Formats containers and only ever runs on the primary channel. A real lab dataset is often a folder of `.lif` or `.czi` files each holding several series, each with several channels. Today's user has to open every series in Fiji, run Test Counts once per channel, and assemble the CSVs by hand. This stage makes batch mode "point at the folder and walk away".

## Prerequisites

- `01_foundation-perf-refactor` complete.
- Depends on stage 01 for the context-aware runner and typed mask path; batch rows should not invent a second threshold execution path.

## Read first

- `src/main/java/macro/builder/analysis/BatchShootoutRunner.java` (whole file)
- `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java:546-638` for the batch UI and file chooser
- `src/main/java/macro/builder/image/BioFormatsSeriesProvider.java:14-103` for existing Bio-Formats series listing/opening
- `src/main/java/macro/builder/analysis/BatchMacroInput.java` for the existing file-vs-container-series input model
- Bio-Formats `ImporterOptions` / `BF` Java API: <https://docs.openmicroscopy.org/bio-formats/latest/developers/java-library.html>
- Existing Bio-Formats use in the codebase: `grep -r "loci.plugins" src/main/java`

## Scope

- Keep `BatchShootoutRunner.collectBatchFiles(List<File>)` as the public file/folder expansion helper (it currently returns `List<File>` and already includes Bio-Formats containers). Add a new internal `collectBatchEntries(...)` that expands each container into one entry per series using the existing `BioFormatsSeriesProvider`. Replace the current "Bio-Formats containers are skipped" rejection in `runOneFile`.
- In the batch dialog, add a "Channels to sweep" multi-select chip group below the existing primary-channel control. Default: only the current primary channel.
- Add `ShootoutSettings.channelsToSweep` as an immutable 1-based channel list. The single-image dialog may keep using the primary channel; batch mode reads this list.
- In the batch runner, iterate (file × series × channel × threshold variant). Emit one CSV row per combination.
- Append two new batch CSV columns after the current base batch columns and before any later score columns: `series_index`, `channel_index`. Direct-image rows leave `series_index` blank; Bio-Formats rows use 0-based series indexes; `channel_index` is always the 1-based Fiji channel number. Existing columns and their order do not change. See `00_overview.md` "CSV column order (cumulative)".
- Per-series open reuses `BioFormatsSeriesProvider.openSeries(...)`. The real Bio-Formats call is `loci.plugins.BF.openImagePlus(ImporterOptions)`, which returns `ImagePlus[]`, not a single image. Configure `ImporterOptions` with `setWindowless(true)`, `setOpenAllSeries(false)`, and `setSeriesOn(i, i == seriesIndex)` for every known series, then keep the first returned `ImagePlus` and close or flush any others. Close the selected `ImagePlus` after its series finishes.
- Progress text shows "file N/M, series S/T, channel C".

## Out of scope

- Heatmap visualisation of the CSV (stage 11 owns that).
- Parallel batch (sequential is fine here; per-file parallelism is a future stage).
- Single-image dialog gets no new behaviour — channels-to-sweep is batch-only.

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/BatchShootoutRunner.java` | MODIFY | Expand Bio-Formats containers into series entries; iterate channels; emit new CSV columns. |
| `src/main/java/macro/builder/analysis/BatchShootoutResult.java` | MODIFY | Add `seriesIndex` and `channelIndex` fields. |
| `src/main/java/macro/builder/analysis/ShootoutSettings.java` | MODIFY | Add immutable `channelsToSweep` list; default to the current primary channel for batch and `[1]` in defaults. |
| `src/main/java/macro/builder/ui/ThresholdShootoutDialog.java` | MODIFY | Channels-to-sweep chip group; updated progress strings. |
| `src/main/java/macro/builder/image/BioFormatsSeriesProvider.java` | MODIFY | Reuse the existing reflective Bio-Formats wrapper; add any count-runner helper needed for opening one series without UI. |
| `src/test/java/macro/builder/image/BioFormatsSeriesProviderTest.java` | MODIFY | Extend existing provider tests for the count-runner path; skip automatically if Bio-Formats is not on the test classpath. |
| `src/test/java/macro/builder/analysis/BatchShootoutRunnerTest.java` | MODIFY | Add a multi-channel synthetic-image case asserting per-channel rows. |
| `src/test/resources/tiny_two_series.ome.tif` | NEW | 64×64×2 channel × 2 series fixture for the reader test (commit a small file). |
| `docs/USER_GUIDE.md` | MODIFY | Update the "Batch Count Testing" section to mention Bio-Formats and per-channel sweep. |

## Implementation sketch

Series expansion:

```java
List<BatchEntry> expand(File f, BioFormatsSeriesProvider seriesProvider) {
    if (isBioFormatsContainer(f)) {
        List<BatchMacroInput> series = seriesProvider.listSeries(f);
        List<BatchEntry> out = new ArrayList<BatchEntry>(series.size());
        for (BatchMacroInput input : series) out.add(BatchEntry.containerSeries(input));
        return out;
    }
    return Collections.singletonList(new BatchEntry(f, -1));
}
```

`BatchEntry` is NEW in this stage unless you decide to reuse `BatchMacroInput` directly.
If direct-image entries use internal `seriesIndex = -1`, the CSV writer converts that sentinel to a blank `series_index` cell.
<!-- audit:agent1 corrected ThresholdShootoutDialog line range and existing BioFormatsSeriesProvider/BatchMacroInput usage instead of inventing BioFormatsSeriesReader -->

Series count and per-series open use the real Bio-Formats APIs:

```java
ImageReader reader = new ImageReader();
reader.setId(container.getAbsolutePath());
int seriesCount = reader.getSeriesCount();
for (int i = 0; i < seriesCount; i++) {
    reader.setSeries(i);
    rows.add(BatchMacroInput.containerSeries(
            container, i, seriesName(reader),
            reader.getSizeX(), reader.getSizeY(), reader.getSizeC(), reader.getSizeZ(), reader.getSizeT()));
}
reader.close(false);

ImporterOptions options = new ImporterOptions();
options.setId(container.getAbsolutePath());
options.setWindowless(true);
options.setOpenAllSeries(false);
for (int i = 0; i < seriesCount; i++) options.setSeriesOn(i, i == seriesIndex);
ImagePlus[] opened = BF.openImagePlus(options);
ImagePlus selected = firstImage(opened);
closeUnselected(opened, selected);
```
<!-- audit:agent2 corrected Bio-Formats openImagePlus return type and series-count API -->

Channel loop inside the runner:

```java
for (int channel : settings.channelsToSweep) {
    // Let ThresholdShootoutRunner own channel duplication so macro/DAG handling
    // stays identical to the single-image path.
    List<ShootoutResult> rows = singleRunner.run(image, macro, settings, channel, progress);
    emitCsvRows(file, seriesIndex, channel, rows);
}
```

Threading model:

- The batch dialog starts this work from its existing `SwingWorker`.
- Bio-Formats series counting/opening, channel iteration, macro execution, and CSV row construction run in the worker.
- Progress and status messages are marshalled back to the EDT through the existing dialog helpers.
- `BatchShootoutRunner` itself stays UI-free and can be exercised from headless tests.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. `BatchShootoutRunnerTest` exercises the new channel-sweep path on a synthetic 3-channel image and asserts one CSV row per selected channel per variant.
3. `BioFormatsSeriesProviderTest` exercises the Bio-Formats count/open path against `tiny_two_series.ome.tif` when Bio-Formats is present, and skips with a clear message when it is not on the test classpath.
4. Selecting one `.lif` file with 3 series and one primary channel produces exactly 3 series groups in the CSV, with `series_index` values `0`, `1`, and `2`.
5. Selecting 5 single-channel TIFFs with channels-to-sweep = `[1, 2, 3]` produces a per-file error row saying `only channel 1 exists in this file` and does not abort the batch.
6. The first 17 batch CSV headers are byte-for-byte identical to the base batch headers in `00_overview.md`; `series_index` and `channel_index` appear immediately after them.
7. Progress text contains `file N/M`, `series S/T`, and `channel C` while the batch is running.
8. Batch CSV headers exactly match `00_overview.md` "CSV column order (cumulative)" through `channel_index` for this stage.

## Known risks

- `loci.plugins.BF` opens a UI dialog by default if `ImporterOptions.setWindowless(true)` is not set. Mitigation: set `setWindowless(true)` in the provider and assert it through the Bio-Formats provider test path.
- ImageJ 1.x and ImageJ2 hybrid Fiji installs can load Bio-Formats classes through different classloaders. Mitigation: keep the reflective `BioFormatsSeriesProvider` boundary, catch `ClassNotFoundException` / linkage errors, and fall back to a clear "Bio-Formats unavailable" row instead of crashing.
- Some Bio-Formats files have hundreds of series. Mitigation: warn in the UI if `series_count > 50` and offer a "first N series" cap before running.
- Bio-Formats may expose virtual disk-backed stacks, where each channel/slice fetch can be slow and may not cache well. Mitigation: process one series/channel at a time, close it immediately, and do not prefetch or retain all slices.
- Memory: closing the per-series `ImagePlus` is mandatory. Mitigation: keep ownership inside a `try/finally` around `BioFormatsSeriesProvider.openSeries(...)`, closing selected and unselected `ImagePlus` instances.
- Locale-dependent CSV writing can emit comma decimals in some JVM locales. Mitigation: write all numeric batch CSV cells with `Locale.ROOT` and cover this in `BatchShootoutRunnerTest`.
- The test fixture file must be small to keep the public repo clean. Mitigation: keep `tiny_two_series.ome.tif` under 100 kB and fail the provider test if the fixture grows beyond that size.
