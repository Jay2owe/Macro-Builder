package macro.builder.image.variation;

import java.util.List;

/**
 * Listener for {@link VariantExecutor#runAll}. All callbacks dispatch on the
 * Swing EDT (via {@code SwingUtilities.invokeLater}) so implementations can
 * touch UI state directly without re-marshaling.
 *
 * <p>Default methods are no-ops to keep ad hoc callers terse.
 */
public interface ProgressCallback {

    void onStart(int total);

    void onVariantComplete(int completed, int total, VariantResult result);

    void onAllDone(List<VariantResult> results);
}
