package macro.builder.ui.sandbox.variation;

import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.OpTypeParamRegistry;
import macro.builder.image.variation.ParamSpec;
import macro.builder.image.variation.ParamSpec.Scale;
import macro.builder.image.variation.VariantAxis;
import macro.builder.image.variation.VariantAxis.AlternativeValue;
import macro.builder.image.variation.VariantAxis.Kind;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sub-panel for "sweep parameter" mode: pick a numeric param of the chosen DAG
 * node, then a min/max/steps range. Spits out a {@link VariantAxis} of
 * {@link Kind#PARAM_SWEEP} alternatives.
 *
 * <p>Pure Swing — constructable headless. The dialog wires
 * {@link #setChangeListener} so the live memory estimate updates whenever the
 * step count changes.
 */
public final class SweepPanel extends JPanel {

    /** Hard upper bound on sweep steps, mirroring the spec ("default 5, max 9"). */
    public static final int MAX_STEPS = 9;

    /** Listener fired when the user changes any input that affects {@link #alternativeCount}. */
    public interface StateListener {
        void onStateChanged();
    }

    private DagNode node;
    private final JLabel nodeLabel = new JLabel(" ");
    private final JComboBox<ParamSpec> paramCombo = new JComboBox<ParamSpec>();
    private final SpinnerNumberModel minModel = new SpinnerNumberModel(0.5, -1.0e9, 1.0e9, 0.1);
    private final SpinnerNumberModel maxModel = new SpinnerNumberModel(4.0, -1.0e9, 1.0e9, 0.1);
    private final SpinnerNumberModel stepsModel = new SpinnerNumberModel(5, 2, MAX_STEPS, 1);
    private final JSpinner minSpinner = new JSpinner(minModel);
    private final JSpinner maxSpinner = new JSpinner(maxModel);
    private final JSpinner stepsSpinner = new JSpinner(stepsModel);
    private final JLabel scaleLabel = new JLabel(" ");
    private final JLabel previewLabel = new JLabel(" ");
    private StateListener listener;
    private boolean updatingParamCombo;

    public SweepPanel() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        nodeLabel.setFont(nodeLabel.getFont().deriveFont(java.awt.Font.BOLD));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(nodeLabel, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        add(new JLabel("Parameter:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        add(paramCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        add(new JLabel("Min:"), gbc);
        gbc.gridx = 1;
        add(minSpinner, gbc);
        gbc.gridx = 2;
        add(new JLabel("Max:"), gbc);
        gbc.gridx = 3;
        add(maxSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        add(new JLabel("Steps:"), gbc);
        gbc.gridx = 1;
        add(stepsSpinner, gbc);
        gbc.gridx = 2;
        add(new JLabel("Scale:"), gbc);
        gbc.gridx = 3;
        add(scaleLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4; gbc.fill = GridBagConstraints.HORIZONTAL;
        add(previewLabel, gbc);

        paramCombo.addActionListener(e -> {
            if (updatingParamCombo) return;
            applyParamSpec(getSelectedParam());
            fireChange();
        });
        minSpinner.addChangeListener(this::onSpinnerChange);
        maxSpinner.addChangeListener(this::onSpinnerChange);
        stepsSpinner.addChangeListener(this::onSpinnerChange);
    }

    public void setStateListener(StateListener listener) {
        this.listener = listener;
    }

    /** Set the DAG node whose parameter should be swept. Rebuilds the param combo. */
    public void setNode(DagNode node) {
        this.node = node;
        rebuildParamCombo();
        applyParamSpec(getSelectedParam());
        fireChange();
    }

    public DagNode getNode() {
        return node;
    }

    public ParamSpec getSelectedParam() {
        Object item = paramCombo.getSelectedItem();
        return item instanceof ParamSpec ? (ParamSpec) item : null;
    }

    /** Test convenience: pick the param spec by its arg key. */
    public boolean selectParamByKey(String argKey) {
        if (argKey == null) return false;
        for (int i = 0; i < paramCombo.getItemCount(); i++) {
            ParamSpec spec = paramCombo.getItemAt(i);
            if (spec != null && argKey.equals(spec.argKey)) {
                paramCombo.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    /** Programmatic sweep range setter. The spinners are updated and listeners fire once. */
    public void setSweepRange(double min, double max, int steps) {
        int clampedSteps = Math.max(2, Math.min(MAX_STEPS, steps));
        minModel.setValue(min);
        maxModel.setValue(max);
        stepsModel.setValue(clampedSteps);
        refreshPreview();
    }

    /** Number of alternatives the current state would emit (== steps spinner). */
    public int alternativeCount() {
        if (!isReady()) return 0;
        return ((Number) stepsModel.getValue()).intValue();
    }

    /** True iff a node and a selected param are both in place. */
    public boolean isReady() {
        return node != null && getSelectedParam() != null;
    }

    /** Build the {@link VariantAxis} described by current dialog state. */
    public VariantAxis buildAxis() {
        if (node == null) {
            throw new IllegalStateException("no node selected");
        }
        ParamSpec spec = getSelectedParam();
        if (spec == null) {
            throw new IllegalStateException("no parameter selected");
        }
        double min = ((Number) minModel.getValue()).doubleValue();
        double max = ((Number) maxModel.getValue()).doubleValue();
        int steps = ((Number) stepsModel.getValue()).intValue();
        if (steps < 1) steps = 1;
        if (max < min) {
            double tmp = min; min = max; max = tmp;
        }
        List<Double> values = (spec.scale == Scale.LOG && min > 0.0 && max > 0.0)
                ? geometricSpacing(min, max, steps)
                : arithmeticSpacing(min, max, steps);

        Map<String, Double> base = OpTypeParamRegistry.parseArgs(node.type, node.args);
        if (base.isEmpty()) base = new LinkedHashMap<String, Double>();
        List<AlternativeValue> alts = new ArrayList<AlternativeValue>(values.size());
        for (double v : values) {
            Map<String, Double> tweaked = new LinkedHashMap<String, Double>(base);
            tweaked.put(spec.argKey, v);
            String args = OpTypeParamRegistry.renderArgs(node.type, tweaked);
            String label = formatLabel(spec, v);
            alts.add(new AlternativeValue(label, null, args));
        }
        return new VariantAxis(node.id, Kind.PARAM_SWEEP, alts);
    }

    private void rebuildParamCombo() {
        updatingParamCombo = true;
        try {
            DefaultComboBoxModel<ParamSpec> model = new DefaultComboBoxModel<ParamSpec>();
            if (node != null) {
                for (ParamSpec spec : OpTypeParamRegistry.paramsOf(node.type)) {
                    model.addElement(spec);
                }
            }
            paramCombo.setModel(model);
            paramCombo.setRenderer(new ParamSpecRenderer());
            if (model.getSize() > 0) paramCombo.setSelectedIndex(0);
            String label = node == null
                    ? " "
                    : node.type.name() + "  [id=" + node.id + "]";
            nodeLabel.setText(label);
        } finally {
            updatingParamCombo = false;
        }
    }

    private void applyParamSpec(ParamSpec spec) {
        if (spec == null) {
            scaleLabel.setText(" ");
            refreshPreview();
            return;
        }
        // Default min/max to the spec's recommended sweep range and the default
        // value's neighbourhood. We pick spec.min/spec.max because that is what
        // the user wants 90% of the time; widening is allowed but typed.
        minModel.setValue(spec.min);
        maxModel.setValue(spec.max);
        scaleLabel.setText(spec.scale == Scale.LOG ? "log" : "linear");
        refreshPreview();
    }

    private void onSpinnerChange(ChangeEvent e) {
        // Keep min ≤ max as a soft guard. Don't reorder silently — just refresh.
        refreshPreview();
        fireChange();
    }

    private void refreshPreview() {
        ParamSpec spec = getSelectedParam();
        if (node == null || spec == null) {
            previewLabel.setText("Pick a parameter to preview values.");
            return;
        }
        double min = ((Number) minModel.getValue()).doubleValue();
        double max = ((Number) maxModel.getValue()).doubleValue();
        int steps = ((Number) stepsModel.getValue()).intValue();
        if (max < min) {
            double tmp = min; min = max; max = tmp;
        }
        List<Double> values = (spec.scale == Scale.LOG && min > 0.0 && max > 0.0)
                ? geometricSpacing(min, max, steps)
                : arithmeticSpacing(min, max, steps);
        StringBuilder sb = new StringBuilder("Values: ");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatNumber(values.get(i)));
        }
        previewLabel.setText(sb.toString());
    }

    private void fireChange() {
        if (listener != null) listener.onStateChanged();
    }

    private static String formatLabel(ParamSpec spec, double value) {
        if (spec.isInteger) {
            return String.format(Locale.US, "%s=%d", spec.argKey, (int) Math.round(value));
        }
        return String.format(Locale.US, "%s=%.2f", spec.argKey, value);
    }

    private static String formatNumber(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.format(Locale.US, "%.0f", v);
        }
        return String.format(Locale.US, "%.2f", v);
    }

    /**
     * Geometric (logarithmic) spacing of {@code steps} values from {@code min}
     * to {@code max} inclusive. Both endpoints must be positive.
     */
    public static List<Double> geometricSpacing(double min, double max, int steps) {
        if (steps < 1) return Collections.emptyList();
        List<Double> out = new ArrayList<Double>(steps);
        if (steps == 1) {
            out.add(min);
            return out;
        }
        double logMin = Math.log(min);
        double logMax = Math.log(max);
        double stride = (logMax - logMin) / (steps - 1);
        for (int i = 0; i < steps; i++) {
            out.add(Math.exp(logMin + i * stride));
        }
        return out;
    }

    /** Arithmetic (linear) spacing of {@code steps} values from {@code min} to {@code max} inclusive. */
    public static List<Double> arithmeticSpacing(double min, double max, int steps) {
        if (steps < 1) return Collections.emptyList();
        List<Double> out = new ArrayList<Double>(steps);
        if (steps == 1) {
            out.add(min);
            return out;
        }
        double stride = (max - min) / (steps - 1);
        for (int i = 0; i < steps; i++) {
            out.add(min + i * stride);
        }
        return out;
    }

    private static final class ParamSpecRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ParamSpec) {
                ParamSpec p = (ParamSpec) value;
                String unit = p.unit.isEmpty() ? "" : " " + p.unit;
                setText(p.name + " (" + p.argKey + unit + ")");
            }
            return this;
        }
    }
}
