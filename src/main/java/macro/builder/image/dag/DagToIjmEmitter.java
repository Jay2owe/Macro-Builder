package macro.builder.image.dag;

import macro.builder.image.FilterMacroParser.OpType;

/**
 * Emits a Sandbox DAG as an IJ1 macro fallback.
 *
 * The native executor is still preferred for Sandbox-authored filters. This
 * macro exists so saved custom filters remain portable through the existing
 * legacy macro path.
 */
public final class DagToIjmEmitter {

    private DagToIjmEmitter() {}

    public static String emit(DagIR dag) {
        return emitPortable(dag, true, true);
    }

    public static String emitReadable(DagIR dag) {
        if (dag == null) throw new IllegalArgumentException("DAG must not be null");
        if (isSimpleLinear(dag)) {
            return emitLinearOps(dag.lines.get(0), false);
        }
        return emitPortable(dag, false, false);
    }

    public static String emitExecutable(DagIR dag) {
        if (dag == null) throw new IllegalArgumentException("DAG must not be null");
        return isSimpleLinear(dag) ? emitReadable(dag) : emit(dag);
    }

    private static boolean isSimpleLinear(DagIR dag) {
        if (dag == null || dag.lines.size() != 1 || !dag.combiners.isEmpty()) return false;
        DagLine line = dag.lines.get(0);
        return line != null
                && line.id.equals(dag.output)
                && line.sourceChannel == dag.primaryChannel;
    }

    private static String emitPortable(DagIR dag, boolean includeEmbeddedDag, boolean includeUnknownComments) {
        if (dag == null) throw new IllegalArgumentException("DAG must not be null");
        StringBuilder sb = new StringBuilder();
        if (includeEmbeddedDag) {
            sb.append("// @ihf-dag v1 executionTier=").append(dag.executionTier).append('\n');
            sb.append("// ").append(DagIRSerializer.toJson(dag)).append('\n');
        }
        sb.append("source_id = getImageID();\n");
        sb.append("getDimensions(width, height, channels, slices, frames);\n");
        sb.append("getPixelSize(mb_unit, mb_pixel_width, mb_pixel_height, mb_voxel_depth);\n");
        sb.append("function mb_restore_calibration() {\n");
        sb.append("    setVoxelSize(mb_pixel_width, mb_pixel_height, mb_voxel_depth, mb_unit);\n");
        sb.append("}\n");

        for (int i = 0; i < dag.lines.size(); i++) {
            DagLine line = dag.lines.get(i);
            String lineId = macroIdentifier(line.id);
            int channel = Math.max(1, line.sourceChannel);
            sb.append("selectImage(source_id);\n");
            sb.append("line_range = \"channels=").append(channel).append("-").append(channel)
                    .append(" slices=1-\" + slices + \" frames=1-\" + frames;\n");
            sb.append("run(\"Duplicate...\", \"title=").append(escapeMacroArg(lineId))
                    .append(" duplicate \" + line_range);\n");
            sb.append("mb_restore_calibration();\n");
            sb.append(lineId).append(" = getImageID();\n");
            sb.append(emitLineOps(line, includeUnknownComments, true));
        }

        for (int i = 0; i < dag.combiners.size(); i++) {
            Combiner combiner = dag.combiners.get(i);
            if (combiner.inputs.size() < 2) continue;
            String currentTitle = macroIdentifier(combiner.inputs.get(0));
            for (int j = 1; j < combiner.inputs.size(); j++) {
                String nextTitle = macroIdentifier(combiner.inputs.get(j));
                sb.append("imageCalculator(\"").append(imageCalculatorCommand(combiner.op))
                        .append(" create\", \"").append(escapeMacroArg(currentTitle))
                        .append("\", \"").append(escapeMacroArg(nextTitle)).append("\");\n");
                String outTitle = macroIdentifier(combiner.id);
                if (j < combiner.inputs.size() - 1) {
                    outTitle = macroIdentifier(combiner.id + "_" + j);
                }
                sb.append("rename(\"").append(escapeMacroArg(outTitle)).append("\");\n");
                sb.append("mb_restore_calibration();\n");
                currentTitle = outTitle;
            }
            sb.append(macroIdentifier(combiner.id)).append(" = getImageID();\n");
        }

        String output = macroTitleForId(dag.output);
        if (output.length() > 0) {
            sb.append("selectImage(\"").append(escapeMacroArg(output)).append("\");\n");
            sb.append("mb_restore_calibration();\n");
        }
        return sb.toString();
    }

    private static String emitLinearOps(DagLine line, boolean includeUnknownComments) {
        return emitLineOps(line, includeUnknownComments, false);
    }

    private static String emitLineOps(DagLine line, boolean includeUnknownComments, boolean restoreCalibration) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < line.ops.size(); j++) {
            DagNode node = line.ops.get(j);
            String emitted = emitRun(node, includeUnknownComments);
            if (emitted.length() == 0) continue;
            sb.append(emitted);
            if (restoreCalibration) {
                sb.append("mb_restore_calibration();\n");
            }
        }
        return sb.toString();
    }

    private static String emitRun(DagNode node, boolean includeUnknownComments) {
        if (node.type == OpType.THRESHOLD) {
            return emitThreshold(node.args);
        }
        String command = commandFor(node.type);
        boolean legacyCommand = false;
        if (command == null && node.commandName != null && node.commandName.trim().length() > 0) {
            command = node.commandName.trim();
            legacyCommand = true;
        }
        if (command == null) {
            return includeUnknownComments ? "// UNKNOWN node omitted: " + node.id + "\n" : "";
        }
        String args = node.args == null ? "" : node.args;
        if (args.trim().isEmpty()) {
            return "run(\"" + command + "\");\n";
        }
        if (legacyCommand) {
            return "run(\"" + escapeMacroArg(command) + "\", \"" + escapeMacroArg(args) + "\");\n";
        }
        return "run(\"" + command + "...\", \"" + escapeMacroArg(args) + "\");\n";
    }

    public static String commandFor(OpType type) {
        if (type == null) return null;
        switch (type) {
            case GAUSSIAN_BLUR: return "Gaussian Blur";
            case SUBTRACT_BACKGROUND: return "Subtract Background";
            case MEDIAN: return "Median";
            case MEAN: return "Mean";
            case UNSHARP_MASK: return "Unsharp Mask";
            case MINIMUM: return "Minimum";
            case MAXIMUM: return "Maximum";
            case VARIANCE: return "Variance";
            case DILATE: return "Dilate";
            case ERODE: return "Erode";
            case OPEN: return "Open";
            case CLOSE_: return "Close-";
            case FILL_HOLES: return "Fill Holes";
            case SKELETONIZE: return "Skeletonize";
            case INVERT: return "Invert";
            case ADD: return "Add";
            case SUBTRACT: return "Subtract";
            case MULTIPLY: return "Multiply";
            case DIVIDE: return "Divide";
            case AUTO_LOCAL_THRESHOLD: return "Auto Local Threshold";
            case THRESHOLD: return null;
            case CONVERT_8BIT: return "8-bit";
            case CONVERT_16BIT: return "16-bit";
            case CONVERT_32BIT: return "32-bit";
            case ENHANCE_CONTRAST: return "Enhance Contrast";
            case GAUSSIAN_BLUR_3D: return "Gaussian Blur 3D";
            case MEDIAN_3D: return "Median 3D";
            case MINIMUM_3D: return "Minimum 3D";
            default: return null;
        }
    }

    private static String emitThreshold(String args) {
        String mode = argValue(args, "mode");
        if ("auto".equals(mode)) {
            String method = argValue(args, "method");
            if (method.length() == 0) method = "Default";
            String background = argValue(args, "background");
            String suffix = "dark".equals(background) ? " dark" : "";
            return "setAutoThreshold(\"" + escapeMacroArg(method + suffix) + "\");\n";
        }
        String lower = argValue(args, "lower");
        String upper = argValue(args, "upper");
        if (lower.length() == 0) lower = "0";
        if (upper.length() == 0) upper = "255";
        return "setThreshold(" + lower + ", " + upper + ");\n";
    }

    private static String argValue(String args, String key) {
        if (args == null || key == null || key.length() == 0) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:^|\\s)" + java.util.regex.Pattern.quote(key) + "=([^\\s]+)");
        java.util.regex.Matcher m = p.matcher(args);
        return m.find() ? m.group(1) : "";
    }

    private static String imageCalculatorCommand(CombinerOp op) {
        if (op == null) return "Add";
        switch (op) {
            case AND: return "AND";
            case OR: return "OR";
            case ADD: return "Add";
            case SUBTRACT: return "Subtract";
            case DIFFERENCE: return "Difference";
            case AVG: return "Average";
            case MAX: return "Max";
            case MIN: return "Min";
            default: return "Add";
        }
    }

    public static String macroTitleForId(String id) {
        return macroIdentifier(id);
    }

    private static String macroIdentifier(String id) {
        if (id == null || id.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String trimmed = id.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static String escapeMacroArg(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
