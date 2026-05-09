package macro.builder.image.variation;

import ij.ImagePlus;
import macro.builder.image.FilterExecutor;
import macro.builder.image.ParallelContext;
import macro.builder.image.WindowManagerLock;
import macro.builder.image.dag.DagRejectedException;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs a list of {@link VariantPlan}s against a source {@link ImagePlus} and
 * collects one {@link VariantResult} per plan.
 *
 * <p>Execution policy:
 * <ul>
 *   <li>If <em>any</em> plan has {@code executionTier == "legacy"}, every plan
 *       runs serially under {@link WindowManagerLock} — legacy DAGs touch
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

    public static List<VariantResult> runAll(ImagePlus source,
                                             List<VariantPlan> plans,
                                             ProgressCallback progress) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (plans == null) throw new IllegalArgumentException("plans must not be null");
        final int total = plans.size();
        notifyStart(progress, total);
        if (total == 0) {
            List<VariantResult> empty = new ArrayList<VariantResult>();
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
        List<VariantResult> results = anyLegacy
                ? runSerial(source, plans, progress)
                : runParallel(source, plans, progress);
        notifyAllDone(progress, results);
        return results;
    }

    private static List<VariantResult> runSerial(ImagePlus source,
                                                 List<VariantPlan> plans,
                                                 ProgressCallback progress) {
        final int total = plans.size();
        List<VariantResult> out = new ArrayList<VariantResult>(total);
        int done = 0;
        for (VariantPlan plan : plans) {
            VariantResult r = runOne(source, plan, true);
            out.add(r);
            done++;
            notifyComplete(progress, done, total, r);
        }
        return out;
    }

    private static List<VariantResult> runParallel(ImagePlus source,
                                                   List<VariantPlan> plans,
                                                   ProgressCallback progress) {
        final int total = plans.size();
        int poolSize = nativeWorkerCount(total);
        ExecutorService exec = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<VariantResult>> futures = new ArrayList<Future<VariantResult>>(total);
            for (final VariantPlan plan : plans) {
                futures.add(exec.submit(new java.util.concurrent.Callable<VariantResult>() {
                    @Override
                    public VariantResult call() {
                        return runOne(source, plan, false);
                    }
                }));
            }
            List<VariantResult> out = new ArrayList<VariantResult>(total);
            for (int i = 0; i < total; i++) {
                Future<VariantResult> f = futures.get(i);
                VariantPlan plan = plans.get(i);
                VariantResult r;
                try {
                    r = f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    r = new VariantResult(plan, null, e, 0L);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    r = new VariantResult(plan, null, cause, 0L);
                }
                out.add(r);
                notifyComplete(progress, i + 1, total, r);
            }
            return out;
        } finally {
            exec.shutdown();
        }
    }

    private static VariantResult runOne(ImagePlus source, VariantPlan plan, boolean holdLock) {
        long start = System.currentTimeMillis();
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

    private static void notifyStart(final ProgressCallback progress, final int total) {
        if (progress == null) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { progress.onStart(total); }
        });
    }

    private static void notifyComplete(final ProgressCallback progress,
                                       final int completed,
                                       final int total,
                                       final VariantResult result) {
        if (progress == null) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { progress.onVariantComplete(completed, total, result); }
        });
    }

    private static void notifyAllDone(final ProgressCallback progress,
                                      final List<VariantResult> results) {
        if (progress == null) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { progress.onAllDone(results); }
        });
    }
}
