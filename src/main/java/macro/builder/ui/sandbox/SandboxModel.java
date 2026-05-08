package macro.builder.ui.sandbox;

import macro.builder.image.FilterMacroParser.OpType;
import macro.builder.image.dag.Combiner;
import macro.builder.image.dag.CombinerOp;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

final class SandboxModel {
    static final int MAX_LINES = 4;

    final List<Line> lines = new ArrayList<Line>();
    final List<CombinerNode> combiners = new ArrayList<CombinerNode>();
    private final LinkedHashSet<Line> selectedLines = new LinkedHashSet<Line>();
    private Line selectionAnchorLine;
    Object selected;
    int primaryChannel = 1;
    int channelCount = 1;
    private int nextNode = 1;
    private int nextCombiner = 1;

    static SandboxModel fromDag(DagIR dag) {
        SandboxModel model = new SandboxModel();
        if (dag != null) {
            model.primaryChannel = dag.primaryChannel;
            for (int i = 0; i < dag.lines.size(); i++) {
                DagLine dagLine = dag.lines.get(i);
                Line line = new Line(dagLine.id, dagLine.name, dagLine.sourceChannel);
                for (int j = 0; j < dagLine.ops.size(); j++) {
                    DagNode node = dagLine.ops.get(j);
                    line.nodes.add(new Node(node.id.length() == 0 ? "node_" + model.nextNode++ : node.id,
                            node.type, node.args, node.commandName, node.menuPath));
                }
                model.lines.add(line);
            }
            for (int i = 0; i < dag.combiners.size(); i++) {
                Combiner c = dag.combiners.get(i);
                model.combiners.add(new CombinerNode(c.id, c.op, c.inputs));
            }
        }
        if (model.lines.isEmpty()) {
            model.lines.add(new Line("line_A", model.primaryChannel));
        }
        model.syncPrimaryLineSource();
        model.reseedCounters();
        model.selectLine(model.lines.get(0), false, false);
        return model;
    }

    DagIR toDag() {
        List<DagLine> dagLines = new ArrayList<DagLine>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            List<DagNode> nodes = new ArrayList<DagNode>();
            for (int j = 0; j < line.nodes.size(); j++) {
                Node node = line.nodes.get(j);
                nodes.add(new DagNode(node.id, node.type, node.args, node.commandName, node.menuPath));
            }
            dagLines.add(new DagLine(line.id, line.name, nodes, line.sourceChannel));
        }
        List<Combiner> dagCombiners = new ArrayList<Combiner>();
        for (int i = 0; i < combiners.size(); i++) {
            CombinerNode combiner = combiners.get(i);
            dagCombiners.add(new Combiner(combiner.id, combiner.op, combiner.inputs));
        }
        String output = dagCombiners.isEmpty()
                ? lines.get(0).id
                : dagCombiners.get(dagCombiners.size() - 1).id;
        return new DagIR(1, primaryChannel, dagLines, dagCombiners, output, executionTier(dagLines));
    }

    DagIR toPartialDag() {
        if (selected instanceof Node) {
            Node selectedNode = (Node) selected;
            List<DagLine> partialLines = new ArrayList<DagLine>();
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                List<DagNode> nodes = new ArrayList<DagNode>();
                for (int j = 0; j < line.nodes.size(); j++) {
                    Node node = line.nodes.get(j);
                    nodes.add(new DagNode(node.id, node.type, node.args, node.commandName, node.menuPath));
                    if (node == selectedNode) break;
                }
                partialLines.add(new DagLine(line.id, line.name, nodes, line.sourceChannel));
                if (line.nodes.contains(selectedNode)) {
                    return new DagIR(1, primaryChannel, partialLines, Collections.<Combiner>emptyList(),
                            line.id, executionTier(partialLines));
                }
            }
        }
        if (selected instanceof CombinerNode) {
            CombinerNode selectedCombiner = (CombinerNode) selected;
            List<Combiner> partialCombiners = new ArrayList<Combiner>();
            DagIR full = toDag();
            for (int i = 0; i < combiners.size(); i++) {
                CombinerNode c = combiners.get(i);
                partialCombiners.add(new Combiner(c.id, c.op, c.inputs));
                if (c == selectedCombiner) {
                    return new DagIR(1, primaryChannel, full.lines, partialCombiners, c.id, full.executionTier);
                }
            }
        }
        return toDag();
    }

    void addLine() {
        if (lines.size() >= MAX_LINES) return;
        Line line = new Line("line_" + (char) ('A' + lines.size()), primaryChannel);
        lines.add(line);
        syncPrimaryLineSource();
        selectLine(line, false, false);
    }

    Node addNode(Line line, FilterCatalog.Entry entry) {
        return addNode(line, entry, entry == null ? "" : entry.defaultArgs);
    }

    Node addNode(Line line, FilterCatalog.Entry entry, String args) {
        if (line == null || entry == null || entry.stub || entry.type == null) return null;
        Node node = new Node("node_" + nextNode++, entry.type, args,
                entry.legacy ? entry.commandName : "",
                entry.legacy ? entry.menuPath : "");
        line.nodes.add(node);
        selectNode(node);
        return node;
    }

    boolean hasLegacyNode() {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            for (int j = 0; j < line.nodes.size(); j++) {
                if (line.nodes.get(j).isLegacy()) return true;
            }
        }
        return false;
    }

    void removeNode(Line line, Node node) {
        if (line == null || node == null) return;
        line.nodes.remove(node);
        if (selected == node) selectLine(line, false, false);
    }

    void removeLine(Line line) {
        if (line == null || lines.size() <= 1) return;
        lines.remove(line);
        syncPrimaryLineSource();
        for (int i = combiners.size() - 1; i >= 0; i--) {
            if (combiners.get(i).inputs.contains(line.id)) combiners.remove(i);
        }
        selectedLines.remove(line);
        if (selectionAnchorLine == line) selectionAnchorLine = null;
        selectLine(lines.get(0), false, false);
    }

    void addCombiner() {
        if (lines.size() < 2) return;
        CombinerNode combiner = new CombinerNode("combiner_" + nextCombiner++, CombinerOp.AND,
                Arrays.asList(lines.get(0).id, lines.get(1).id));
        combiners.add(combiner);
        selectCombiner(combiner);
    }

    void addCombinerForSelectedLines() {
        List<Line> selected = selectedLinesInVisualOrder();
        if (selected.size() < 2) {
            addCombiner();
            return;
        }
        List<String> inputs = new ArrayList<String>();
        for (int i = 0; i < selected.size(); i++) {
            inputs.add(selected.get(i).id);
        }
        CombinerNode combiner = new CombinerNode("combiner_" + nextCombiner++, CombinerOp.AND, inputs);
        combiners.add(combiner);
        selectCombiner(combiner);
    }

    void removeCombiner(CombinerNode combiner) {
        combiners.remove(combiner);
        if (selected == combiner && !lines.isEmpty()) selectLine(lines.get(0), false, false);
    }

    void selectLine(Line line, boolean ctrl, boolean shift) {
        if (line == null) return;
        if (shift && selectionAnchorLine != null) {
            int a = lines.indexOf(selectionAnchorLine);
            int b = lines.indexOf(line);
            if (a >= 0 && b >= 0) {
                selectedLines.clear();
                int start = Math.min(a, b);
                int end = Math.max(a, b);
                for (int i = start; i <= end; i++) selectedLines.add(lines.get(i));
            } else {
                selectSingleLine(line);
            }
        } else if (ctrl) {
            if (!selectedLines.remove(line)) selectedLines.add(line);
            selectionAnchorLine = line;
        } else {
            selectSingleLine(line);
        }
        selected = line;
    }

    void selectNode(Node node) {
        if (node == null) return;
        clearLineSelection();
        selected = node;
    }

    void selectCombiner(CombinerNode combiner) {
        if (combiner == null) return;
        clearLineSelection();
        selected = combiner;
    }

    List<Line> selectedLinesInVisualOrder() {
        List<Line> out = new ArrayList<Line>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (selectedLines.contains(line)) out.add(line);
        }
        return out;
    }

    boolean isLineSelected(Line line) {
        return selectedLines.contains(line);
    }

    void setPrimaryChannel(int channel) {
        primaryChannel = clampChannel(channel);
        syncPrimaryLineSource();
    }

    void setLineSourceChannel(Line line, int channel) {
        if (line == null) return;
        if (!lines.isEmpty() && line == lines.get(0)) {
            line.sourceChannel = primaryChannel;
            return;
        }
        line.sourceChannel = clampChannel(channel);
    }

    void setLineName(Line line, String name) {
        if (line == null) return;
        line.name = name == null ? "" : name.trim();
    }

    void setChannelCount(int count) {
        channelCount = Math.max(1, count);
        primaryChannel = clampChannel(primaryChannel);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            line.sourceChannel = clampChannel(line.sourceChannel);
        }
        syncPrimaryLineSource();
    }

    private void selectSingleLine(Line line) {
        selectedLines.clear();
        selectedLines.add(line);
        selectionAnchorLine = line;
    }

    void clearLineSelection() {
        selectedLines.clear();
        selectionAnchorLine = null;
    }

    private void syncPrimaryLineSource() {
        if (!lines.isEmpty()) {
            lines.get(0).sourceChannel = primaryChannel;
        }
    }

    private int clampChannel(int channel) {
        if (channel < 1) return 1;
        if (channel > channelCount) return channelCount;
        return channel;
    }

    private void reseedCounters() {
        nextNode = 1;
        nextCombiner = 1;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            for (int j = 0; j < line.nodes.size(); j++) {
                nextNode++;
            }
        }
        nextCombiner = combiners.size() + 1;
    }

    private static String executionTier(List<DagLine> dagLines) {
        for (int i = 0; i < dagLines.size(); i++) {
            DagLine line = dagLines.get(i);
            for (int j = 0; j < line.ops.size(); j++) {
                DagNode node = line.ops.get(j);
                if (node.commandName.length() > 0 || node.type == OpType.UNKNOWN) {
                    return "legacy";
                }
            }
        }
        return "native";
    }

    static final class Line {
        final String id;
        String name;
        int sourceChannel;
        final List<Node> nodes = new ArrayList<Node>();

        Line(String id) {
            this(id, 1);
        }

        Line(String id, int sourceChannel) {
            this(id, "", sourceChannel);
        }

        Line(String id, String name, int sourceChannel) {
            this.id = id == null || id.trim().isEmpty() ? "line_A" : id;
            this.name = name == null ? "" : name.trim();
            this.sourceChannel = sourceChannel < 1 ? 1 : sourceChannel;
        }
    }

    static final class Node {
        final String id;
        OpType type;
        String args;
        String commandName;
        String menuPath;

        Node(String id, OpType type, String args) {
            this(id, type, args, "", "");
        }

        Node(String id, OpType type, String args, String commandName, String menuPath) {
            this.id = id == null || id.trim().isEmpty() ? "node" : id;
            this.type = type == null ? OpType.UNKNOWN : type;
            this.args = args == null ? "" : args;
            this.commandName = commandName == null ? "" : commandName;
            this.menuPath = menuPath == null ? "" : menuPath;
        }

        boolean isLegacy() {
            return commandName.length() > 0 || type == OpType.UNKNOWN;
        }
    }

    static final class CombinerNode {
        final String id;
        CombinerOp op;
        List<String> inputs;

        CombinerNode(String id, CombinerOp op, List<String> inputs) {
            this.id = id == null || id.trim().isEmpty() ? "combiner" : id;
            this.op = op == null ? CombinerOp.AND : op;
            this.inputs = inputs == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(inputs);
        }
    }
}
