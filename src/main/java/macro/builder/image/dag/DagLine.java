package macro.builder.image.dag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DagLine {
    public final String id;
    public final String name;
    public final int sourceChannel;
    public final List<DagNode> ops;

    public DagLine(String id, List<DagNode> ops) {
        this(id, ops, 1);
    }

    public DagLine(String id, List<DagNode> ops, int sourceChannel) {
        this(id, "", ops, sourceChannel);
    }

    public DagLine(String id, String name, List<DagNode> ops, int sourceChannel) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name.trim();
        this.sourceChannel = positiveChannel(sourceChannel);
        if (ops == null) {
            this.ops = Collections.emptyList();
        } else {
            this.ops = Collections.unmodifiableList(new ArrayList<DagNode>(ops));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DagLine)) return false;
        DagLine other = (DagLine) obj;
        return id.equals(other.id)
                && name.equals(other.name)
                && sourceChannel == other.sourceChannel
                && ops.equals(other.ops);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + sourceChannel;
        result = 31 * result + ops.hashCode();
        return result;
    }

    private static int positiveChannel(int channel) {
        if (channel < 1) throw new IllegalArgumentException("sourceChannel must be positive");
        return channel;
    }
}
