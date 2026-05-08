package macro.builder.ui.sandbox;

import macro.builder.image.dag.DagToIjmEmitter;
import ij.measure.Calibration;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

final class StepEditorDialog {

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final List<ArgsEditorModel.Token> tokens;
    private final ArgsEditorModel.UnitContext units;
    private final List<FieldBinding> fields = new ArrayList<FieldBinding>();
    private final JTextArea rawOptions;

    private StepEditorDialog(SandboxModel.Node node, Calibration calibration) {
        String args = node == null ? "" : node.args;
        this.tokens = ArgsEditorModel.parse(args);
        this.units = ArgsEditorModel.UnitContext.fromCalibration(calibration);

        String command = displayName(node);
        addLabel(command, 0, 0, 2, true);

        if (ArgsEditorModel.hasEditableParameters(tokens)) {
            this.rawOptions = null;
            int row = 1;
            if (ArgsEditorModel.hasPhysicalParameters(tokens, units)) {
                addHint("Spatial values use source calibration and are saved as pixels for stable execution.", 0, row++, 2);
            } else if (ArgsEditorModel.hasPixelParameters(tokens, units)) {
                addHint("Spatial values use pixels, not microns.", 0, row++, 2);
            }
            for (int i = 0; i < tokens.size(); i++) {
                ArgsEditorModel.Token token = tokens.get(i);
                if (!token.isEditable()) continue;
                JLabel label = addLabel(ArgsEditorModel.displayLabel(token, units), 0, row, 1, false);
                String hint = ArgsEditorModel.unitHint(token, units);
                if (hint.length() > 0) label.setToolTipText(hint);
                String displayValue = ArgsEditorModel.displayValue(token, units);
                JTextField field = new JTextField(displayValue,
                        Math.max(8, Math.min(24, displayValue.length() + 4)));
                if (hint.length() > 0) field.setToolTipText(hint);
                fields.add(new FieldBinding(token, field, units));
                addField(field, 1, row);
                row++;
            }
        } else {
            addLabel("Raw options", 0, 1, 2, false);
            this.rawOptions = new JTextArea(args == null ? "" : args, 4, 34);
            this.rawOptions.setLineWrap(true);
            this.rawOptions.setWrapStyleWord(true);
            JScrollPane scroll = new JScrollPane(this.rawOptions);
            scroll.setBorder(BorderFactory.createEtchedBorder());
            addWide(scroll, 0, 2);
        }
    }

    static boolean show(Component parent, SandboxModel.Node node) {
        return show(parent, node, null);
    }

    static boolean show(Component parent, SandboxModel.Node node, Calibration calibration) {
        if (node == null) return false;
        String original = node.args == null ? "" : node.args;
        StepEditorDialog editor = new StepEditorDialog(node, calibration);
        int result = JOptionPane.showConfirmDialog(parent,
                editor.panel,
                "Edit parameters",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return false;
        String updated = editor.render().trim();
        if (updated.equals(original)) return false;
        node.args = updated;
        return true;
    }

    private String render() {
        if (rawOptions != null) return rawOptions.getText();
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).apply();
        }
        return ArgsEditorModel.render(tokens);
    }

    private JLabel addLabel(String text, int x, int y, int width, boolean header) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(y == 0 ? 4 : 2, 4, 2, 4);
        JLabel label = new JLabel(text == null ? "" : text);
        if (header) label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        panel.add(label, gbc);
        return label;
    }

    private void addHint(String text, int x, int y, int width) {
        JLabel hint = addLabel(text, x, y, width, false);
        hint.setForeground(new java.awt.Color(80, 80, 80));
    }

    private void addField(JTextField field, int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        panel.add(field, gbc);
    }

    private void addWide(Component component, int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);
        panel.add(component, gbc);
    }

    private static String displayName(SandboxModel.Node node) {
        if (node == null) return "";
        String command = node.commandName.length() > 0
                ? node.commandName
                : DagToIjmEmitter.commandFor(node.type);
        return command == null ? node.type.name() : command;
    }

    private static final class FieldBinding {
        private final ArgsEditorModel.Token token;
        private final JTextField field;
        private final ArgsEditorModel.UnitContext units;

        FieldBinding(ArgsEditorModel.Token token, JTextField field,
                     ArgsEditorModel.UnitContext units) {
            this.token = token;
            this.field = field;
            this.units = units == null ? ArgsEditorModel.UnitContext.none() : units;
        }

        void apply() {
            token.setValue(ArgsEditorModel.storageValue(token, field.getText(), units));
        }
    }
}
