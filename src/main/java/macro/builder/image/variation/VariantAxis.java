package macro.builder.image.variation;

import macro.builder.image.FilterMacroParser.OpType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One axis of variation: which DAG node is varied, how (parameter sweep vs filter
 * substitution), and the list of alternative values to apply along this axis.
 *
 * <p>Immutable value type. The {@code alternatives} list is copied defensively at
 * construction and exposed unmodifiable.
 */
public final class VariantAxis {

    public enum Kind {
        /** Vary the {@code args} string of the existing node; node {@code type} stays the same. */
        PARAM_SWEEP,
        /** Substitute the node's {@code type} (and {@code args}) for an alternative filter. */
        FILTER_SWAP
    }

    public final String nodeId;
    public final Kind kind;
    public final List<AlternativeValue> alternatives;

    public VariantAxis(String nodeId, Kind kind, List<AlternativeValue> alternatives) {
        if (nodeId == null || nodeId.length() == 0) {
            throw new IllegalArgumentException("nodeId must not be empty");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.nodeId = nodeId;
        this.kind = kind;
        if (alternatives == null) {
            this.alternatives = Collections.emptyList();
        } else {
            this.alternatives = Collections.unmodifiableList(
                    new ArrayList<AlternativeValue>(alternatives));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof VariantAxis)) return false;
        VariantAxis other = (VariantAxis) obj;
        return nodeId.equals(other.nodeId)
                && kind == other.kind
                && alternatives.equals(other.alternatives);
    }

    @Override
    public int hashCode() {
        int result = nodeId.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + alternatives.hashCode();
        return result;
    }

    /**
     * One alternative value along an axis. {@code label} is baked into the tile
     * caption (e.g. {@code "sigma=2.0"} or {@code "Median"}). {@code type} is null
     * for {@link Kind#PARAM_SWEEP} (the existing node type is kept) and non-null for
     * {@link Kind#FILTER_SWAP}. {@code args} is the args string to apply.
     */
    public static final class AlternativeValue {
        public final String label;
        public final OpType type;
        public final String args;

        public AlternativeValue(String label, OpType type, String args) {
            this.label = label == null ? "" : label;
            this.type = type;
            this.args = args == null ? "" : args;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof AlternativeValue)) return false;
            AlternativeValue other = (AlternativeValue) obj;
            return label.equals(other.label)
                    && type == other.type
                    && args.equals(other.args);
        }

        @Override
        public int hashCode() {
            int result = label.hashCode();
            result = 31 * result + (type == null ? 0 : type.hashCode());
            result = 31 * result + args.hashCode();
            return result;
        }
    }
}
