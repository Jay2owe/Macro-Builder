package macro.builder.ui.sandbox.variation;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.DagNode;
import macro.builder.image.variation.FilterCompatibility;
import macro.builder.image.variation.OpTypeParamRegistry;
import macro.builder.image.variation.VariantAxis;
import macro.builder.image.variation.VariantAxis.AlternativeValue;
import macro.builder.image.variation.VariantAxis.Kind;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sub-panel for "swap filter" mode: pick which alternative filter types should
 * substitute the chosen DAG node. Each ticked alternative gets the registry's
 * default args. Spits out a {@link VariantAxis} of {@link Kind#FILTER_SWAP}
 * alternatives (the baseline type is always excluded from the output even if
 * accidentally ticked).
 *
 * <p>Compatibility is decided by {@link FilterCompatibility}. The user may tick
 * 1–8 alternatives.
 */
public final class SwapPanel extends JPanel {

    /** Hard upper bound on ticked alternatives, to keep variant counts sane. */
    public static final int MAX_TICKED = 8;

    public interface StateListener {
        void onStateChanged();
    }

    private DagNode node;
    private final JLabel nodeLabel = new JLabel(" ");
    private final JLabel emptyHint = new JLabel(" ");
    private final JPanel checkboxColumn = new JPanel();
    private final Map<OpType, JCheckBox> checkboxes = new LinkedHashMap<OpType, JCheckBox>();
    private StateListener listener;

    public SwapPanel() {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        nodeLabel.setFont(nodeLabel.getFont().deriveFont(java.awt.Font.BOLD));
        emptyHint.setForeground(new java.awt.Color(120, 120, 120));

        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.add(nodeLabel, BorderLayout.NORTH);
        header.add(emptyHint, BorderLayout.SOUTH);

        checkboxColumn.setLayout(new BoxLayout(checkboxColumn, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(checkboxColumn,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEtchedBorder());
        scroll.setPreferredSize(new Dimension(280, 180));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void setStateListener(StateListener listener) {
        this.listener = listener;
    }

    public void setNode(DagNode node) {
        this.node = node;
        rebuildCheckboxes();
        fireChange();
    }

    public DagNode getNode() {
        return node;
    }

    /** Test convenience: tick exactly the given alternative types. Unticks others. */
    public void setTicked(OpType... types) {
        java.util.Set<OpType> wanted = new java.util.HashSet<OpType>();
        if (types != null) {
            for (OpType t : types) if (t != null) wanted.add(t);
        }
        for (Map.Entry<OpType, JCheckBox> entry : checkboxes.entrySet()) {
            if (node != null && entry.getKey() == node.type) {
                entry.getValue().setSelected(true);   // baseline stays ticked
            } else {
                entry.getValue().setSelected(wanted.contains(entry.getKey()));
            }
        }
        fireChange();
    }

    /** Number of distinct alternatives (excluding the baseline type) that are ticked. */
    public int alternativeCount() {
        if (node == null) return 0;
        int count = 0;
        for (Map.Entry<OpType, JCheckBox> entry : checkboxes.entrySet()) {
            if (entry.getValue().isSelected() && entry.getKey() != node.type) count++;
        }
        return Math.min(count, MAX_TICKED);
    }

    public boolean isReady() {
        return node != null && alternativeCount() >= 1;
    }

    /** Build the {@link VariantAxis} described by current dialog state. */
    public VariantAxis buildAxis() {
        if (node == null) {
            throw new IllegalStateException("no node selected");
        }
        List<AlternativeValue> alts = new ArrayList<AlternativeValue>();
        for (Map.Entry<OpType, JCheckBox> entry : checkboxes.entrySet()) {
            OpType alt = entry.getKey();
            if (alt == node.type) continue;             // skip baseline
            if (!entry.getValue().isSelected()) continue;
            String args = OpTypeParamRegistry.argsForDefaults(alt);
            alts.add(new AlternativeValue(alt.name(), alt, args));
            if (alts.size() >= MAX_TICKED) break;
        }
        return new VariantAxis(node.id, Kind.FILTER_SWAP, alts);
    }

    private void rebuildCheckboxes() {
        checkboxColumn.removeAll();
        checkboxes.clear();
        if (node == null) {
            nodeLabel.setText(" ");
            emptyHint.setText(" ");
            checkboxColumn.revalidate();
            checkboxColumn.repaint();
            return;
        }
        nodeLabel.setText(node.type.name() + "  [id=" + node.id + "]");
        List<OpType> compatible = FilterCompatibility.alternativesFor(node.type);
        if (compatible.isEmpty() || (compatible.size() == 1 && compatible.get(0) == node.type)) {
            emptyHint.setText("No alternative filters known for this op.");
        } else {
            emptyHint.setText("Tick 1–8 alternatives. Baseline type is shown but cannot be unticked.");
        }
        for (OpType type : compatible) {
            JCheckBox box = new JCheckBox(type == node.type
                    ? type.name() + "  (baseline)"
                    : type.name());
            if (type == node.type) {
                box.setSelected(true);
                box.setEnabled(false);
            } else {
                box.setSelected(false);
            }
            box.addActionListener(e -> {
                enforceCap(type, box);
                fireChange();
            });
            checkboxColumn.add(box);
            checkboxes.put(type, box);
        }
        checkboxColumn.revalidate();
        checkboxColumn.repaint();
    }

    private void enforceCap(OpType justClicked, JCheckBox box) {
        if (!box.isSelected()) return;
        int ticked = 0;
        for (Map.Entry<OpType, JCheckBox> entry : checkboxes.entrySet()) {
            if (entry.getKey() == node.type) continue;
            if (entry.getValue().isSelected()) ticked++;
        }
        if (ticked > MAX_TICKED) {
            box.setSelected(false);
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    private void fireChange() {
        if (listener != null) listener.onStateChanged();
    }
}
