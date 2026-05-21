package macro.builder.image.variation;

import java.util.List;

/**
 * Listener for {@link VariantExecutor#runAll}. All callbacks dispatch on the
 * Swing EDT (via {@code SwingUtilities.invokeLater}) so implementations can
 * touch UI state directly without re-marshaling.
 *
 * <p>{@link #onCancelled()} is fired instead of {@link #onAllDone(List)} when
 * generation stops because the caller thread was interrupted. In that case
 * {@code runAll} returns the complete results collected before cancellation.
 *
 * <p>Default methods are no-ops to keep ad hoc callers terse.
 */
public interface ProgressCallback {

    default void onStart(int total) {}

    default void onVariantComplete(int completed, int total, VariantResult result) {}

    default void onAllDone(List<VariantResult> results) {}

    default void onCancelled() {}
}
