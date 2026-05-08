# Stage 04 — Parallel variant executor

## Why this stage exists

Each `VariantPlan` from stage 01 is a `DagIR` ready to execute. With N variants, we need to run N pipelines on (a possibly-cropped) source image and collect N result `ImagePlus` objects to display in the grid window. ImageJ's macro interpreter is thread-safe per-thread since 1.41f, and `FilterExecutor.runDagThreadSafe` is documented as not touching `WindowManager` — so a fixed parallel pool over native-tier DAGs is safe. Legacy-tier DAGs (with `commandName != ""`) must serialize through `WindowManagerLock`. This stage builds the executor that handles both paths and reports progress.

## Prerequisites

- Stage 01 completed (`VariantPlan` value type exists).

## Read first

- `docs/create-macro-variations/00_overview.md`
- `docs/create-macro-variations/01_dag-mutations-and-sampler.md` (for `VariantPlan` shape)
- `src/main/java/macro/builder/image/FilterExecutor.java` lines 480–512 (`runDagThreadSafe`)
- `src/main/java/macro/builder/image/FilterExecutor.java` lines 270–320 (`WindowManagerLock` usage for legacy path)
- `src/main/java/macro/builder/image/FilterExecutor.java` lines 813–849 (existing `ParallelContext.enterParallel` pattern)
- `src/main/java/macro/builder/image/FilterExecutor.java` lines 640–700 (`cloneChannelStack` / `duplicateChannel` — model for cloning `ImagePlus` per task)
- `src/main/java/macro/builder/image/dag/DagRejectedException.java`

## Scope

- New `VariantExecutor` class with one public method:
  - `List<VariantResult> runAll(ImagePlus source, List<VariantPlan> plans, ProgressCallback progress)` — runs every plan, returns a result per plan in the same order. Each result holds the variant's `ImagePlus` output OR an error.
- New `VariantResult` value type: `VariantPlan plan`, `ImagePlus output` (nullable), `Throwable error` (nullable, mutually exclusive with `output`), `long elapsedMillis`.
- New `ProgressCallback` interface: `onStart(int total)`, `onVariantComplete(int completed, int total, VariantResult result)`, `onAllDone(List<VariantResult> results)`. EDT-safe (calls go through `SwingUtilities.invokeLater`).
- Execution policy:
  - Inspect `plans.get(0).dag.executionTier`. If any plan has tier `"legacy"`, fall back to **serial** execution (one variant at a time, holding `WindowManagerLock` per run). If all are `"native"`, use a parallel pool.
  - Pool size: `Math.min(plans.size(), Runtime.getRuntime().availableProcessors())`.
  - Each parallel task: clone the source `ImagePlus` (via `source.duplicate()`), set `ParallelContext.enterParallel()` for the task duration, call `FilterExecutor.runDagThreadSafe(clone, plan.dag)`, capture result.
  - Catch `DagRejectedException` and any `Throwable` per task → `VariantResult.error`. One variant failing must not crash others.
- Cancellation: `runAll` returns a `Future`-like handle? **No — keep v1 synchronous.** The caller (stage 05's dialog) blocks on it inside a `SwingWorker.doInBackground`. Cancellation can be added in v2.

## Out of scope

- The dialog showing the progress bar — stage 05.
- The grid window that consumes the results — stage 06.
- Any caching of intermediate node outputs across variants — Tier 2.
- Time-lapse representative-frame selection — stage 05 owns that policy.

## Files touched

| Path | NEW / MODIFY | Reason |
|------|--------------|--------|
| `src/main/java/macro/builder/image/variation/VariantExecutor.java` | NEW | Parallel/serial dispatcher |
| `src/main/java/macro/builder/image/variation/VariantResult.java` | NEW | Value type per-variant output or error |
| `src/main/java/macro/builder/image/variation/ProgressCallback.java` | NEW | Listener interface |
| `src/test/java/macro/builder/image/variation/VariantExecutorTest.java` | NEW | Round-trip on a tiny synthetic DAG; error-isolation; legacy serial fallback |

## Implementation sketch

```java
// VariantExecutor.java
public final class VariantExecutor {

    public static List<VariantResult> runAll(ImagePlus source, List<VariantPlan> plans, ProgressCallback progress) {
        boolean anyLegacy = plans.stream().anyMatch(p -> "legacy".equals(p.dag.executionTier));
        if (progress != null) progress.onStart(plans.size());
        List<VariantResult> results = anyLegacy
            ? runSerial(source, plans, progress)
            : runParallel(source, plans, progress);
        if (progress != null) progress.onAllDone(results);
        return results;
    }

    private static List<VariantResult> runParallel(ImagePlus source, List<VariantPlan> plans, ProgressCallback progress) {
        int pool = Math.min(plans.size(), Runtime.getRuntime().availableProcessors());
        ExecutorService exec = Executors.newFixedThreadPool(pool);
        List<Future<VariantResult>> futures = new ArrayList<>();
        for (VariantPlan plan : plans) {
            futures.add(exec.submit(() -> runOne(source, plan, /*holdLock*/ false)));
        }
        List<VariantResult> out = collect(futures, plans.size(), progress);
        exec.shutdown();
        return out;
    }

    private static List<VariantResult> runSerial(ImagePlus source, List<VariantPlan> plans, ProgressCallback progress) {
        List<VariantResult> out = new ArrayList<>();
        int done = 0;
        for (VariantPlan plan : plans) {
            VariantResult r = runOne(source, plan, /*holdLock*/ true);
            out.add(r);
            done++;
            if (progress != null) {
                final int d = done;
                SwingUtilities.invokeLater(() -> progress.onVariantComplete(d, plans.size(), r));
            }
        }
        return out;
    }

    private static VariantResult runOne(ImagePlus source, VariantPlan plan, boolean holdLock) {
        long start = System.currentTimeMillis();
        try {
            ImagePlus clone = source.duplicate();   // independent ImagePlus per variant
            // for legacy: synchronized (WindowManagerLock.LOCK) { ... } around the call
            ParallelContext.enterParallel();
            try {
                ImagePlus out = FilterExecutor.runDagThreadSafe(clone, plan.dag);
                return new VariantResult(plan, out, null, System.currentTimeMillis() - start);
            } finally {
                ParallelContext.exitParallel();
            }
        } catch (Throwable t) {
            return new VariantResult(plan, null, t, System.currentTimeMillis() - start);
        }
    }
}
```

**`ParallelContext.enterParallel()` semantics.** Read `FilterExecutor.java` lines 813–849 to confirm: this is the existing pattern for nested-pool collapse (the inner per-slice pool in `runDagThreadSafe` becomes serial when called from inside an outer parallel context). Calling it per-task means each variant runs its inner work serially, while the outer pool runs N variants in parallel. This is the right tradeoff — N small parallel groups beat N×(N×slices) total threads.

**Why duplicate per variant.** `runDagThreadSafe` itself doesn't mutate the source `ImagePlus` (it clones channels internally), but `source.duplicate()` is cheap insurance against any code path that might. It also gives the caller (stage 06 grid window) clear ownership of the result `ImagePlus` per tile.

## Exit gate

1. `mvn test -Dtest=VariantExecutorTest` passes.
2. Test coverage (use a tiny synthetic DAG, e.g. single `GAUSSIAN_BLUR` line with sigma=1, on a 64×64×3 stack so tests run in <100ms):
   - `runAll` with 4 valid plans returns 4 results in the same order, all with non-null `output`.
   - `runAll` with one plan that has an invalid DAG (e.g. unknown OpType) → that plan's result has `error != null`, others succeed.
   - Serial fallback: a plan with `executionTier == "legacy"` (synthesise by giving a node a non-empty `commandName`) → all plans run serially. Test by checking total wall time ≈ sum of individual times (parallel would be much less).
   - Parallel speedup: 4 plans on 4 available processors run in ≈ wall time of one plan, not 4× one plan. (Loose bound — assert `total < 2× single`.)
   - `ProgressCallback.onVariantComplete` fires N times, on EDT (assert via `SwingUtilities.isEventDispatchThread()` inside the callback).
3. `mvn compile` produces no new warnings.
4. Manual smoke from a small test main: load a real Fiji image, run 4 variants of a Gaussian sigma sweep, confirm 4 distinct `ImagePlus` results returned with different blur levels.

## Known risks

- `source.duplicate()` on a multi-GiB hyperstack costs the full source size per variant. The estimator from stage 03 already accounts for this (the 1.3× factor). On very tight heap, the executor will OOM — that's why stage 05 forces ROI mode before invoking the executor.
- `ParallelContext.enterParallel()` is thread-local. Make sure `exitParallel()` runs in `finally` even when the inner call throws.
- Some `OpType` ops may use `IJ.run(imp, ...)` internally rather than pure-processor APIs. Read `FilterExecutor.executeOpOnSlice` and `executeOpOnStack` carefully — if any covered op makes `IJ.run` calls, those *do* require either thread-binding (which they get for free per IJ 1.41f) or `WindowManagerLock`. The existing native path is documented as safe; trust it but verify with the parallel test above.
- Catch `OutOfMemoryError` per-task. Letting it propagate up the executor crashes the pool. `runOne`'s `catch (Throwable t)` already covers it; make sure the test asserts an OOM in one variant doesn't kill the others.
