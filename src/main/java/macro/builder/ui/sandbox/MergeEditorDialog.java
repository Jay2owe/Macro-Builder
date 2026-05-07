package macro.builder.ui.sandbox;

import macro.builder.image.dag.CombinerOp;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

final class MergeEditorDialog {

    private final SandboxModel model;
    private final SandboxModel.CombinerNode combiner;
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JComboBox<CombinerOp> operation = new JComboBox<CombinerOp>(CombinerOp.values());
    private final List<InputBinding> inputBindings = new ArrayList<InputBinding>();
    private final JLabel orderHint = new JLabel(" ");
    private final JLabel validation = new JLabel(" ");
    private JButton ok;
    private boolean accepted;
    private boolean changed;

    private MergeEditorDialog(SandboxModel model, SandboxModel.CombinerNode combiner) {
        this.model = model;
        this.combiner = combiner;
        buildPanel();
    }

    static boolean show(Component parent, SandboxModel model, SandboxModel.CombinerNode combiner) {
        if (model == null || combiner == null) return false;
        final MergeEditorDialog editor = new MergeEditorDialog(model, combiner);
        final JDialog dialog = createDialog(parent, "Edit merge");

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(editor.panel, BorderLayout.CENTER);
        root.add(editor.buildButtons(dialog), BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(editor.ok);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        editor.refreshValidation();
        dialog.setVisible(true);
        return editor.accepted && editor.changed;
    }

    static boolean hasEnoughInputs(List<String> inputs) {
        if (inputs == null) return false;
        List<String> unique = new ArrayList<String>();
        for (int i = 0; i < inputs.size(); i++) {
            String input = inputs.get(i);
            if (input == null || input.trim().length() == 0) continue;
            if (!unique.contains(input)) unique.add(input);
        }
        return unique.size() >= 2;
    }

    static List<String> orderedSelectedInputs(List<String> currentInputs, List<String> selectedLineIds) {
        List<String> ordered = new ArrayList<String>();
        appendSelected(ordered, currentInputs, selectedLineIds);
        appendSelected(ordered, selectedLineIds, selectedLineIds);
        return ordered;
    }

    static boolean applySelection(SandboxModel.CombinerNode combiner,
                                  CombinerOp operation,
                                  List<String> inputs) {
        if (combiner == null) return false;
        if (!hasEnoughInputs(inputs)) {
            throw new IllegalArgumentException("A merge needs at least two inputs.");
        }
        CombinerOp nextOp = operation == null ? CombinerOp.AND : operation;
        List<String> nextInputs = new ArrayList<String>(inputs);
        boolean changed = combiner.op != nextOp || !combiner.inputs.equals(nextInputs);
        combiner.op = nextOp;
        combiner.inputs = nextInputs;
        return changed;
    }

    private JPanel buildButtons(final JDialog dialog) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancel = new JButton("Cancel");
        this.ok = new JButton("OK");
        cancel.addActionListener(e -> {
            accepted = false;
            dialog.dispose();
        });
        ok.addActionListener(e -> {
            try {
                changed = applySelection(combiner, (CombinerOp) operation.getSelectedItem(), selectedInputs());
                accepted = true;
                dialog.dispose();
            } catch (IllegalArgumentException ex) {
                refreshValidation();
            }
        });
        buttons.add(cancel);
        buttons.add(ok);
        return buttons;
    }

    private void buildPanel() {
        addLabel("Operation", 0, 0, 1, false);
        operation.setSelectedItem(combiner.op);
        operation.addActionListener(e -> refreshValidation());
        addField(operation, 1, 0);

        addLabel("Inputs", 0, 1, 2, false);
        int row = 2;
        for (int i = 0; i < model.lines.size(); i++) {
            SandboxModel.Line line = model.lines.get(i);
            JCheckBox box = new JCheckBox(branchLabel(line) + " (" + line.id + ")",
                    combiner.inputs.contains(line.id));
            box.addActionListener(e -> refreshValidation());
            inputBindings.add(new InputBinding(line.id, box));
            addField(box, 0, row++);
        }

        orderHint.setForeground(new Color(80, 80, 80));
        addField(orderHint, 0, row++);

        validation.setForeground(new Color(150, 30, 30));
        addField(validation, 0, row);
    }

    private List<String> selectedInputs() {
        List<String> selected = new ArrayList<String>();
        for (int i = 0; i < inputBindings.size(); i++) {
            InputBinding binding = inputBindings.get(i);
            if (binding.box.isSelected()) selected.add(binding.lineId);
        }
        return orderedSelectedInputs(combiner.inputs, selected);
    }

    private void refreshValidation() {
        List<String> selectedInputs = selectedInputs();
        boolean valid = hasEnoughInputs(selectedInputs);
        if (operation.getSelectedItem() == CombinerOp.SUBTRACT && valid) {
            orderHint.setText("Subtract order: " + describeInputs(selectedInputs, " minus "));
        } else if (operation.getSelectedItem() == CombinerOp.SUBTRACT) {
            orderHint.setText("Subtract uses input order: first input minus later inputs.");
        } else {
            orderHint.setText(" ");
        }
        validation.setText(valid ? " " : "Select at least two inputs.");
        if (ok != null) ok.setEnabled(valid);
    }

    private String branchLabel(SandboxModel.Line line) {
        int index = model.lines.indexOf(line);
        String source = index == 0 && line.sourceChannel == model.primaryChannel
                ? "Primary C" + model.primaryChannel
                : "C" + line.sourceChannel;
        return "Branch " + (index + 1) + " - " + source;
    }

    private String describeInputs(List<String> inputs, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(branchName(inputs.get(i)));
        }
        return sb.toString();
    }

    private String branchName(String lineId) {
        for (int i = 0; i < model.lines.size(); i++) {
            if (model.lines.get(i).id.equals(lineId)) return "Branch " + (i + 1);
        }
        return lineId == null ? "unknown branch" : lineId;
    }

    private void addLabel(String text, int x, int y, int width, boolean header) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(y == 0 ? 4 : 2, 4, 2, 4);
        JLabel label = new JLabel(text == null ? "" : text);
        if (header) label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        panel.add(label, gbc);
    }

    private void addField(Component field, int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = x == 0 ? 2 : 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        panel.add(field, gbc);
    }

    private static void appendSelected(List<String> output, List<String> order, List<String> selected) {
        if (order == null || selected == null) return;
        for (int i = 0; i < order.size(); i++) {
            String candidate = order.get(i);
            if (candidate == null || !selected.contains(candidate) || output.contains(candidate)) continue;
            output.add(candidate);
        }
    }

    private static JDialog createDialog(Component parent, String title) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner instanceof Frame) return new JDialog((Frame) owner, title, true);
        if (owner instanceof Dialog) return new JDialog((Dialog) owner, title, true);
        return new JDialog((Frame) null, title, true);
    }

    private static final class InputBinding {
        private final String lineId;
        private final JCheckBox box;

        InputBinding(String lineId, JCheckBox box) {
            this.lineId = lineId;
            this.box = box;
        }
    }
}
