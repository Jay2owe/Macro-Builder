package macro.builder.ui.sandbox.variation;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Window;

/**
 * Non-modal progress {@link JDialog} shown while {@code VariantExecutor.runAll}
 * is producing variant outputs in the background.
 *
 * <p>Determinate {@link JProgressBar} sized to the variant count. Title shows
 * "running k of n" as it progresses. Closing the window from the title bar is
 * disabled because the {@code SwingWorker} backing the run cannot be cleanly
 * cancelled at this stage — stage 07 / 08 may add a cancel button.
 *
 * <p>All mutators are safe to call from any thread; they marshal onto the EDT.
 */
public final class ProgressDialog extends JDialog {

    private final JProgressBar bar;
    private final JLabel status;
    private final int total;

    public ProgressDialog(Window owner, int total) {
        super(owner, "Generating variants", ModalityType.MODELESS);
        this.total = Math.max(0, total);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        this.bar = new JProgressBar(0, Math.max(1, this.total));
        this.bar.setStringPainted(true);
        this.bar.setValue(0);
        this.bar.setString("0 / " + this.total);

        this.status = new JLabel("Preparing variant runs...");

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        panel.add(status, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);

        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Update the progress bar to {@code completed} of {@code total}. EDT-safe. */
    public void setProgress(final int completed) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                int clamped = Math.max(0, Math.min(total, completed));
                bar.setValue(clamped);
                bar.setString(clamped + " / " + total);
                status.setText(clamped >= total
                        ? "Finalising results..."
                        : "Running variant " + (clamped + 1) + " of " + total + "...");
            }
        });
    }

    /** Replace the status text below the title. EDT-safe. */
    public void setStatusText(final String text) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                status.setText(text == null ? "" : text);
            }
        });
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}
