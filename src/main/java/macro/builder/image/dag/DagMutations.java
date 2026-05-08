package macro.builder.image.dag;

import macro.builder.image.FilterMacroParser.OpType;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-logic helpers that rebuild a {@link DagIR} with one node mutated.
 *
 * <p>Both methods preserve {@code version}, {@code primaryChannel}, {@code combiners},
 * {@code output}, and per-line {@code id}/{@code name}/{@code sourceChannel}. The
 * {@code executionTier} is recomputed by {@link DagIR}'s constructor; substituting a
 * tier-2 op (one with a non-empty {@code commandName}) into or out of the DAG can
 * therefore flip the resulting tier.
 */
public final class DagMutations {

    private DagMutations() {}

    /**
     * Rebuild {@code src} with the node identified by {@code nodeId} carrying
     * {@code newArgs}. All other fields of that node — and every other node — are
     * preserved exactly.
     *
     * @throws IllegalArgumentException if no node with {@code nodeId} exists.
     */
    public static DagIR withNodeArgs(DagIR src, String nodeId, String newArgs) {
        return rebuildWith(src, nodeId, new NodeRewriter() {
            @Override
            public DagNode rewrite(DagNode node) {
                return new DagNode(node.id, node.type, newArgs, node.commandName, node.menuPath);
            }
        });
    }

    /**
     * Rebuild {@code src} with the node identified by {@code nodeId} replaced by a
     * new node of {@code newType} with {@code newArgs}. The node {@code id} is kept
     * so combiners and downstream references remain valid. {@code commandName} and
     * {@code menuPath} are reset to empty — substituting always lands on a native-tier op.
     *
     * @throws IllegalArgumentException if no node with {@code nodeId} exists.
     */
    public static DagIR withNodeSubstituted(DagIR src, String nodeId, OpType newType, String newArgs) {
        return rebuildWith(src, nodeId, new NodeRewriter() {
            @Override
            public DagNode rewrite(DagNode node) {
                return new DagNode(node.id, newType, newArgs, "", "");
            }
        });
    }

    private static DagIR rebuildWith(DagIR src, String nodeId, NodeRewriter rewriter) {
        if (nodeId == null) throw new IllegalArgumentException("nodeId must not be null");
        boolean found = false;
        List<DagLine> newLines = new ArrayList<DagLine>(src.lines.size());
        for (DagLine line : src.lines) {
            List<DagNode> newOps = new ArrayList<DagNode>(line.ops.size());
            for (DagNode n : line.ops) {
                if (nodeId.equals(n.id)) {
                    newOps.add(rewriter.rewrite(n));
                    found = true;
                } else {
                    newOps.add(n);
                }
            }
            newLines.add(new DagLine(line.id, line.name, newOps, line.sourceChannel));
        }
        if (!found) {
            throw new IllegalArgumentException("nodeId not found: " + nodeId);
        }
        // Pass "native" so DagIR's ctor re-derives the tier from the rebuilt lines:
        // it flips to "legacy" iff any node still carries a non-empty commandName.
        // This way, substituting *into* a tier-2 op flips to legacy, and substituting
        // the last tier-2 op back out flips to native.
        return new DagIR(src.version, src.primaryChannel, newLines,
                src.combiners, src.output, "native");
    }

    private interface NodeRewriter {
        DagNode rewrite(DagNode node);
    }
}
