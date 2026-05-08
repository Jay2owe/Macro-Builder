package macro.builder.ui.sandbox.variation;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.MemoryEstimate;
import macro.builder.image.variation.MemoryEstimator;
import macro.builder.image.variation.OpTypeParamRegistry;
import macro.builder.image.variation.ProgressCallback;
import macro.builder.image.variation.RoiCropper;
import macro.builder.image.variation.VariantAxis;
import macro.builder.image.variation.VariantExecutor;
import macro.builder.image.variation.VariantPlan;
import macro.builder.image.variation.VariantResult;
import macro.builder.image.variation.VariantSampler;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal Swing dialog asking the user what to vary in their pipeline.
 *
 * <p>Pulls in stage 01–04: builds {@link VariantAxis} lists, runs the
 * {@link VariantSampler}, calls {@link MemoryEstimator}, and finally fires
 * {@link VariantExecutor#runAll} inside a {@link SwingWorker} with a
 * {@link ProgressDialog}.
 *
 * <p>The result callback is invoked on the EDT (from inside the worker's
 * {@code done()}), so callers may safely create Swing windows from it.
 *
 * <p>{@code SandboxModel} is package-private, so this dialog accepts the DAG
 * and source {@link ImagePlus} directly. Stage 08 (which adds the Sandbox
 * button) is responsible for extracting them from the model.
 */
public final class VariationChooserDialog extends JDialog {

    /** Maximum variants the user may produce in one run (cartesian or otherwise). */
    public static final int MAX_VARIANTS_HARD_CAP = 16;
    /** Default per-run cap when Advanced is closed. */
    public static final int DEFAULT_MAX_VARIANTS = 9;

    private final DagIR baseline;
    private final ImagePlus source;
    private final Consumer<List<VariantResult>> resultCallback;

    private final ButtonGroup modeGroup = new ButtonGroup();
    private final JRadioButton sweepRadio = new JRadioButton("Sweep parameter", true);
    private final JRadioButton swapRadio = new JRadioButton("Swap filter", false);

    private final JComboBox<NodeChoice> nodeCombo = new JComboBox<NodeChoice>();
    private final CardLayout panelStack = new CardLayout();
    private final JPanel modePanel = new JPanel(panelStack);
    private final SweepPanel sweepPanel = new SweepPanel();
    private final SwapPanel swapPanel = new SwapPanel();

    private final JCheckBox advancedToggle = new JCheckBox("Advanced...");
    private final JPanel advancedBody = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private final JCheckBox cartesianCheck = new JCheckBox("Cartesian product (multi-axis)");
    private final SpinnerNumberModel maxVariantsModel =
            new SpinnerNumberModel(DEFAULT_MAX_VARIANTS, 2, MAX_VARIANTS_HARD_CAP, 1);
    private final JSpinner maxVariantsSpinner = new JSpinner(maxVariantsModel);

    private final JLabel memoryLabel = new JLabel(" ");
    private final JButton generateButton = new JButton("Generate");
    private final JButton cancelButton = new JButton("Cancel");

    private boolean updatingNodeCombo;

    public VariationChooserDialog(Window owner,
                                  DagIR baseline,
                                  ImagePlus source,
                                  Consumer<List<VariantResult>> resultCallback) {
        super(owner, "Create variations", ModalityType.APPLICATION_MODAL);
        if (baseline == null) throw new IllegalArgumentException("baseline must not be null");
        if (resultCallback == null) {
            throw new IllegalArgumentException("resultCallback must not be null");
        }
        this.baseline = baseline;
        this.source = source;
        this.resultCallback = resultCallback;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        modeGroup.add(sweepRadio);
        modeGroup.add(swapRadio);

        modePanel.add(sweepPanel, "sweep");
        modePanel.add(swapPanel, "swap");

        buildLayout();
        wireListeners();
        populateNodeCombo();
        refreshGenerateState();
    }

    private void buildLayout() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel modeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeBar.add(new JLabel("Mode:"));
        modeBar.add(sweepRadio);
        modeBar.add(swapRadio);

        JPanel nodeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nodeBar.add(new JLabel("Node:"));
        nodeBar.add(nodeCombo);

        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 4, 0);
        gbc.gridy = 0;
        header.add(modeBar, gbc);
        gbc.gridy = 1;
        header.add(nodeBar, gbc);

        // Advanced section: toggle + body that we hide/show in place.
        advancedBody.add(cartesianCheck);
        advancedBody.add(new JLabel("Max variants:"));
        advancedBody.add(maxVariantsSpinner);
        advancedBody.setVisible(false);

        JPanel advancedPanel = new JPanel(new BorderLayout(0, 2));
        advancedPanel.add(advancedToggle, BorderLayout.NORTH);
        advancedPanel.add(advancedBody, BorderLayout.CENTER);

        memoryLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        memoryLabel.setText("(memory estimate appears here)");

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(modePanel, BorderLayout.CENTER);
        center.add(advancedPanel, BorderLayout.SOUTH);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonBar.add(cancelButton);
        buttonBar.add(generateButton);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(memoryLabel, BorderLayout.NORTH);
        south.add(buttonBar, BorderLayout.SOUTH);

        content.add(header, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);

        // Time-lapse policy tooltip — surface the v1 single-timepoint behaviour.
        getRootPane().setDefaultButton(generateButton);
        setTooltipForTimeLapsePolicy();

        pack();
        setLocationRelativeTo(getOwner());
    }

    private void setTooltipForTimeLapsePolicy() {
        String tip = "<html>Variants run on the current timepoint only.<br>"
                + "Promote a winner, then run the chosen pipeline on the full T axis.</html>";
        generateButton.setToolTipText(tip);
    }

    private void wireListeners() {
        sweepRadio.addActionListener(e -> {
            panelStack.show(modePanel, "sweep");
            populateNodeCombo();
            refreshGenerateState();
            refreshMemoryEstimate();
        });
        swapRadio.addActionListener(e -> {
            panelStack.show(modePanel, "swap");
            populateNodeCombo();
            refreshGenerateState();
            refreshMemoryEstimate();
        });
        nodeCombo.addActionListener(e -> {
            if (updatingNodeCombo) return;
            DagNode selected = selectedNode();
            sweepPanel.setNode(selected);
            swapPanel.setNode(selected);
            refreshGenerateState();
            refreshMemoryEstimate();
        });

        sweepPanel.setStateListener(new SweepPanel.StateListener() {
            @Override public void onStateChanged() {
                refreshGenerateState();
                refreshMemoryEstimate();
            }
        });
        swapPanel.setStateListener(new SwapPanel.StateListener() {
            @Override public void onStateChanged() {
                refreshGenerateState();
                refreshMemoryEstimate();
            }
        });

        advancedToggle.addActionListener(e -> {
            advancedBody.setVisible(advancedToggle.isSelected());
            if (!advancedToggle.isSelected()) cartesianCheck.setSelected(false);
            revalidate();
            repaint();
            refreshMemoryEstimate();
        });
        cartesianCheck.addActionListener(e -> refreshMemoryEstimate());
        maxVariantsSpinner.addChangeListener(e -> refreshMemoryEstimate());

        cancelButton.addActionListener(e -> dispose());
        generateButton.addActionListener(e -> onGenerate());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); }
        });
    }

    private void populateNodeCombo() {
        updatingNodeCombo = true;
        try {
            DefaultComboBoxModel<NodeChoice> model = new DefaultComboBoxModel<NodeChoice>();
            boolean sweepMode = sweepRadio.isSelected();
            for (DagLine line : baseline.lines) {
                for (DagNode node : line.ops) {
                    if (sweepMode && OpTypeParamRegistry.paramsOf(node.type).isEmpty()) continue;
                    model.addElement(new NodeChoice(node, line.name.length() > 0 ? line.name : line.id));
                }
            }
            nodeCombo.setModel(model);
            if (model.getSize() > 0) {
                nodeCombo.setSelectedIndex(0);
                DagNode selected = model.getElementAt(0).node;
                sweepPanel.setNode(selected);
                swapPanel.setNode(selected);
            } else {
                sweepPanel.setNode(null);
                swapPanel.setNode(null);
            }
        } finally {
            updatingNodeCombo = false;
        }
    }

    private DagNode selectedNode() {
        Object item = nodeCombo.getSelectedItem();
        return item instanceof NodeChoice ? ((NodeChoice) item).node : null;
    }

    private void refreshGenerateState() {
        boolean sweep = sweepRadio.isSelected();
        boolean ready = sweep ? sweepPanel.isReady() : swapPanel.isReady();
        generateButton.setEnabled(ready);
    }

    private void refreshMemoryEstimate() {
        int alts = sweepRadio.isSelected()
                ? sweepPanel.alternativeCount()
                : swapPanel.alternativeCount();
        // The plan list always starts with the baseline; treat 0 alternatives as
        // a 1-variant baseline for the visible estimate.
        int variants = Math.max(1, alts + 1);
        if (advancedToggle.isSelected() && cartesianCheck.isSelected()) {
            variants = Math.max(1, alts);  // cartesian doesn't include baseline
        }
        if (source == null) {
            memoryLabel.setText(variants + " variants (load source to compute memory)");
            memoryLabel.setForeground(Color.BLACK);
            return;
        }
        try {
            MemoryEstimate estimate = MemoryEstimator.estimate(source, variants);
            memoryLabel.setText(estimate.humanReadable);
            memoryLabel.setForeground(estimate.exceedsBudget ? new Color(180, 0, 0) : Color.BLACK);
        } catch (RuntimeException re) {
            memoryLabel.setText("Memory estimate unavailable: " + re.getMessage());
            memoryLabel.setForeground(new Color(180, 0, 0));
        }
    }

    private void onGenerate() {
        boolean sweep = sweepRadio.isSelected();
        VariantAxis axis = sweep ? sweepPanel.buildAxis() : swapPanel.buildAxis();
        if (axis.alternatives.isEmpty()) {
            // The Generate button should already be disabled in this case; defensive.
            return;
        }
        boolean advanced = advancedToggle.isSelected();
        boolean cartesian = advanced && cartesianCheck.isSelected();
        int cap = ((Number) maxVariantsModel.getValue()).intValue();
        if (!advanced) cap = DEFAULT_MAX_VARIANTS;

        final List<VariantPlan> plans;
        try {
            plans = choosePlans(baseline, Collections.singletonList(axis), advanced, cartesian, cap);
        } catch (IllegalStateException ise) {
            IJ.error("Variations", ise.getMessage());
            return;
        } catch (IllegalArgumentException iae) {
            IJ.error("Variations", iae.getMessage());
            return;
        }
        if (plans.isEmpty()) return;

        generateButton.setEnabled(false);
        final ProgressDialog progress = new ProgressDialog(this, plans.size());

        SwingWorker<List<VariantResult>, Integer> worker =
                new SwingWorker<List<VariantResult>, Integer>() {
            @Override
            protected List<VariantResult> doInBackground() {
                ImagePlus executionSource = resolveExecutionSource(source, plans.size());
                if (executionSource == null) return Collections.emptyList();
                return VariantExecutor.runAll(executionSource, plans, new ProgressCallback() {
                    @Override public void onStart(int total) { publish(0); }
                    @Override public void onVariantComplete(int completed, int total, VariantResult r) {
                        publish(completed);
                    }
                    @Override public void onAllDone(List<VariantResult> results) { /* no-op */ }
                });
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (chunks.isEmpty()) return;
                progress.setProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                progress.dispose();
                List<VariantResult> results;
                try {
                    results = get();
                } catch (Exception ex) {
                    IJ.handleException(ex);
                    results = Collections.emptyList();
                }
                // Dispose the chooser before invoking the callback so any window
                // the callback creates can take focus cleanly.
                final List<VariantResult> finalResults = results;
                VariationChooserDialog.this.dispose();
                resultCallback.accept(finalResults);
            }
        };
        progress.setVisible(true);
        worker.execute();
    }

    /**
     * Resolve the {@link ImagePlus} actually fed into {@link VariantExecutor}.
     * If memory exceeds budget, prompt the user to draw an ROI and crop. Then,
     * if the source is a hyperstack with {@code T > 1}, narrow to the
     * currently displayed timepoint.
     *
     * <p>Runs on the {@code SwingWorker} background thread — must not be EDT
     * because {@link RoiPromptDialog#prompt} parks on a latch.
     */
    private ImagePlus resolveExecutionSource(ImagePlus original, int variantCount) {
        if (original == null) return null;
        ImagePlus working = original;
        MemoryEstimate estimate = MemoryEstimator.estimate(working, variantCount);
        if (estimate.exceedsBudget) {
            Roi roi = RoiPromptDialog.prompt(working, estimate.humanReadable);
            if (roi == null) return null;
            working = RoiCropper.cropToRoi(working, roi);
        }
        if (working.getNFrames() > 1) {
            int t = Math.max(1, original.getT());
            int c = Math.max(1, working.getNChannels());
            int z = Math.max(1, working.getNSlices());
            working = new Duplicator().run(working, 1, c, 1, z, t, t);
        }
        return working;
    }

    /**
     * Decide between OFAT and cartesian sampling. Refuses cartesian unless
     * Advanced is open AND the cartesian box is ticked — exposed as a static
     * method so headless tests can exercise the logic without instantiating
     * any Swing components.
     *
     * @throws IllegalStateException if {@code cartesianRequested} is true but
     *     {@code advancedOpen} is false (the spec's "v1 only allows multi-axis
     *     when Advanced is open and explicitly ticked" rule).
     */
    public static List<VariantPlan> choosePlans(DagIR baseline,
                                                List<VariantAxis> axes,
                                                boolean advancedOpen,
                                                boolean cartesianRequested,
                                                int maxVariants) {
        if (cartesianRequested && !advancedOpen) {
            throw new IllegalStateException(
                    "Cartesian product is only available with the Advanced panel open.");
        }
        int cap = Math.min(maxVariants, MAX_VARIANTS_HARD_CAP);
        if (cap < 1) cap = 1;
        return cartesianRequested
                ? VariantSampler.cartesian(baseline, axes, cap)
                : VariantSampler.ofat(baseline, axes, cap);
    }

    /** Tuple of (DagNode, lineName) used as the JComboBox<NodeChoice> entry. */
    private static final class NodeChoice {
        final DagNode node;
        final String lineName;

        NodeChoice(DagNode node, String lineName) {
            this.node = node;
            this.lineName = lineName == null ? "" : lineName;
        }

        @Override
        public String toString() {
            return lineName + ": " + node.type.name() + " [id=" + node.id + "]";
        }
    }

    // Test hooks — package-private so VariationChooserDialogTest can drive the
    // dialog without exposing internals to other code.

    SweepPanel sweepPanelForTest() { return sweepPanel; }
    SwapPanel swapPanelForTest() { return swapPanel; }
    JCheckBox advancedToggleForTest() { return advancedToggle; }
    JCheckBox cartesianCheckForTest() { return cartesianCheck; }
}
