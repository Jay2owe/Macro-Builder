# Test Counts improvements

## End goal

Test Counts becomes the place where a user goes from "I have a macro and an image" to "I have a defensible count, captured into the macro, with a reproducibility file I can hand a reviewer." No typed threshold numbers, no spreadsheet wrangling, no jargon in the UI.

## Why we're doing this

Today Test Counts is a competent threshold shootout but leaves the user holding three problems: they have to guess threshold values, they have no way to defend "why this method", and the choice they make never makes it back into the macro. This plan closes those gaps without adding any new dependency to the plugin.

## What it will feel like when done

- Open Test Counts on any image, click Run, get a sensible answer with a starred recommendation.
- See a histogram and a count-versus-threshold curve above the table so the user can tell at a glance whether the image is easy or hard to threshold.
- Drop a `RoiSet.zip` (or use ROIs already in the ROI Manager) and every method gets scored against that truth.
- Click "Apply to macro" on the winning row and the choice is written into the loaded macro plus a small JSON file next to the CSV.
- Batch mode handles Bio-Formats containers and sweeps all channels in one run.

## Architecture overview

`ThresholdShootoutDialog` stays the user-facing dialog. `ThresholdShootoutRunner` and `BatchShootoutRunner` keep their roles but grow:

- a faster pixel-access path and a single cached histogram (stage 01),
- an `AutoGrid` mode in `ShootoutSettings` (stage 02),
- new columns on `ShootoutResult` for ground-truth, quality, fragility, agreement (stages 05–08),
- a `TestCountsManifest` writer for the JSON sidecar (stage 09).

```text
image -> duplicate -> macro -> [cached histogram + range]
                                 |
                                 +-> auto grid of thresholds
                                 +-> auto methods
                                 +-> fixed values
                                 |
                                 v
                             masks + counts
                                 |
       +-------------------------+----------------------+
       |             |             |             |     |
   ground-truth   quality       fragility     agreement charts
   scoring        scores        wiggle        vs. consensus
       |             |             |             |     |
       +------+------+------+------+-------------+-----+
                     v
              results table + sidecar
                     v
              "Apply to macro" -> DAG / IJM
```

## Stage map

| NN | name | one-line goal | rough size | depends on |
|---|---|---|---|---|
| 01 | foundation-perf-refactor | Faster pixel access, cached histogram, plugin version stamp. | small-medium | none |
| 02 | auto-threshold-grid | Auto-spaced threshold grid plus plateau pick that stars the recommended row. | medium | 01 |
| 03 | histogram-and-curve-charts | Histogram and count-vs-threshold charts above the results table. | small-medium | 02 |
| 04 | batch-bioformats-and-channels | Batch mode reads Bio-Formats series and sweeps channels in one run. | medium | 01 |
| 05 | ground-truth-scoring | Import ROI truth, score every variant, colour-coded TP/FN/FP mask preview. | medium-large | 01 |
| 06 | quality-score-columns | Two plain-language quality columns (no jargon in the UI). | small | 01 |
| 07 | fragility-bar | Per-row bar showing how much the count moves under small threshold and intensity wiggles. | medium | 02 |
| 08 | method-agreement | Agreement column plus a consensus mask the user can view. | small-medium | 01 |
| 09 | macro-roundtrip-and-sidecar | "Apply to macro" button, JSON sidecar, copy-paste methods paragraph. | medium | 05, 06, 07, 08 |
| 10 | live-threshold-slider | Drag-a-slider live mask preview with a Pin button. | medium-large | 01 |
| 11 | batch-heatmap | Heatmap window opens after a batch run; click to drill in. | small-medium | 04 |
| 12 | click-to-mark-backsolver | User clicks real objects on the source; system reports the variant that catches them. | medium-large | 02 |

Stages 01–09 are the main programme. Stages 10–12 are post-MVP polish; numbered order works as written. If one of stages 10–12 is pulled forward after 09, carry forward the helper called out in that stage's prerequisites instead of creating duplicate implementations.

## House rules

- Bundled Fiji only. No 3D ImageJ Suite, MorphoLibJ, or other update-site dependencies. If an algorithm is small (e.g. Kneedle, multi-Otsu), vendor it as a single file under `src/main/java/macro/builder/analysis/`.
- Source image is never mutated. Every variant runs on a duplicate.
- New CSV columns are additive. Never reorder or rename existing columns; append-only.
- Plain-language UI labels everywhere. "Otsu", "Kneedle", "Jaccard", "F1" do not appear in dialog text. Method names that the user already sees today (Otsu, Triangle, etc.) stay as they are.
- No analysis on the Swing thread. Use `SwingWorker` (existing pattern) or `FilterExecutor.runThreadSafe`.
- Headless-safe paths do not create a `RoiManager` when `GraphicsEnvironment.isHeadless()` is true. Use direct ROI parsing or synthetic `GroundTruthReference` fixtures in true headless tests; use `RoiManager.getInstance2()` only to inspect an already-open manager.
- Each stage must leave the dialog working end-to-end. Half-built features behind a feature flag are fine; broken dialogs are not.
- Tests live alongside existing ones under `src/test/java/macro/builder/...`. New stages add at least one unit test that exercises the new code path on a small synthetic image.
- `.\mvnw.cmd test "-Denforcer.skip=true"` must pass before each stage is marked complete.
- Deploy is local-jar only (see `AGENTS.md`). Do not push to public main or update site as part of this work unless the user says so.

## Known open questions

- For ground-truth scoring (stage 05), default matching rule should be centroid-in-mask for point ROIs and IoU ≥ 0.5 for area ROIs. Confirm with one real dataset before locking in.
- For the JSON sidecar (stage 09), settle on `.testcounts.json` extension and one schema version field. Bump the schema version on every additive change.
- For the live slider (stage 10), 3D stacks need a "which slice does the preview show" decision; default to the active slice and offer a small slice scrubber next to the threshold slider.
- For the back-solver (stage 12), the spread check (so the system can't over-fit to a handful of clicks) needs a concrete rule. Default proposal: variant must catch ≥ 90% of clicks and have a count within ±25% of the median variant count.

## CSV column order (cumulative)

Columns are additive once their stage lands. Keep the order below even when a value is not available; write a blank cell rather than omitting or moving the column.

Single-image CSV:

| # | column | added in stage | populated only when |
|---|---|---|---|
| 1 | Variant | base | always |
| 2 | Count mode | base | always |
| 3 | Threshold value | base | row has a threshold value |
| 4 | Count | base | row succeeded |
| 5 | Mean size | base | row succeeded |
| 6 | Coverage % | base | row succeeded |
| 7 | Range | base | macro-output range is known |
| 8 | Status | base | always |
| 9 | precision | 05 | reference loaded and row succeeded |
| 10 | recall | 05 | reference loaded and row succeeded |
| 11 | f1 | 05 | reference loaded and row succeeded |
| 12 | separation | 06 | row succeeded and has a threshold value |
| 13 | distinctness | 06 | row succeeded and has a threshold value |
| 14 | fragility_score | 07 | `runFragilityChecks` is true and row succeeded |
| 15 | fragility_range_min | 07 | `runFragilityChecks` is true and row succeeded |
| 16 | fragility_range_max | 07 | `runFragilityChecks` is true and row succeeded |
| 17 | agreement_score | 08 | at least 3 successful variants and consensus was not memory-capped |

Batch CSV:

| # | column | added in stage | populated only when |
|---|---|---|---|
| 1 | file | base batch | always |
| 2 | title | base batch | image opened |
| 3 | width | base batch | image opened |
| 4 | height | base batch | image opened |
| 5 | channels | base batch | image opened |
| 6 | slices | base batch | image opened |
| 7 | frames | base batch | image opened |
| 8 | counting_mode | base batch | always |
| 9 | variant | base batch | row succeeded or variant was known |
| 10 | threshold_value | base batch | row has a threshold value |
| 11 | count | base batch | row succeeded |
| 12 | mean_size | base batch | row succeeded |
| 13 | coverage | base batch | row succeeded |
| 14 | range_min | base batch | macro-output range is known |
| 15 | range_max | base batch | macro-output range is known |
| 16 | status | base batch | always |
| 17 | error | base batch | row failed |
| 18 | series_index | 04 | Bio-Formats container row; direct image rows are blank |
| 19 | channel_index | 04 | always for stage 04+ batch rows |
| 20 | precision | 05 | reference loaded and row succeeded |
| 21 | recall | 05 | reference loaded and row succeeded |
| 22 | f1 | 05 | reference loaded and row succeeded |
| 23 | separation | 06 | row succeeded and has a threshold value |
| 24 | distinctness | 06 | row succeeded and has a threshold value |
| 25 | fragility_score | 07 | `runFragilityChecks` is true and row succeeded |
| 26 | fragility_range_min | 07 | `runFragilityChecks` is true and row succeeded |
| 27 | fragility_range_max | 07 | `runFragilityChecks` is true and row succeeded |
| 28 | agreement_score | 08 | at least 3 successful variants and consensus was not memory-capped |

## ShootoutResult field map

New field names are unique. Later stages extend enum values on `source` rather than adding another source/kind field.

| field | type | added in stage | populated only when |
|---|---|---|---|
| countingMode | `ShootoutSettings.CountingMode` | base | always |
| variant | `String` | base | always |
| thresholdLabel | `String` | base | always; currently mirrors `variant` |
| thresholdValue | `Double` | base | threshold value exists |
| imageMinimum | `double` | base | macro-output range is known; otherwise `NaN` |
| imageMaximum | `double` | base | macro-output range is known; otherwise `NaN` |
| maskPreview | `ImagePlus` | base | row succeeded and preview retention is allowed |
| countSummary | `ObjectCounter.CountSummary` | base | row succeeded |
| status | `ShootoutResult.Status` | base | always |
| error | `String` | base | row failed |
| recommended | `boolean` | 02 | true only for the active recommendation |
| recommendationReason | `String` | 02 | `recommended` is true |
| precision | `double` | 05 | reference loaded and row succeeded; otherwise `NaN` |
| recall | `double` | 05 | reference loaded and row succeeded; otherwise `NaN` |
| f1 | `double` | 05 | reference loaded and row succeeded; otherwise `NaN` |
| perObjectStatus | `int[]` | 05 | reference loaded and overlay data exists |
| separationScore | `double` | 06 | row succeeded and has a threshold value; otherwise `NaN` |
| distinctnessScore | `double` | 06 | row succeeded and has a threshold value; otherwise `NaN` |
| fragilityScore | `double` | 07 | fragility checks ran and row succeeded; otherwise `NaN` |
| fragilityCountRange | `int[]` | 07 | fragility checks ran and row succeeded |
| agreementScore | `double` | 08 | consensus was built for at least 3 successful variants; otherwise `NaN` |
| source | `ShootoutResult.Source` | 09 | always after stage 09; values include `AUTO`, `FIXED`, `GRID`, and stage 12 `CLICK_FIT` |

## ShootoutSettings field map

| field | type | added in stage | populated only when |
|---|---|---|---|
| countingMode | `ShootoutSettings.CountingMode` | base | always |
| thresholdMode | `ShootoutSettings.ThresholdMode` | base | always |
| autoMethods | `List<String>` | base | auto methods are enabled; may fall back to defaults |
| fixedThresholds | `List<Double>` | base | fixed thresholds are enabled |
| minSize | `double` | base | always |
| maxSize | `double` | base | always |
| darkBackground | `boolean` | base | always |
| `ThresholdMode.AUTO_GRID` | enum value | 02 | auto-grid mode selected |
| gridSteps | `int` | 02 | auto-grid mode or back-solving needs a grid |
| channelsToSweep | `List<Integer>` | 04 | batch mode; defaults to the primary channel |
| groundTruthReference | `GroundTruthReference` | 05 | reference loaded |
| runFragilityChecks | `boolean` | 07 | always after stage 07; gates fragility work |
| clickPoints | `List<int[]>` | 12 | click-fit run captured points |

## Memory budget

Policy: optional pixel-heavy features must estimate memory before allocation and degrade without breaking the dialog. Use `P = width * height * stackSize` bytes for one 8-bit mask stack. Default caps are 256 MiB for retained optional masks and 64 MiB for interactive previews/rendered heatmaps unless a later stage deliberately adds a user-visible override.

| stage | retained pixel data | worst-case estimate | cap/degrade policy |
|---|---|---|---|
| 01 | context-aware run retains the post-macro processed image for the dialog | `P * bytesPerPixel` for the processed duplicate, plus existing result masks | only context-aware single-image runs retain it; list-only/batch `run(...)` closes it before returning |
| 08 | successful variant masks, one consensus mask, one per-slice vote array | `(successfulVariants + 1) * P + 4 * width * height` bytes | skip agreement/consensus when retained mask estimate exceeds 256 MiB; leave `agreement_score` blank, disable the consensus button with a reason, and allow per-row previews to be unavailable instead of retaining every mask |
| 10 | live preview mask and display image for one active slice or downsampled slice | about `2 * previewWidth * previewHeight` bytes | never build a full-stack live preview; downsample to stay under 64 MiB; Pin still runs the full single-variant path off the EDT |
| 11 | heatmap numeric matrix and rendered image | `8 * rows * columns + 4 * renderedPixels` bytes | keep model/render under 64 MiB by scrolling, row grouping, or downsampling; do not auto-open a giant ungrouped image |
| 11 drill-in | one opened source/series plus one single-variant mask | source image memory plus `P` bytes for the mask | run off the EDT; if the mask estimate exceeds 256 MiB, show a useful "too large for drill-in preview" message |

## How to run a stage

Run `/do-step docs/test-counts-improvements/` to execute the first incomplete numbered stage.

## Audit log — codebase facts (Agent 1)

- `01_foundation-perf-refactor.md`: corrected `FilterExecutor` read-first line refs to `247-285`, `57-58`, and `795-833`.
- `01_foundation-perf-refactor.md`: noted `FilterExecutor.SLICE_PARALLEL_THRESHOLD` exists but is private, so stage 01 cannot reference it directly without exposing a helper.
- `01_foundation-perf-refactor.md`: corrected the ImageJ statistics sketch to `ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null)`.
- `02_auto-threshold-grid.md`: changed `gridThresholds(...)` from private to package-private because stage 12 calls it from `BackSolver`.
- `04_batch-bioformats-and-channels.md`: corrected the batch dialog line range to `ThresholdShootoutDialog.java:546-638`.
- `04_batch-bioformats-and-channels.md`: replaced the planned `BioFormatsSeriesReader` with existing `BioFormatsSeriesProvider` and its existing test path.
- `04_batch-bioformats-and-channels.md`: corrected the `collectBatchFiles(...)` claim; the current method returns `List<File>`, so series expansion needs a new internal entry helper.
- `04_batch-bioformats-and-channels.md`: corrected Bio-Formats opening text to reuse the existing reflective `BioFormatsSeriesProvider.openSeries(...)`.
- `05_ground-truth-scoring.md`: corrected preview references to `ThresholdShootoutDialog.java:502-523` and selected-row state to `781-805`.
- `05_ground-truth-scoring.md`: marked `ObjectCounter.detect(...)` and `DetectedObject` as new stage work because only `ObjectCounter.count(mask, settings)` exists today.
- `05_ground-truth-scoring.md`: corrected the `GroundTruthScorer.score(...)` sketch to accept `ShootoutSettings`.
- `08_method-agreement.md`: corrected preview lifecycle references to `ThresholdShootoutDialog.java:502-523` and `844-854`.
- `09_macro-roundtrip-and-sidecar.md`: corrected `Macro_Builder.java` references for session fields, macro updates, Test Counts hand-off, and state persistence.
- `09_macro-roundtrip-and-sidecar.md`: replaced nonexistent `Macro_Builder.applyMacroEdit(...)` with a new private `SessionDialog.applyMacroEdit(...)` callback path.
- `09_macro-roundtrip-and-sidecar.md`: corrected `DagUndoHistory` usage; it is package-private and exposes `record(DagIR)`, not `push()`.
- `09_macro-roundtrip-and-sidecar.md`: corrected the DAG threshold plan; no `THRESHOLD` op type exists today.
- `09_macro-roundtrip-and-sidecar.md`: replaced nonexistent `variant.isFixed()` with a new `ShootoutResult.Source` field.
- `09_macro-roundtrip-and-sidecar.md`: replaced nonexistent `variant.methodName` with the existing `ShootoutResult.variant` field in the IJM sketch.
- `10_live-threshold-slider.md`: marked `ThresholdShootoutRunner.runOneVariant(...)` as new helper work because the runner currently only exposes `run(...)`.
- `11_batch-heatmap.md`: replaced `BioFormatsSeriesReader` drill-in text with `BioFormatsSeriesProvider`.
- `11_batch-heatmap.md`: marked the heatmap drill-in single-variant runner as new or reused stage 10 work because no public single-variant runner exists today.
- `12_click-to-mark-backsolver.md`: marked `GroundTruthScorer` and `TestCountsManifest` as prior-stage new files instead of current codebase files.
- `12_click-to-mark-backsolver.md`: marked `LiveMaskBuilder.build(...)` as new work and tied the `gridThresholds(...)` call to the corrected package-private helper.

## Audit log — API realism (Agent 2)

- `00_overview.md`: corrected the headless ROI Manager house rule; true Java headless code must not create `RoiManager`, and UI code should inspect existing managers with `RoiManager.getInstance2()`.
- `01_foundation-perf-refactor.md`: corrected the ImageJ statistics sketch to clear the processor ROI before `ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null)`.
- `01_foundation-perf-refactor.md`: corrected the `FloatProcessor` histogram claim; current Fiji exposes `FloatProcessor.getHistogram()`, and `ImageStatistics.getStatistics(...)` does not return null for float processors.
- `02_auto-threshold-grid.md`: replaced the unverified Apache-2 Kneedle attribution with the real single-file `etam4260/kneedle` source and MIT License.
- `04_batch-bioformats-and-channels.md`: corrected Bio-Formats opening to `BF.openImagePlus(ImporterOptions)` returning `ImagePlus[]`, with `setWindowless(true)`, `setOpenAllSeries(false)`, and per-series `setSeriesOn(...)`.
- `04_batch-bioformats-and-channels.md`: corrected Bio-Formats series counting to use `ImageReader.setId(...)`, `getSeriesCount()`, `setSeries(i)`, and size getters without opening every series.
- `05_ground-truth-scoring.md`: corrected ROI Manager headless behavior; `new RoiManager(false)` is hidden but not true-headless safe, so tests should use direct references or `RoiDecoder`.
- `07_fragility-bar.md`: corrected `ImageProcessor.translate(...)` usage; `translate(1.0, 0.0)` is the real non-deprecated call and fills exposed pixels with 0, with no mean-fill option.
- `11_batch-heatmap.md`: corrected the viridis source and license; Bokeh's single-file `Viridis256` table carries the Matplotlib viridis palette under CC0/public domain, while Matplotlib itself is BSD-compatible but not BSD-3-Clause.

## Audit log — sequencing & cross-cutting (Agent 3)

- `00_overview.md`: clarified that stages 10-12 work in numbered order; pulling them forward after stage 09 requires carrying forward the named helper instead of duplicating it.
- `00_overview.md`: added the cumulative single-image and batch CSV column order, with blank cells instead of omitted columns once a stage lands.
- `00_overview.md`: added the `ShootoutResult` field map and noted that later source/kind changes extend the `source` enum instead of adding colliding fields.
- `00_overview.md`: added the `ShootoutSettings` field map, including `channelsToSweep`, `groundTruthReference`, `runFragilityChecks`, and `clickPoints`.
- `00_overview.md`: added the shared memory budget for retained masks, live previews, heatmap rendering, and drill-in previews.
- `01_foundation-perf-refactor.md`: added a `ShootoutRun` result wrapper and ownership rules so stages 03, 10, and 12 can access cached context without breaking existing list-only runner callers.
- `02_auto-threshold-grid.md`: added an explicit threading model for grid generation, plateau detection, star rendering, and clipboard writes.
- `03_histogram-and-curve-charts.md`: added dependencies on stage 01 `ShootoutRun` and stage 02 recommendation fields, plus headless/degraded chart rendering and EDT hand-off rules.
- `04_batch-bioformats-and-channels.md`: added `ShootoutSettings.channelsToSweep`, locked `series_index`/`channel_index` CSV placement, corrected the channel loop to avoid double-duplicating channels, and added batch threading rules.
- `05_ground-truth-scoring.md`: added the stage 02 recommendation dependency, changed ground-truth CSV columns to stable blanks when no reference is loaded, and specified background parsing/scoring plus true-headless behavior.
- `06_quality-score-columns.md`: added the stage 05 dependency, clarified that quality does not replace prior recommendation decisions, locked CSV placement, and added threading rules.
- `07_fragility-bar.md`: added stage 06/stage 01 dependencies, locked fragility CSV placement, and specified worker-side probing with an EDT-only renderer.
- `08_method-agreement.md`: added stage 07/stage 01 dependencies, corrected agreement scoring to use leave-one-out votes without per-row consensus stacks, added the shared memory cap, and locked CSV/headless behavior.
- `09_macro-roundtrip-and-sidecar.md`: added the stage 01 plugin-version dependency plus threading and failure-posture rules for macro edits, sidecar hashing/writes, and clipboard/status updates.
- `10_live-threshold-slider.md`: added stage 09/stage 01 dependencies, tied the scrubber to the retained post-macro image lifecycle, added live-preview memory degradation, and moved rebuild/count work off the EDT.
- `11_batch-heatmap.md`: added stage 09/stage 10 dependency handling, required header-name CSV parsing, disabled auto-open in headless mode, added memory degradation, and specified worker-side parsing/drill-in.
- `12_click-to-mark-backsolver.md`: added dependencies on stages 05, 09, 10, and stage 01 processed context, plus headless pure back-solving and EDT-safe click listener lifecycle rules.

## Audit log — exit gates & risks (Agent 4)

- `01_foundation-perf-refactor.md`: made the exit gate require all tests, typed byte/short/float mask-path tests, a 2048x2048x8 timing check, concrete base-column UI verification, and mitigations for virtual stacks, non-finite float pixels, large retained images, unsupported processors, and manifest fallback.
- `02_auto-threshold-grid.md`: made the exit gate require PlateauFinder and AUTO_GRID runner tests, exact no-plateau and clipboard checks, below-6-step warning verification, and mitigations for epsilon tuning, short grids, mixed grid/auto rows, and locale-safe threshold formatting.
- `03_histogram-and-curve-charts.md`: made chart gates quantitative, added a manual smoke check for chart rendering/resizing/fallback, and added mitigations for Plot image cleanup, 16-bit bin labelling, zero-span ranges, HiDPI sizing, and avoiding pixel rescans.
- `04_batch-bioformats-and-channels.md`: made batch gates verify channel-sweep tests, Bio-Formats test skipping, exact series/channel CSV headers, progress text, and mitigations for Bio-Formats UI prompts, IJ1/ImageJ2 classloaders, virtual stacks, memory ownership, locale-safe CSV, and fixture size.
- `05_ground-truth-scoring.md`: made gates verify loader/scorer tests, score precision, overlay pixels, no-reference blanks, added a manual smoke check for ROI Manager/import/preview/clear paths, and added mitigations for IoU ambiguity, point-boundaries, 16-bit labels, large overlays, HiDPI previews, locale CSV, and headless ROI Manager use.
- `06_quality-score-columns.md`: made gates verify HistogramQualityScorer coverage, numeric score thresholds, no rerun on column toggles, locale-safe CSV output, and mitigations for score normalisation, non-finite float histograms, empty histograms, and user-guide explanation.
- `07_fragility-bar.md`: made gates verify FragilityProbe coverage, bounded extra mask builds, quantified renderer bar widths, no-cost disabled mode, locale-safe CSV, and mitigations for translate edge fill, virtual-stack slowness, large-image memory, non-finite float jitter, and confidence-interval confusion.
- `08_method-agreement.md`: made gates verify ConsensusMaskBuilder coverage, exact one-window behaviour, fewer-than-3 blanking, locale-safe CSV, and mitigations for agreement-vs-correctness, retained-mask caps, very large images, virtual stacks, and failed rows.
- `09_macro-roundtrip-and-sidecar.md`: made gates verify MacroApplier, manifest escaping, and methods paragraph tests, exact macro/JSON outputs, locale-safe thresholds, and mitigations for ambiguous DAG insertion, Fiji/classloader version fallback, JSON and IJM string escaping, async hashing, and sidecar write failures.
- `10_live-threshold-slider.md`: made live-slider gates verify LiveMaskBuilder coverage, fps/latency/window lifecycle/pin behaviour, added a manual smoke check for scrubbing, slice changes, pinning, closing, and downsampling, and added mitigations for short 16-bit ranges, non-finite floats, virtual stacks, large previews, HiDPI positioning, worker/EDT separation, and slider recursion.
- `11_batch-heatmap.md`: made heatmap gates verify model and renderer tests, exact auto-open/toggle/drill-in/preferences/headless behaviour, added a manual smoke check for metrics, normalisation, drill-in, resize, and second-monitor use, and added mitigations for huge batches, render memory, missing files, IJ1/ImageJ2 classloaders, virtual drill-in, locale CSV parsing, HiDPI alignment, and viridis provenance.
- `12_click-to-mark-backsolver.md`: made click-fit gates verify BackSolver and manifest tests, listener-count cleanup, source badge/star behaviour, headless purity, added a manual smoke check for click capture/cancel/3D sidecar/close cleanup, and added mitigations for multi-canvas capture, z recording, double-clicks, virtual stacks, large masks, non-finite floats, HiDPI coordinates, over-fit tuning, and spread-check fallback.
