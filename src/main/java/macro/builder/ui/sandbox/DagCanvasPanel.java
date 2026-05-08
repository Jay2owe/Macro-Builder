package macro.builder.ui.sandbox;

import macro.builder.image.dag.CombinerOp;
import macro.builder.image.dag.DagToIjmEmitter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class DagCanvasPanel extends JPanel {

    public interface CatalogSupplier {
        FilterCatalog.Entry getSelectedCatalogEntry();
    }

    public interface NodeCreator {
        boolean addNode(SandboxModel.Line line, FilterCatalog.Entry entry);
    }

    public interface NodeActionHandler {
        void editNode(SandboxModel.Line line, SandboxModel.Node node);
        void previewToNode(SandboxModel.Line line, SandboxModel.Node node);
    }

    public interface CombinerActionHandler {
        void editCombiner(SandboxModel.CombinerNode combiner);
        void previewToCombiner(SandboxModel.CombinerNode combiner);
    }

    private final SandboxModel model;
    private final CatalogSupplier catalogSupplier;
    private final NodeCreator nodeCreator;
    private final NodeActionHandler nodeActionHandler;
    private final CombinerActionHandler combinerActionHandler;
    private final Runnable selectionCallback;
    private final Runnable changeCallback;

    private SandboxModel.Line dragLine;
    private SandboxModel.Node dragNode;

    public DagCanvasPanel(SandboxModel model, CatalogSupplier catalogSupplier,
                          NodeCreator nodeCreator,
                          Runnable selectionCallback, Runnable changeCallback) {
        this(model, catalogSupplier, nodeCreator, null, null, selectionCallback, changeCallback);
    }

    public DagCanvasPanel(SandboxModel model, CatalogSupplier catalogSupplier,
                          NodeCreator nodeCreator,
                          NodeActionHandler nodeActionHandler,
                          Runnable selectionCallback, Runnable changeCallback) {
        this(model, catalogSupplier, nodeCreator, nodeActionHandler, null, selectionCallback, changeCallback);
    }

    public DagCanvasPanel(SandboxModel model, CatalogSupplier catalogSupplier,
                          NodeCreator nodeCreator,
                          NodeActionHandler nodeActionHandler,
                          CombinerActionHandler combinerActionHandler,
                          Runnable selectionCallback, Runnable changeCallback) {
        super(new GridBagLayout());
        this.model = model;
        this.catalogSupplier = catalogSupplier;
        this.nodeCreator = nodeCreator;
        this.nodeActionHandler = nodeActionHandler;
        this.combinerActionHandler = combinerActionHandler;
        this.selectionCallback = selectionCallback;
        this.changeCallback = changeCallback;
        setBorder(BorderFactory.createTitledBorder("Your filter"));
        rebuild();
    }

    public void rebuild() {
        removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.NORTH;
        for (int i = 0; i < model.lines.size(); i++) {
            gbc.gridx = i;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            add(buildLinePanel(model.lines.get(i)), gbc);
        }

        gbc.gridx = model.lines.size();
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        JButton addLine = new JButton("+ Add parallel branch");
        addLine.setEnabled(model.lines.size() < SandboxModel.MAX_LINES);
        addLine.addActionListener(e -> {
            model.addLine();
            changedAndSelected();
        });
        add(addLine, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = model.lines.size() + 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(buildCombinerRow(), gbc);

        revalidate();
        repaint();
    }

    private JPanel buildLinePanel(final SandboxModel.Line line) {
        JPanel panel = new JPanel(new GridBagLayout());
        boolean lineSelected = model.isLineSelected(line);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(lineSelected ? new Color(45, 110, 200) : new Color(180, 180, 180),
                        lineSelected ? 3 : 2),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        panel.setBackground(lineSelected ? new Color(232, 242, 255) : new Color(250, 250, 250));
        MouseAdapter lineSelectionListener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                model.selectLine(line, e.isControlDown(), e.isShiftDown());
                selected();
            }
        };
        panel.addMouseListener(lineSelectionListener);

        GridBagConstraints gbc = baseGbc();
        gbc.gridy = 0;
        int branchIndex = model.lines.indexOf(line) + 1;
        JLabel title = new JLabel("Branch " + branchIndex + " - " + sourceLabel(line));
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
        title.addMouseListener(lineSelectionListener);
        panel.add(title, gbc);

        if (model.channelCount > 1) {
            final JComboBox<String> source = new JComboBox<String>(channelLabels());
            source.setSelectedIndex(Math.max(0, line.sourceChannel - 1));
            source.setEnabled(branchIndex != 1);
            source.setToolTipText(branchIndex == 1
                    ? "The first branch follows the primary channel"
                    : "Source channel for this branch");
            source.addActionListener(e -> {
                model.setLineSourceChannel(line, source.getSelectedIndex() + 1);
                changedAndSelected();
            });
            gbc.gridx = 1;
            panel.add(source, gbc);
        }

        JButton deleteLine = new JButton("x");
        deleteLine.setEnabled(model.lines.size() > 1);
        deleteLine.addActionListener(e -> {
            model.removeLine(line);
            changedAndSelected();
        });
        gbc.gridx = 2;
        panel.add(deleteLine, gbc);

        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.gridy = 1;
        panel.add(buildNameField(line), gbc);

        for (int i = 0; i < line.nodes.size(); i++) {
            gbc.gridy = i + 2;
            panel.add(buildNodeCard(line, line.nodes.get(i)), gbc);
        }

        gbc.gridy = line.nodes.size() + 2;
        JButton addNode = new JButton("+ Add step");
        addNode.addActionListener(e -> {
            FilterCatalog.Entry entry = catalogSupplier == null ? null : catalogSupplier.getSelectedCatalogEntry();
            boolean added;
            if (nodeCreator != null) {
                added = nodeCreator.addNode(line, entry);
            } else {
                added = model.addNode(line, entry) != null;
            }
            if (added) changedAndSelected();
        });
        panel.add(addNode, gbc);
        return panel;
    }

    private JPanel buildNameField(final SandboxModel.Line line) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints gbc = baseGbc();
        gbc.insets = new Insets(0, 0, 0, 4);
        row.add(new JLabel("Name:"), gbc);

        final JTextField name = new JTextField(line.name, 10);
        name.setToolTipText("Optional branch name");
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
            private void update() {
                model.setLineName(line, name.getText());
                if (changeCallback != null) changeCallback.run();
            }
        });
        name.addActionListener(e -> rebuild());
        name.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                rebuild();
            }
        });
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row.add(name, gbc);
        return row;
    }

    private String sourceLabel(SandboxModel.Line line) {
        int index = model.lines.indexOf(line);
        if (index == 0 && line.sourceChannel == model.primaryChannel) {
            return "Primary C" + model.primaryChannel;
        }
        return "C" + line.sourceChannel;
    }

    private String[] channelLabels() {
        String[] labels = new String[Math.max(1, model.channelCount)];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = "C" + (i + 1);
        }
        return labels;
    }

    private JPanel buildNodeCard(final SandboxModel.Line line, final SandboxModel.Node node) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(model.selected == node ? new Color(70, 120, 200) : new Color(205, 205, 205), 2),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        card.setBackground(Color.WHITE);

        String command = node.commandName.length() > 0 ? node.commandName : DagToIjmEmitter.commandFor(node.type);
        String displayName = command == null ? node.type.name() : command;
        String badge = node.isLegacy() ? " [legacy]" : "";
        JLabel label = new JLabel("<html><b>" + html(displayName + badge)
                + "</b><br><span style='font-size:9px;'>" + html(node.args) + "</span></html>");
        String tooltip = node.isLegacy()
                ? "Runs through Fiji's slower single-image path"
                : "Runs through the fast batched path";
        label.setToolTipText(tooltip);
        card.setToolTipText(tooltip);
        GridBagConstraints gbc = baseGbc();
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        card.add(label, gbc);

        JButton delete = new JButton("x");
        delete.addActionListener(e -> {
            model.removeNode(line, node);
            changedAndSelected();
        });
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        card.add(delete, gbc);

        MouseAdapter adapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (showNodeMenuIfRequested(e, line, node)) return;
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (e.getClickCount() == 2) {
                    dragLine = null;
                    dragNode = null;
                    model.selectNode(node);
                    selected();
                    if (nodeActionHandler != null) nodeActionHandler.editNode(line, node);
                    return;
                }
                dragLine = line;
                dragNode = node;
                model.selectNode(node);
                selected();
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (showNodeMenuIfRequested(e, line, node)) {
                    dragLine = null;
                    dragNode = null;
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    dragLine = null;
                    dragNode = null;
                    return;
                }
                if (dragLine == null || dragNode == null) return;
                Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(),
                        DagCanvasPanel.this);
                int newIndex = yToIndex(dragLine, p.y);
                int oldIndex = dragLine.nodes.indexOf(dragNode);
                if (oldIndex >= 0 && newIndex >= 0 && newIndex != oldIndex) {
                    dragLine.nodes.remove(oldIndex);
                    if (newIndex > dragLine.nodes.size()) newIndex = dragLine.nodes.size();
                    dragLine.nodes.add(newIndex, dragNode);
                    changedAndSelected();
                }
                dragLine = null;
                dragNode = null;
            }
        };
        card.addMouseListener(adapter);
        label.addMouseListener(adapter);
        return card;
    }

    private boolean showNodeMenuIfRequested(MouseEvent e, SandboxModel.Line line, SandboxModel.Node node) {
        if (!e.isPopupTrigger()) return false;
        Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), this);
        model.selectNode(node);
        selected();
        buildNodeMenu(line, node).show(this, p.x, p.y);
        return true;
    }

    private JPopupMenu buildNodeMenu(final SandboxModel.Line line, final SandboxModel.Node node) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Edit parameters", new Runnable() {
            @Override public void run() {
                if (nodeActionHandler != null) nodeActionHandler.editNode(line, node);
            }
        }));
        menu.add(menuItem("Preview to here", new Runnable() {
            @Override public void run() {
                if (nodeActionHandler != null) nodeActionHandler.previewToNode(line, node);
            }
        }));
        menu.addSeparator();
        menu.add(menuItem("Delete", new Runnable() {
            @Override public void run() {
                model.removeNode(line, node);
                changedAndSelected();
            }
        }));
        return menu;
    }

    private static JMenuItem menuItem(String label, final Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> {
            if (action != null) action.run();
        });
        return item;
    }

    private static String html(String text) {
        if (text == null) return "";
        String escaped = text.replace("&", "&amp;");
        escaped = escaped.replace("<", "&lt;");
        escaped = escaped.replace(">", "&gt;");
        escaped = escaped.replace("\"", "&quot;");
        return escaped;
    }

    private int yToIndex(SandboxModel.Line line, int y) {
        int lineIndex = model.lines.indexOf(line);
        if (lineIndex < 0) return -1;
        Component linePanel = getComponent(lineIndex);
        Point origin = linePanel.getLocation();
        int relative = y - origin.y - 34;
        int index = relative / 72;
        if (index < 0) index = 0;
        if (index >= line.nodes.size()) index = line.nodes.size() - 1;
        return index;
    }

    private JPanel buildCombinerRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(8, 0, 0, 0)));
        GridBagConstraints gbc = baseGbc();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        for (int i = 0; i < model.combiners.size(); i++) {
            final SandboxModel.CombinerNode combiner = model.combiners.get(i);
            gbc.gridx = i;
            row.add(buildCombinerCard(combiner), gbc);
        }
        gbc.gridx = model.combiners.size();
        gbc.weightx = 0.0;
        JButton addCombiner = new JButton(model.selectedLinesInVisualOrder().size() >= 2
                ? "+ Merge selected branches"
                : "+ Merge branches");
        addCombiner.setEnabled(model.lines.size() >= 2);
        addCombiner.addActionListener(e -> {
            model.addCombinerForSelectedLines();
            changedAndSelected();
        });
        row.add(addCombiner, gbc);
        if (model.lines.size() < 2) {
            gbc.gridx++;
            gbc.weightx = 1.0;
            JLabel hint = new JLabel("Add a 2nd branch to combine two channels here.");
            hint.setForeground(new Color(110, 110, 110));
            hint.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            row.add(hint, gbc);
        }
        return row;
    }

    private JPanel buildCombinerCard(final SandboxModel.CombinerNode combiner) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(model.selected == combiner ? new Color(70, 120, 200) : new Color(205, 205, 205), 2),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        card.setBackground(Color.WHITE);
        GridBagConstraints gbc = baseGbc();
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel label = new JLabel(combinerLabel(combiner));
        card.add(label, gbc);
        JButton delete = new JButton("x");
        delete.addActionListener(e -> {
            model.removeCombiner(combiner);
            changedAndSelected();
        });
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        card.add(delete, gbc);

        MouseAdapter adapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (showCombinerMenuIfRequested(e, combiner)) return;
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                model.selectCombiner(combiner);
                selected();
                if (e.getClickCount() == 2 && combinerActionHandler != null) {
                    combinerActionHandler.editCombiner(combiner);
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                showCombinerMenuIfRequested(e, combiner);
            }
        };
        card.addMouseListener(adapter);
        label.addMouseListener(adapter);
        return card;
    }

    private String combinerLabel(SandboxModel.CombinerNode combiner) {
        if (combiner == null) return "Merge";
        if (combiner.op == CombinerOp.SUBTRACT) {
            return "Subtract: " + joinBranchInputs(combiner.inputs, " minus ");
        }
        return combiner.op + ": " + joinBranchInputs(combiner.inputs, " then ");
    }

    private String joinBranchInputs(java.util.List<String> inputs, String separator) {
        if (inputs == null || inputs.isEmpty()) return "no inputs";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(branchLabel(inputs.get(i)));
        }
        return sb.toString();
    }

    private String branchLabel(String lineId) {
        for (int i = 0; i < model.lines.size(); i++) {
            SandboxModel.Line line = model.lines.get(i);
            if (line.id.equals(lineId)) return branchLabel(line, i);
        }
        return lineId == null ? "unknown branch" : lineId;
    }

    private String branchLabel(SandboxModel.Line line, int index) {
        String base = "Branch " + (index + 1);
        if (line == null || line.name == null || line.name.trim().length() == 0) return base;
        return base + ": " + line.name.trim();
    }

    private boolean showCombinerMenuIfRequested(MouseEvent e, SandboxModel.CombinerNode combiner) {
        if (!e.isPopupTrigger()) return false;
        Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), this);
        model.selectCombiner(combiner);
        selected();
        buildCombinerMenu(combiner).show(this, p.x, p.y);
        return true;
    }

    private JPopupMenu buildCombinerMenu(final SandboxModel.CombinerNode combiner) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Edit merge", new Runnable() {
            @Override public void run() {
                if (combinerActionHandler != null) combinerActionHandler.editCombiner(combiner);
            }
        }));
        menu.add(menuItem("Preview to merge", new Runnable() {
            @Override public void run() {
                if (combinerActionHandler != null) combinerActionHandler.previewToCombiner(combiner);
            }
        }));
        menu.addSeparator();
        menu.add(menuItem("Delete", new Runnable() {
            @Override public void run() {
                model.removeCombiner(combiner);
                changedAndSelected();
            }
        }));
        return menu;
    }

    private GridBagConstraints baseGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private void selected() {
        if (selectionCallback != null) selectionCallback.run();
        rebuild();
    }

    private void changedAndSelected() {
        if (changeCallback != null) changeCallback.run();
        selected();
    }
}
