package macro.builder.macro;

import macro.builder.analysis.ShootoutResult;
import macro.builder.analysis.ShootoutSettings;
import macro.builder.image.FilterMacroParser;
import macro.builder.image.dag.DagIR;
import macro.builder.image.dag.DagLine;
import macro.builder.image.dag.DagNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MacroApplier {
    private MacroApplier() {
    }

    public static String applyToIjm(
            String originalIjm,
            ShootoutResult chosen,
            ShootoutSettings settings,
            Range range) {
        if (chosen == null) {
            throw new IllegalArgumentException("chosen result must not be null");
        }
        if (!chosen.isSuccess()) {
            throw new IllegalArgumentException("chosen result must be successful");
        }
        String line = thresholdLine(chosen, settings, range);
        String base = originalIjm == null ? "" : originalIjm;
        StringBuilder sb = new StringBuilder(base.length() + line.length() + 2);
        sb.append(base);
        if (sb.length() > 0 && !endsWithLineBreak(sb)) {
            sb.append('\n');
        }
        sb.append(line).append('\n');
        return sb.toString();
    }

    public static DagIR applyToDag(DagIR dag, ShootoutResult chosen, ShootoutSettings settings) {
        if (dag == null) {
            throw new IllegalArgumentException("DAG must not be null");
        }
        if (chosen == null) {
            throw new IllegalArgumentException("chosen result must not be null");
        }
        if (!chosen.isSuccess()) {
            throw new IllegalArgumentException("chosen result must be successful");
        }

        String args = thresholdArgs(chosen, settings, rangeFor(chosen));
        List<DagLine> lines = new ArrayList<DagLine>(dag.lines.size());
        boolean replaced = false;
        for (int i = 0; i < dag.lines.size(); i++) {
            DagLine line = dag.lines.get(i);
            List<DagNode> ops = new ArrayList<DagNode>(line.ops.size() + 1);
            for (int j = 0; j < line.ops.size(); j++) {
                DagNode node = line.ops.get(j);
                if (!replaced && node.type == FilterMacroParser.OpType.THRESHOLD) {
                    ops.add(new DagNode(
                            node.id,
                            FilterMacroParser.OpType.THRESHOLD,
                            args,
                            node.commandName,
                            node.menuPath));
                    replaced = true;
                } else {
                    ops.add(node);
                }
            }
            lines.add(new DagLine(line.id, line.name, ops, line.sourceChannel));
        }

        if (!replaced) {
            if (lines.isEmpty()) {
                lines.add(new DagLine(
                        "line_threshold",
                        "",
                        singleThresholdNode("threshold_1", args),
                        Math.max(1, dag.primaryChannel)));
            } else {
                int index = targetLineIndex(dag);
                DagLine line = lines.get(index);
                List<DagNode> ops = new ArrayList<DagNode>(line.ops);
                ops.add(new DagNode(uniqueThresholdNodeId(dag), FilterMacroParser.OpType.THRESHOLD, args));
                lines.set(index, new DagLine(line.id, line.name, ops, line.sourceChannel));
            }
        }

        String output = dag.output;
        if (output == null || output.length() == 0) {
            output = lines.get(0).id;
        }
        DagIR updated = new DagIR(
                dag.version,
                dag.primaryChannel,
                lines,
                dag.combiners,
                output,
                dag.executionTier);
        recordDagUndo(dag, updated);
        return updated;
    }

    public static String thresholdArgs(
            ShootoutResult chosen,
            ShootoutSettings settings,
            Range range) {
        if (chosen.source == ShootoutResult.Source.AUTO) {
            StringBuilder sb = new StringBuilder();
            sb.append("mode=auto method=").append(chosen.variant);
            if (settings != null && settings.darkBackground) {
                sb.append(" background=dark");
            } else {
                sb.append(" background=light");
            }
            return sb.toString();
        }
        if (chosen.thresholdValue == null) {
            throw new IllegalArgumentException("fixed/grid result needs a threshold value");
        }
        double upper = upperBound(chosen, range);
        return "mode=fixed lower=" + formatNumber(chosen.thresholdValue.doubleValue())
                + " upper=" + formatNumber(upper);
    }

    public static String thresholdLine(
            ShootoutResult chosen,
            ShootoutSettings settings,
            Range range) {
        if (chosen.source == ShootoutResult.Source.AUTO) {
            String suffix = settings != null && settings.darkBackground ? " dark" : "";
            return "setAutoThreshold(\"" + escapeIjmString(chosen.variant + suffix) + "\");";
        }
        if (chosen.thresholdValue == null) {
            throw new IllegalArgumentException("fixed/grid result needs a threshold value");
        }
        return "setThreshold(" + formatNumber(chosen.thresholdValue.doubleValue())
                + ", " + formatNumber(upperBound(chosen, range)) + ");";
    }

    public static Range rangeFor(ShootoutResult result) {
        if (result == null) {
            return null;
        }
        return new Range(result.imageMinimum, result.imageMaximum);
    }

    public static String formatNumber(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0.0 ? "Infinity" : "-Infinity";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1000000000000000.0) {
            return Long.toString(Math.round(value));
        }
        String formatted = String.format(Locale.ROOT, "%.6f", value);
        while (formatted.indexOf('.') >= 0 && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static double upperBound(ShootoutResult chosen, Range range) {
        if (range != null && isFinite(range.maximum)) {
            return range.maximum;
        }
        if (chosen != null && isFinite(chosen.imageMaximum)) {
            return chosen.imageMaximum;
        }
        return chosen == null || chosen.thresholdValue == null
                ? 255.0
                : chosen.thresholdValue.doubleValue();
    }

    private static boolean endsWithLineBreak(StringBuilder sb) {
        char c = sb.charAt(sb.length() - 1);
        return c == '\n' || c == '\r';
    }

    private static String escapeIjmString(String text) {
        String value = text == null ? "" : text;
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\r': sb.append("\\r"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int j = hex.length(); j < 4; j++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static List<DagNode> singleThresholdNode(String id, String args) {
        List<DagNode> nodes = new ArrayList<DagNode>(1);
        nodes.add(new DagNode(id, FilterMacroParser.OpType.THRESHOLD, args));
        return nodes;
    }

    private static int targetLineIndex(DagIR dag) {
        if (dag.output != null && dag.output.length() > 0) {
            for (int i = 0; i < dag.lines.size(); i++) {
                if (dag.output.equals(dag.lines.get(i).id)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static String uniqueThresholdNodeId(DagIR dag) {
        int index = 1;
        while (containsNodeId(dag, "threshold_" + index)) {
            index++;
        }
        return "threshold_" + index;
    }

    private static boolean containsNodeId(DagIR dag, String id) {
        for (DagLine line : dag.lines) {
            for (DagNode node : line.ops) {
                if (id.equals(node.id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void recordDagUndo(DagIR before, DagIR after) {
        try {
            Class<?> historyClass = Class.forName("macro.builder.ui.sandbox.DagUndoHistory");
            Constructor<?> constructor = historyClass.getDeclaredConstructor(DagIR.class);
            constructor.setAccessible(true);
            Object history = constructor.newInstance(before);
            Method record = historyClass.getDeclaredMethod("record", DagIR.class);
            record.setAccessible(true);
            record.invoke(history, after);
        } catch (ClassNotFoundException ignored) {
            // Clean public builds may not include the sandbox undo helper yet.
        } catch (Exception ex) {
            throw new IllegalStateException("Could not record DAG undo state", ex);
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class Range {
        public final double minimum;
        public final double maximum;

        public Range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }
}
