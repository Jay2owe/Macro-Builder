package macro.builder.image.variation;

import ij.ImagePlus;
import macro.builder.image.FilterExecutor;
import macro.builder.image.ParallelContext;
import macro.builder.image.WindowManagerLock;
import macro.builder.image.dag.DagRejectedException;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs a list of {@link VariantPlan}s against a source {@link ImagePlus} and
 * collects one {@link VariantResult} per plan.
 *
 * <p>Execution policy:
 * <ul>
 *   <li>If <em>any</em> plan has {@code executionTier == "legacy"}, every plan
 *       runs serially under {@link WindowManagerLock}. Legacy DAGs touch
 *       global ImageJ state and cannot share a thread pool.</li>
 *   <li>If <em>all</em> plans are native, plans run in a fixed-size pool of
 *       {@code min(plans.size(), Runtime.availableProcessors())} workers.</li>
 * </ul>
 *
 * <p>Each task duplicates the source so workers cannot interfere via shared
 * pixel arrays, and enters {@link ParallelContext} so the inner per-slice
 * pool inside {@link FilterExecutor#runDagThreadSafe} collapses to serial.
 *
 * <p>Per-task failures (including {@link DagRejectedException} and
 * {@link OutOfMemoryError}) are captured into {@code VariantResult.error}
 * so one bad plan never crashes the others.
 */
public final class VariantExecutor {

    /** Keep variation generation responsive on desktop Fiji; each worker still creates large image outputs. */
    static final int MAX_NATIVE_WORKERS = 2;

    private VariantExecutor() {}

    /**
     * Run all plans, stopping cooperatively if the caller thread is interrupted.
     *
     * <p>Cancellation is checked before starting the next serial variant and
     * before collecting the next parallel result. Work already inside
     * {@link FilterExecutor#runDagThreadSafe} may finish one current variant
     * before stopping because individual ImageJ operations do not all honour
     * thread interruption. On cancellation this method calls
     * {@link ProgressCallback#onCancelled()} instead of
     * {@link ProgressCallback#onAllDone(List)} and returns only complete
     * {@link VariantResult}s collected before the cancellation boundary.
     */
    public static List<VariantResult> runAll(ImagePlus source,
                                             List<VariantPlan> plans,
                                             ProgressCallback progress) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (plans == null) throw new IllegalArgumentException("plans must not be null");
        final int total = plans.size();
        notifyStart(progress, total);

        List<VariantResult> empty = new ArrayList<VariantResult>();
        if (Thread.currentThread().isInterrupted()) {
            notifyCancelled(progress);
            return empty;
        }
        if (total == 0) {
            notifyAllDone(progress, empty);
            return empty;
        }

        boolean anyLegacy = false;
        for (VariantPlan plan : plans) {
            if (plan != null && "legacy".equals(plan.dag.executionTier)) {
                anyLegacy = true;
                break;
            }
        }
        ExecutionResult execution = anyLegacy
                ? runSerial(source, plans, progress)
                : runParallel(source, plans, progress);
        if (execution.cancelled) {
            notifyCancelled(progress);
        } else {
            notifyAllDone(progress, execution.results);
        }
        return execution.results;
    }

    private static ExecutionResult runSerial(ImagePlus source,
                                             List<VariantPlan> plans,
                                             ProgressCallback progress) {
        final int total = plans.size();
        List<VariantResult> out = new ArrayList<VariantResult>(total);
        int done = 0;
        for (VariantPlan plan : plans) {
            if (Thread.currentThread().isInterrupted()) {
                return ExecutionResult.cancelled(out);
            }
            VariantResult r = runOne(source, plan, true);
            if (isCancellationResult(r)) {
                Thread.currentThread().interrupt();
                return ExecutionResult.cancelled(out);
            }
            out.add(r);
            done++;
            notifyComplete(progress, done, total, r);
            if (Thread.currentThread().isInterrupted()) {
                return ExecutionResult.cancelled(out);
            }
        }
        return ExecutionResult.completed(out);
    }

    private static ExecutionResult runParallel(ImagePlus source,
                                               List<VariantPlan> plans,
                                               ProgressCallback progress) {
        final int total = plans.size();
        int poolSize = nativeWorkerCount(total);
        ExecutorService exec = Executors.newFixedThreadPool(poolSize);
        List<Future<VariantResult>> futures = new ArrayList<Future<VariantResult>>(total);
        List<VariantResult> out = new ArrayList<VariantResult>(total);
        boolean cancelled = false;
        try {
            for (final VariantPlan plan : plans) {
                if (Thread.currentThread().isInterrupted()) {
                    cancelled = true;
                    return ExecutionResult.cancelled(out);
                }
                futures.add(exec.submit(new java.util.concurrent.Callable<VariantResult>() {
                    @Override
                    public VariantResult call() {
                        return runOne(source, plan, false);
                    }
                }));
            }
            for (int i = 0; i < total; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    cancelled = true;
                    return ExecutionResult.cancelled(out);
                }
                Future<VariantResult> f = futures.get(i);
                VariantPlan plan = plans.get(i);
                VariantResult r;
                try {
                    r = f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancelled = true;
                    return ExecutionResult.cancelled(out);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    r = new VariantResult(plan, null, cause, 0L);
                } catch (CancellationException e) {
                    Thread.currentThread().interrupt();
                    cancelled = true;
                    return ExecutionResult.cancelled(out);
                }
                if (isCancellationResult(r)) {
                    Thread.currentThread().interrupt();
                    cancelled = true;
                    return ExecutionResult.cancelled(out);
                }
                out.add(r);
                notifyComplete(progress, i + 1, total, r);
                if (Thread.currentThread().isInterrupted()) {
                    cancelled = true;
                    return ExecutionResult.cancelled(out);
                }
            }
            return ExecutionResult.completed(out);
        } finally {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                cancelUncollected(exec, futures, out.size());
            } else {
                exec.shutdown();
            }
        }
    }

    private static VariantResult runOne(ImagePlus source, VariantPlan plan, boolean holdLock) {
        long start = System.currentTimeMillis();
        if (Thread.currentThread().isInterrupted()) {
            return cancellationResult(plan, start);
        }
        if (holdLock) WindowManagerLock.LOCK.lock();
        ImagePlus clone = null;
        try {
            ImagePlus executionSource = source;
            if (holdLock) {
                try {
                    clone = source.duplicate();
                    executionSource = clone;
                } catch (Throwable t) {
                    return new VariantResult(plan, null, t, System.currentTimeMillis() - start);
                }
            }
            ParallelContext.enterParallel();
            try {
                ImagePlus output = FilterExecutor.runDagThreadSafe(executionSource, plan.dag);
                if (output == null) {
                    return new VariantResult(plan, null,
                            new DagRejectedException("DAG produced no output"),
                            System.currentTimeMillis() - start);
                }
                if (Thread.currentThread().isInterrupted()) {
                    output.flush();
                    return cancellationResult(plan, start);
                }
                return new VariantResult(plan, output, null,
                        System.currentTimeMillis() - start);
            } finally {
                ParallelContext.exitParallel();
            }
        } catch (Throwable t) {
            return new VariantResult(plan, null, t, System.currentTimeMillis() - start);
        } finally {
            if (clone != null) clone.flush();
            if (holdLock) WindowManagerLock.LOCK.unlock();
        }
    }

    static int nativeWorkerCount(int totalPlans) {
        if (totalPlans < 1) return 1;
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, Math.min(totalPlans, Math.min(MAX_NATIVE_WORKERS, cores)));
    }

    private static void cancelUncollected(ExecutorService exec,
                                          List<Future<VariantResult>> futures,
                                          int firstUncollected) {
        boolean interrupted = Thread.interrupted();
        for (int i = Math.max(0, firstUncollected); i < futures.size(); i++) {
            futures.get(i).cancel(true);
        }
        exec.shutdownNow();
        try {
            while (!exec.awaitTermination(1L, TimeUnit.SECONDS)) {
                // Wait for the active variant workers to release their ImagePlus clones.
            }
        } catch (InterruptedException e) {
            interrupted = true;
            exec.shutdownNow();
        }
        if (flushCompletedDiscardedResults(futures, firstUncollected)) {
            interrupted = true;
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static boolean flushCompletedDiscardedResults(List<Future<VariantResult>> futures,
                                                          int firstUncollected) {
        boolean interrupted = false;
        for (int i = Math.max(0, firstUncollected); i < futures.size(); i++) {
            Future<VariantResult> future = futures.get(i);
            if (!future.isDone() || future.isCancelled()) continue;
            try {
                VariantResult discarded = future.get();
                if (discarded != null && discarded.output != null) discarded.output.flush();
            } catch (InterruptedException e) {
                interrupted = true;
            } catch (ExecutionException ignored) {
                // Failed discarded results do not own output images.
            } catch (CancellationException ignored) {
                // Already cancelled.
            }
        }
        return interrupted;
    }

    private static boolean isCancellationResult(VariantResult result) {
        return result != null
                && (result.error instanceof InterruptedException
                || result.error instanceof CancellationException);
    }

    private static VariantResult cancellationResult(VariantPlan plan, long start) {
        return new VariantResult(plan, null,
                new InterruptedException("Variant generation cancelled"),
                System.currentTimeMillis() - start);
    }

    private static void notifyStart(final ProgressCallback progress, final int total) {
        if (progress == null) return;
        dispatchOnEdt(new Runnable() {
            @Override public void run() { progress.onStart(total); }
        });
    }

    private static void notifyComplete(final ProgressCallback progress,
                                       final int completed,
                                       final int total,
                                       final VariantResult result) {
        if (progress == null) return;
        dispatchOnEdt(new Runnable() {
            @Override public void run() { progress.onVariantComplete(completed, total, result); }
        });
    }

    private static void notifyAllDone(final ProgressCallback progress,
                                      final List<VariantResult> results) {
        if (progress == null) return;
        dispatchOnEdt(new Runnable() {
            @Override public void run() { progress.onAllDone(results); }
        });
    }

    private static void notifyCancelled(final ProgressCallback progress) {
        if (progress == null) return;
        dispatchOnEdt(new Runnable() {
            @Override public void run() { progress.onCancelled(); }
        });
    }

    private static void dispatchOnEdt(Runnable runnable) {
        boolean interrupted = Thread.interrupted();
        try {
            SwingUtilities.invokeLater(runnable);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static final class ExecutionResult {
        final List<VariantResult> results;
        final boolean cancelled;

        private ExecutionResult(List<VariantResult> results, boolean cancelled) {
            this.results = results;
            this.cancelled = cancelled;
        }

        static ExecutionResult completed(List<VariantResult> results) {
            return new ExecutionResult(results, false);
        }

        static ExecutionResult cancelled(List<VariantResult> results) {
            return new ExecutionResult(results, true);
        }
    }
}
