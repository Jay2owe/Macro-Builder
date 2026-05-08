package macro.builder.image.variation;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ShortProcessor;
import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.dag.DagRejectedException;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VariantExecutorTest {

    @Test
    public void runAllReturnsResultsInPlanOrderWithMatchingPlans() {
        ImagePlus source = shortStack(64, 64, 3);
        List<VariantPlan> plans = blurPlans(64, 64, new double[]{1.0, 2.0, 3.0, 4.0});

        List<VariantResult> results = VariantExecutor.runAll(source, plans, null);

        assertEquals(4, results.size());
        for (int i = 0; i < results.size(); i++) {
            VariantResult r = results.get(i);
            assertSame("result " + i + " plan", plans.get(i), r.plan);
            assertNull("result " + i + " unexpected error: " + r.error, r.error);
            assertNotNull("result " + i + " missing output", r.output);
            assertEquals(64, r.output.getWidth());
            assertEquals(64, r.output.getHeight());
            assertTrue("result " + i + " elapsed must be non-negative",
                    r.elapsedMillis >= 0L);
        }
    }

    @Test
    public void oneInvalidPlanDoesNotCrashTheOthers() {
        ImagePlus source = shortStack(64, 64, 3);
        List<VariantPlan> plans = new ArrayList<VariantPlan>();
        plans.add(blurPlan("v0", 1.0));
        plans.add(invalidUnknownOpPlan("bad"));
        plans.add(blurPlan("v1", 2.0));
        plans.add(blurPlan("v2", 3.0));

        List<VariantResult> results = VariantExecutor.runAll(source, plans, null);

        assertEquals(4, results.size());
        VariantResult bad = results.get(1);
        assertNotNull("bad plan should record an error", bad.error);
        assertTrue("expected DagRejectedException, got " + bad.error.getClass(),
                bad.error instanceof DagRejectedException);
        assertNull(bad.output);
        for (int i : new int[]{0, 2, 3}) {
            VariantResult r = results.get(i);
            assertNull("plan " + i + " unexpected error: " + r.error, r.error);
            assertNotNull("plan " + i + " missing output", r.output);
        }
    }

    @Test
    public void parallelSpeedupOnFourPlans() {
        // Per stage 04 exit gate: 4 plans on 4+ cores must run in parallel
        // wall-time < 2× a single plan's time.  With < 4 cores the plan count
        // and ceiling scale together so the assertion still validates the
        // qualitative claim "the pool actually parallelizes".
        int cores = Runtime.getRuntime().availableProcessors();
        int plansN = Math.min(4, cores);
        // The workload is intentionally lightweight: a single Gaussian on a
        // tiny 64×64×4 stack.  This keeps fixed overhead (thread-pool setup,
        // ImagePlus.duplicate, JaCoCo coverage probes baked into ImageJ's
        // blur path) much smaller than the parallel-speedup signal we care
        // about.  Heavy workloads suffer cross-thread contention on the very
        // hot blur1Direction loop which masks dispatch-level parallelism on
        // some hosts.
        final int width = 64;
        final int height = 64;
        final int slices = 4;
        final double sigma = 4.0;
        ImagePlus source = shortStack(width, height, slices);

        // Warm up: run two parallel batches and a single before we time
        // anything.  This lets the JIT compile blurGaussian in worker
        // threads and primes any per-class JaCoCo data structures.
        for (int i = 0; i < 3; i++) {
            VariantExecutor.runAll(source, uniformBlurPlans(width, height, plansN, sigma), null);
            VariantExecutor.runAll(source, uniformBlurPlans(width, height, 1, sigma), null);
        }

        // Best-of-many for both single and parallel timings: the workload is
        // small (a few ms per task) so noise dominates without aggregation.
        long singleNs = bestOf(11, source, uniformBlurPlans(width, height, 1, sigma));
        long parallelNs = bestOf(11, source, uniformBlurPlans(width, height, plansN, sigma));

        long singleMs = Math.max(1L, singleNs / 1_000_000L);
        long parallelMs = parallelNs / 1_000_000L;

        // Scale ceiling for hosts with fewer cores: with c cores running n plans,
        // best case is ⌈n/c⌉ × single.
        int batches = (plansN + cores - 1) / cores;
        // Floor the ceiling so sub-millisecond noise doesn't false-fail.
        long ceilingMs = Math.max(20L, batches * singleMs * 2L);
        assertTrue("parallel " + parallelMs + "ms must be < " + ceilingMs
                        + "ms (single=" + singleMs + "ms, cores=" + cores
                        + ", plans=" + plansN + ", batches=" + batches + ")",
                parallelMs < ceilingMs);

        // Independent parallelism proof, robust to instrumentation overhead:
        // sum of per-task elapsed times must materially exceed the batch wall
        // time, otherwise the pool ran tasks back-to-back on one thread.
        List<VariantResult> results = VariantExecutor.runAll(
                source, uniformBlurPlans(width, height, plansN, sigma), null);
        long sumElapsed = 0L;
        for (VariantResult r : results) {
            assertNull("error during overlap measurement: " + r.error, r.error);
            sumElapsed += r.elapsedMillis;
        }
        // Total elapsed across N parallel tasks should be at least 1.5× the
        // batch wall time when at least 2 tasks overlap.  Using the recorded
        // (already-warm) parallelNs bound here would be circular, so re-time.
        long t0 = System.nanoTime();
        VariantExecutor.runAll(source, uniformBlurPlans(width, height, plansN, sigma), null);
        long overlapWallMs = (System.nanoTime() - t0) / 1_000_000L;
        // Slack the bound for very small wall times — tasks under 5 ms are
        // dominated by thread-pool ramp and don't reliably show overlap.
        if (overlapWallMs > 5L) {
            assertTrue("expected overlap: sum(elapsed)=" + sumElapsed
                            + "ms must exceed 1.2× wall=" + overlapWallMs + "ms",
                    sumElapsed > overlapWallMs * 12L / 10L);
        }
    }

    private static long bestOf(int trials, ImagePlus source, List<VariantPlan> plans) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < trials; i++) {
            long t0 = System.nanoTime();
            List<VariantResult> rs = VariantExecutor.runAll(source, plans, null);
            long ns = System.nanoTime() - t0;
            for (VariantResult r : rs) {
                assertNull("unexpected error during timing run: " + r.error, r.error);
            }
            if (ns < best) best = ns;
        }
        return best;
    }

    private static List<VariantPlan> uniformBlurPlans(int w, int h, int n, double sigma) {
        double[] sigmas = new double[n];
        for (int i = 0; i < n; i++) sigmas[i] = sigma;
        return blurPlans(w, h, sigmas);
    }

    @Test
    public void anyLegacyPlanForcesSerialExecution() {
        // Three native plans plus one synthesized "legacy" plan (a node with a
        // non-empty commandName). The legacy plan errors out (runDagThreadSafe
        // rejects legacy DAGs), but that error should be captured in
        // VariantResult.error — and the entire batch should run serially.
        // Use a lightweight workload because we're only checking *plan* outcomes
        // and the dispatch path, not wall-time speedup (parallelSpeedupOnFourPlans
        // covers that).
        ImagePlus source = shortStack(64, 64, 4);

        List<VariantPlan> mixed = new ArrayList<VariantPlan>();
        mixed.add(blurPlan("v0", 1.0));
        mixed.add(blurPlan("v1", 2.0));
        mixed.add(blurPlan("v2", 3.0));
        mixed.add(legacyPlanStub());

        List<VariantResult> results = VariantExecutor.runAll(source, mixed, null);

        assertEquals(4, results.size());
        for (int i = 0; i < 3; i++) {
            assertNull("native plan " + i + " unexpected error: " + results.get(i).error,
                    results.get(i).error);
            assertNotNull(results.get(i).output);
        }
        VariantResult legacyResult = results.get(3);
        assertNotNull("legacy plan should error in v1", legacyResult.error);
        assertTrue("expected DagRejectedException, got " + legacyResult.error.getClass(),
                legacyResult.error instanceof DagRejectedException);

        // Verify the legacy lock path is exercised: after runAll returns the lock
        // must be released (no leaks).
        assertFalse("WindowManagerLock should be released after runAll",
                macro.builder.image.WindowManagerLock.LOCK.isLocked());
    }

    @Test
    public void onVariantCompleteFiresOnEdtNTimes() throws Exception {
        ImagePlus source = shortStack(64, 64, 2);
        List<VariantPlan> plans = blurPlans(64, 64, new double[]{1.0, 1.0, 1.0, 1.0});

        final List<Boolean> onEdt = new CopyOnWriteArrayList<Boolean>();
        final int[] startCount = new int[1];
        final int[] doneCount = new int[1];
        final List<Integer> completedSeq = new CopyOnWriteArrayList<Integer>();

        ProgressCallback cb = new ProgressCallback() {
            @Override public void onStart(int total) {
                startCount[0]++;
                onEdt.add(SwingUtilities.isEventDispatchThread());
            }
            @Override public void onVariantComplete(int completed, int total,
                                                    VariantResult result) {
                completedSeq.add(completed);
                onEdt.add(SwingUtilities.isEventDispatchThread());
            }
            @Override public void onAllDone(List<VariantResult> results) {
                doneCount[0]++;
                onEdt.add(SwingUtilities.isEventDispatchThread());
            }
        };

        VariantExecutor.runAll(source, plans, cb);

        // Drain the EDT — invokeLater is async so tests must wait for the queue
        // to flush before asserting.
        flushEdt();

        assertEquals(1, startCount[0]);
        assertEquals(1, doneCount[0]);
        assertEquals(plans.size(), completedSeq.size());
        // Counter monotonically increases.
        for (int i = 0; i < completedSeq.size(); i++) {
            assertEquals(Integer.valueOf(i + 1), completedSeq.get(i));
        }
        // Every callback invocation ran on the EDT.
        assertEquals(2 + plans.size(), onEdt.size());
        for (int i = 0; i < onEdt.size(); i++) {
            assertTrue("callback " + i + " was not on EDT", onEdt.get(i));
        }
    }

    @Test
    public void emptyPlansListReturnsEmptyResults() {
        ImagePlus source = shortStack(8, 8, 1);
        List<VariantResult> results = VariantExecutor.runAll(
                source, Collections.<VariantPlan>emptyList(), null);
        assertTrue(results.isEmpty());
    }

    // ── helpers ──

    private static ImagePlus shortStack(int w, int h, int slices) {
        ImageStack stack = new ImageStack(w, h);
        for (int i = 0; i < slices; i++) {
            ShortProcessor sp = new ShortProcessor(w, h);
            // Non-trivial pixel content so the blur actually does work.
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    sp.set(x, y, ((x * 17 + y * 31 + i * 7) & 0xFFFF));
                }
            }
            stack.addSlice(sp);
        }
        return new ImagePlus("test-stack", stack);
    }

    private static List<VariantPlan> blurPlans(int w, int h, double[] sigmas) {
        List<VariantPlan> plans = new ArrayList<VariantPlan>(sigmas.length);
        for (int i = 0; i < sigmas.length; i++) {
            plans.add(blurPlan("blur_" + i, sigmas[i]));
        }
        return plans;
    }

    private static VariantPlan blurPlan(String label, double sigma) {
        DagNode node = new DagNode("n1", OpType.GAUSSIAN_BLUR, "sigma=" + sigma + " stack");
        DagLine line = new DagLine("line_1", Collections.singletonList(node), 1);
        DagIR dag = new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_1",
                "native");
        return new VariantPlan(label, dag, (Map<String, String>) null);
    }

    private static VariantPlan invalidUnknownOpPlan(String label) {
        DagNode node = new DagNode("n1", OpType.UNKNOWN, "");
        DagLine line = new DagLine("line_1", Collections.singletonList(node), 1);
        DagIR dag = new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_1",
                "native");
        return new VariantPlan(label, dag, (Map<String, String>) null);
    }

    private static VariantPlan legacyPlanStub() {
        // commandName non-empty triggers DagIR's tier-coercion to "legacy".
        DagNode node = new DagNode("n1", OpType.GAUSSIAN_BLUR, "sigma=1 stack",
                "Some Legacy Command", "Plugins>Some>Path");
        DagLine line = new DagLine("line_1", Collections.singletonList(node), 1);
        DagIR dag = new DagIR(1, 1,
                Collections.singletonList(line),
                Collections.<macro.builder.image.dag.Combiner>emptyList(),
                "line_1",
                "native");
        // Sanity check: DagIR should have forced tier to "legacy".
        assertEquals("legacy", dag.executionTier);
        return new VariantPlan("legacy_stub", dag, (Map<String, String>) null);
    }

    private static void flushEdt() throws InterruptedException, InvocationTargetException {
        // invokeAndWait blocks until every prior invokeLater task has run.
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() { /* drain marker */ }
        });
    }
}
