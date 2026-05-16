# Foundation: faster pixel access, cached histogram, version stamp

## Why this stage exists

Every later stage adds passes over the image (more grid points, fragility wiggles, agreement IoU, live-slider updates). The current runner scans the stack three times per shootout in boxed-double precision and rebuilds the histogram on every call. Fixing the data layer first means every other stage stays fast. The plugin version stamp lands here too because the sidecar in stage 09 needs it.

## Prerequisites

None.

## Read first

- `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` (whole file)
- `src/main/java/macro/builder/analysis/ObjectCounter.java`
- `src/main/java/macro/builder/image/FilterExecutor.java:247-285` for `runThreadSafe(...)`
- `src/main/java/macro/builder/image/FilterExecutor.java:57-58` and `:795-833` for the existing private slice-parallel threshold and slice executor pattern
- `pom.xml` for build properties
- `AGENTS.md`

## Scope

- Replace `measureRange` and `buildHistogram` in `ThresholdShootoutRunner` with a cached statistics path. Clear any processor ROI first, then use `ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null)` for range and `ImageProcessor.getHistogram()` for byte/short/RGB histograms. `FloatProcessor.getHistogram()` exists in current Fiji, but a manual float pass is still fine if you need exact min/max-to-bin control.
- Specialise `createMask` per processor type (byte, short, float) using raw pixel arrays. Keep the existing double-precision path as a fallback only.
- Cache the histogram and range on a per-shootout context object so every threshold variant reuses them.
- Expose the cached context through a small immutable run result, for example `ShootoutRun { ShootoutContext context; List<ShootoutResult> results; }`. Keep the existing `run(...)` methods as wrappers that return only the list so current callers do not break.
- Define ownership clearly: context-aware runs transfer the processed post-macro `ImagePlus` to the caller, and `ThresholdShootoutDialog` closes it on dialog close or before the next run. The old list-only `run(...)` path still closes the processed image before returning so batch callers do not retain it accidentally.
- Add a plugin version string sourced from the Maven `${project.version}` at build time. Expose it via `Macro_Builder.getPluginVersion()` for later stages.
- Run the mask-build per Z-slice in parallel using the same threshold value as `FilterExecutor.SLICE_PARALLEL_THRESHOLD` (currently private in `FilterExecutor`), or expose a small helper before referencing it directly.
<!-- audit:agent1 corrected FilterExecutor line ranges, private SLICE_PARALLEL_THRESHOLD access, and ImageStatistics API signature -->

## Out of scope

- Changing any user-visible UI.
- Adding new columns or new threshold modes.
- Touching the batch runner (stage 04 will benefit automatically).

## Files touched

| path | action | reason |
|---|---|---|
| `src/main/java/macro/builder/analysis/ThresholdShootoutRunner.java` | MODIFY | Replace per-pixel double scans with typed raw-array access; add a `ShootoutContext` holding cached histogram and range. |
| `src/main/java/macro/builder/analysis/ShootoutContext.java` | NEW | Small value class: cached histogram, range, processed `ImagePlus`. |
| `src/main/java/macro/builder/analysis/ShootoutRun.java` | NEW | Immutable value returned by a new context-aware runner method; keeps `ShootoutContext` available to stages 03, 06, 07, 10, and 12 without shared mutable runner state. |
| `src/main/java/macro/builder/Macro_Builder.java` | MODIFY | Add static `getPluginVersion()` reading from the manifest. |
| `pom.xml` | MODIFY | Add `<Implementation-Version>` to the jar manifest if not present. |
| `src/test/java/macro/builder/analysis/ThresholdShootoutRunnerTest.java` | MODIFY | Add a 16-bit synthetic-stack test that asserts mask correctness against a known-good reference. |
| `src/test/java/macro/builder/MacroBuilderVersionTest.java` | NEW | Asserts `getPluginVersion()` returns a non-empty string. |

## Implementation sketch

Context object:

```java
final class ShootoutContext {
    final ImagePlus processed;
    final int[] histogram;
    final double rangeMin;
    final double rangeMax;
    final boolean isFloat;
}
```

`ShootoutContext.processed` is owned by the returned `ShootoutRun`; callers must close or flush it. This is required by stages 10 and 12 for live preview/back-solving.

Cached histogram + range:

```java
ImageProcessor ip = processed.getProcessor();
ip.resetRoi();
ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null);
if (!isFloat(ip)) {
    int[] h = ip.getHistogram();  // uses raw int[] / short[] / byte[] internally
    return new ShootoutContext(processed, h, stats.min, stats.max, false);
}
// Float fallback is optional: FloatProcessor.getHistogram() returns 256 bins,
// but a manual pass can keep the binning tied exactly to stats.min..stats.max.
```
<!-- audit:agent2 corrected ImageJ statistics ROI clearing and FloatProcessor histogram facts -->

Mask builder, specialised:

```java
static ByteProcessor maskFromShort(ShortProcessor src, double lower, double upper) {
    short[] in = (short[]) src.getPixels();
    byte[] out = new byte[in.length];
    int lo = (int) Math.max(0, Math.ceil(lower));
    int hi = (int) Math.min(65535, Math.floor(upper));
    for (int i = 0; i < in.length; i++) {
        int v = in[i] & 0xffff;
        if (v >= lo && v <= hi) out[i] = (byte) 255;
    }
    return new ByteProcessor(src.getWidth(), src.getHeight(), out, null);
}
```

Version stamp:

```java
public static String getPluginVersion() {
    Package pkg = Macro_Builder.class.getPackage();
    String v = pkg == null ? null : pkg.getImplementationVersion();
    return v == null ? "dev" : v;
}
```

Threading model:

- `ThresholdShootoutDialog` still starts the runner from its existing `SwingWorker`.
- Histogram/range collection and typed mask building run inside that worker; per-slice mask loops may use the same bounded worker pattern as `FilterExecutor`, but no Swing objects are touched there.
- EDT work is limited to progress updates already marshalled through `FilterExecutor.Progress` and final table updates in the dialog.

## Exit gate

1. All existing and new tests pass with `.\mvnw.cmd test "-Denforcer.skip=true"`.
2. Existing `ThresholdShootoutRunnerTest` and `BatchShootoutRunnerTest` still pass without changing their pre-stage assertions.
3. A new or updated `ThresholdShootoutRunnerTest` exercises the typed raw-array mask path on byte, short, and float synthetic images and asserts counts match the old double-precision fallback.
4. A new performance test runs a sweep on a 2048x2048x8 synthetic 16-bit stack in under 2 seconds on a developer laptop.
5. `Macro_Builder.getPluginVersion()` returns the version from the jar manifest when launched from a built jar; `MacroBuilderVersionTest` asserts the unit-test fallback returns `"dev"` or a non-empty version string.
6. Opening Test Counts still shows no new controls or buttons; a single-image run still shows only the base result columns `Variant`, `Count mode`, `Threshold value`, `Count`, `Mean size`, `Coverage %`, `Range`, and `Status`.
7. The new context-aware runner path leaves the old `run(...)` API source-compatible for current dialog and batch callers; this is verified by compiling the unchanged current callers.
8. A unit test proves the list-only `run(...)` path still closes the processed duplicate, while the context-aware path leaves it available until the caller closes it.

## Known risks

- Float processors may contain `NaN`, `Infinity`, or `-Infinity`. Mitigation: treat non-finite pixels as background for mask creation, exclude them from range/histogram bins, and cover this in `ThresholdShootoutRunnerTest`.
- Raw-array access on virtual stacks can be slow because each slice fetches from disk and may not cache well. Mitigation: keep the existing per-slice loop, never materialise the full virtual stack, and add a virtual-stack smoke test that fetches each slice at most once per variant.
- Very large images can blow memory if the context-aware path retains too much data. Mitigation: retain only the processed duplicate for context-aware single-image runs, close it on dialog close or rerun, and keep list-only/batch paths closing it before return.
- `ImageStatistics.getStatistics(...)` can throw for unsupported pixel arrays. Mitigation: keep the existing double-precision fallback and add a float-stack unit test because float binning is easy to get subtly wrong.
- The Maven manifest property may not be set in tests run via the IDE. Mitigation: `getPluginVersion()` returns `"dev"` when the manifest value is absent, and the test accepts `"dev"` or a version string.
