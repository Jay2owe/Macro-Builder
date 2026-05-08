package macro.builder.ui.sandbox.variation;

import ij.ImagePlus;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the current Z slice across a set of registered {@link ImagePlus}
 * tiles in lockstep. Pure logic — no Swing, no AWT, headless-safe.
 *
 * <p>{@link #setSlice(int)} clamps the requested slice to {@code [1, maxSlice()]},
 * calls {@link ImagePlus#setSliceWithoutUpdate(int)} on every registered tile,
 * and runs each tile's repaint callback so the on-screen {@code ImageCanvas}
 * picks up the new pixels.
 *
 * <p>{@link #maxSlice()} is the <em>minimum</em> {@code getNSlices()} across all
 * registered tiles. Variants whose pipeline consumed Z (e.g. a Z-projection
 * node) will collapse the shared scroll range to that variant's slice count;
 * the spec calls this out explicitly under stage 06's known risks.
 */
public final class SharedSliceDriver {

    private final List<ImagePlus> slaves = new ArrayList<ImagePlus>();
    private final List<Runnable> repainters = new ArrayList<Runnable>();
    private int currentSlice = 1;

    /**
     * Register a tile with the driver. {@code onRepaint} is invoked on every
     * {@link #setSlice(int)} after {@code imp.setSliceWithoutUpdate} has been
     * applied; pass {@code () -> canvas.repaint()} from the UI side. May be
     * {@code null} for tests that only verify slice fan-out.
     */
    public void register(ImagePlus imp, Runnable onRepaint) {
        if (imp == null) throw new IllegalArgumentException("imp must not be null");
        slaves.add(imp);
        repainters.add(onRepaint);
    }

    public void unregister(ImagePlus imp) {
        if (imp == null) return;
        for (int i = slaves.size() - 1; i >= 0; i--) {
            if (slaves.get(i) == imp) {
                slaves.remove(i);
                repainters.remove(i);
            }
        }
        currentSlice = Math.max(1, Math.min(currentSlice, maxSlice()));
    }

    public void setSlice(int slice) {
        int clamped = Math.max(1, Math.min(slice, maxSlice()));
        currentSlice = clamped;
        for (int i = 0; i < slaves.size(); i++) {
            slaves.get(i).setSliceWithoutUpdate(clamped);
            Runnable r = repainters.get(i);
            if (r != null) r.run();
        }
    }

    /**
     * Minimum {@code getNSlices()} across registered tiles, or 1 if none are
     * registered. See class javadoc for why minimum, not maximum.
     */
    public int maxSlice() {
        if (slaves.isEmpty()) return 1;
        int min = Integer.MAX_VALUE;
        for (ImagePlus imp : slaves) {
            int n = Math.max(1, imp.getNSlices());
            if (n < min) min = n;
        }
        return min == Integer.MAX_VALUE ? 1 : min;
    }

    public int currentSlice() {
        return currentSlice;
    }

    public int size() {
        return slaves.size();
    }
}
